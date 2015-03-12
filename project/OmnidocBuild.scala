import sbt._
import sbt.Artifact.SourceClassifier
import sbt.Keys._

object OmnidocBuild extends Build {

  val playOrganisation = "com.typesafe.play"

  val snapshotVersionLabel = "2.4.x"

  val playVersion       = sys.props.getOrElse("play.version",       "2.4.0-M3")
  val anormVersion      = sys.props.getOrElse("anorm.version",      "2.4.0-M3")
  val playEbeanVersion  = sys.props.getOrElse("play-ebean.version", "1.0.0-M2")
  val playSlickVersion  = sys.props.getOrElse("play-slick.version", "0.9.0-M4")
  val maybeTwirlVersion = sys.props.get("twirl.version")

  // these dependencies pull in all the others
  val playProjects = Seq(
    "play-cache",
    "play-integration-test",
    "play-java-jpa"
  )

  val excludeArtifacts = Seq(
    "build-link",
    "play-exceptions",
    "play-netty-utils"
  )

  val playModules = Seq(
    playOrganisation %% "anorm"      % anormVersion,
    playOrganisation %% "play-ebean" % playEbeanVersion,
    playOrganisation %% "play-slick" % playSlickVersion
  )

  val maybeTwirlModule = (maybeTwirlVersion map { twirlVersion =>
    playOrganisation %% "twirl-api" % twirlVersion
  }).toSeq

  val externalModules = playModules ++ maybeTwirlModule

  val nameFilter = excludeArtifacts.foldLeft(AllPassFilter: NameFilter)(_ - _)
  val playModuleFilter = moduleFilter(organization = playOrganisation, name = nameFilter)

  val Omnidoc = config("omnidoc").hide

  val PlaydocClassifier = "playdoc"

  val extractedSources = TaskKey[Seq[Extracted]]("extractedSources")
  val sourceUrls       = TaskKey[Map[String, String]]("sourceUrls")
  val javadoc          = TaskKey[File]("javadoc")
  val scaladoc         = TaskKey[File]("scaladoc")
  val playdoc          = TaskKey[File]("playdoc")

  lazy val omnidoc = project
    .in(file("."))
    .settings(omnidocSettings: _*)

  def omnidocSettings: Seq[Setting[_]] =
    projectSettings ++
    dependencySettings ++
    publishSettings ++
    inConfig(Omnidoc) {
      updateSettings ++
      extractSettings ++
      scaladocSettings ++
      javadocSettings ++
      packageSettings
    }

  def projectSettings: Seq[Setting[_]] = Seq(
                  name :=  "play-omnidoc",
          organization :=  playOrganisation,
               version :=  playVersion,
          scalaVersion :=  "2.11.4",
    crossScalaVersions :=  Seq("2.10.4", scalaVersion.value)
  )

  def dependencySettings: Seq[Setting[_]] = Seq(
              resolvers +=  Resolver.typesafeRepo("releases"),
              resolvers ++= DefaultOptions.resolvers(snapshot = true),
      ivyConfigurations +=  Omnidoc,
    libraryDependencies ++= playProjects map (playOrganisation %% _ % playVersion % Omnidoc.name),
    libraryDependencies ++= externalModules map (_ % Omnidoc.name),
    libraryDependencies +=  playOrganisation %% "play-docs" % playVersion
  )

  def publishSettings: Seq[Setting[_]] = Seq(
    publishTo := {
      if (isSnapshot.value) Some(Opts.resolver.sonatypeSnapshots)
      else Some(Opts.resolver.sonatypeStaging)
    },
    homepage := Some(url("https://github.com/playframework/omnidoc")),
    licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    pomExtra := {
      <scm>
        <url>https://github.com/playframework/omnidoc</url>
        <connection>scm:git:git@github.com:playframework/omnidoc.git</connection>
      </scm>
      <developers>
        <developer>
          <id>playframework</id>
          <name>Play Framework Team</name>
          <url>https://github.com/playframework</url>
        </developer>
      </developers>
    },
    pomIncludeRepository := { _ => false }
  )

  def updateSettings: Seq[Setting[_]] = Seq(
    transitiveClassifiers := Seq(SourceClassifier, PlaydocClassifier),
        updateClassifiers := updateClassifiersTask.value
  )

  def extractSettings: Seq[Setting[_]] = Seq(
                 target := crossTarget.value / "omnidoc",
      target in sources := target.value / "sources",
       extractedSources := extractSources.value,
                sources := extractedSources.value.map(_.dir),
             sourceUrls := getSourceUrls(extractedSources.value),
    dependencyClasspath := Classpaths.managedJars(configuration.value, classpathTypes.value, update.value),
      target in playdoc := target.value / "playdoc",
                playdoc := extractPlaydocs.value
  )

  def scaladocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(scaladoc) ++ Seq(
          sources in scaladoc := (sources.value ** "*.scala").get,
           target in scaladoc := target.value / "scaladoc",
    scalacOptions in scaladoc := scaladocOptions.value,
                     scaladoc := rewriteSourceUrls(scaladoc.value, sourceUrls.value, "/src/main/scala", ".scala")
  )

  def javadocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(javadoc) ++ Seq(
         sources in javadoc := (sources.value ** "*.java").get,
          target in javadoc := target.value / "javadoc",
    javacOptions in javadoc := javadocOptions.value
  )

  def packageSettings: Seq[Setting[_]] = Seq(
    mappings in (Compile, packageBin) ++= {
      def mapped(dir: File, path: String) = dir.*** pair rebase(dir, path)
      mapped(playdoc.value,  "play/docs/content") ++
      mapped(scaladoc.value, "play/docs/content/api/scala") ++
      mapped(javadoc.value,  "play/docs/content/api/java")
    }
  )

  /**
   * Custom update classifiers task that only resolves classifiers for Play modules.
   * Also redirects warnings to debug for any artifacts that can't be found.
   */
  def updateClassifiersTask = Def.task {
    val playModules       = update.value.configuration(Omnidoc.name).toSeq.flatMap(_.allModules.filter(playModuleFilter))
    val classifiersModule = GetClassifiersModule(projectID.value, playModules, Seq(Omnidoc), transitiveClassifiers.value)
    val classifiersConfig = GetClassifiersConfiguration(classifiersModule, Map.empty, updateConfiguration.value, ivyScala.value)
    IvyActions.updateClassifiers(ivySbt.value, classifiersConfig, quietLogger(streams.value.log))
  }

  /**
   * Redirect logging above a certain level to debug.
   */
  def quietLogger(underlying: Logger, minimumLevel: Level.Value = Level.Info): Logger = new Logger {
    def log(level: Level.Value, message: => String): Unit = {
      if (level.id > minimumLevel.id) underlying.log(Level.Debug, s"[$level] $message")
      else underlying.log(level, message)
    }
    def success(message: => String): Unit = underlying.success(message)
    def trace(t: => Throwable): Unit = underlying.trace(t)
    override def ansiCodesSupported: Boolean = underlying.ansiCodesSupported
  }

  def extractSources = Def.task {
    val log          = streams.value.log
    val targetDir    = (target in sources).value
    val dependencies = (updateClassifiers.value filter artifactFilter(classifier = SourceClassifier)).toSeq
    log.info("Extracting sources...")
    IO.delete(targetDir)
    dependencies map { case (conf, module, artifact, file) =>
      val name = s"${module.organization}-${module.name}-${module.revision}"
      val dir = targetDir / name
      log.debug(s"Extracting $name")
      IO.unzip(file, dir, -"META-INF*")
      val sourceUrl = extractSourceUrl(file)
      if (sourceUrl.isEmpty) log.warn(s"Source url not found for ${module.name}")
      Extracted(dir, sourceUrl)
    }
  }

  def extractPlaydocs = Def.task {
    val log          = streams.value.log
    val targetDir    = (target in playdoc).value
    val dependencies = updateClassifiers.value matching artifactFilter(classifier = PlaydocClassifier)
    log.info("Extracting playdocs...")
    IO.delete(targetDir)
    dependencies foreach { case file =>
      log.debug(s"Extracting $file")
      IO.unzip(file, targetDir, -"META-INF*")
    }
    targetDir
  }

  def scaladocOptions = Def.task {
    val sourcepath   = (target in sources).value.getAbsolutePath
    val docSourceUrl = sourceUrlMarker("€{FILE_PATH}")
    Seq(
      "-sourcepath", sourcepath,
      "-doc-source-url", docSourceUrl
    )
  }

  def javadocOptions = Def.task {
    val label = "Play " + (if (isSnapshot.value) snapshotVersionLabel else version.value)
    Seq(
      "-windowtitle", label,
      "-notimestamp",
      "-subpackages", "play",
      "-exclude", "play.api:play.core"
    )
  }

  // Source linking

  case class Extracted(dir: File, url: Option[String])

  val SourceUrlKey = "Omnidoc-Source-URL"

  val NoSourceUrl = "javascript:;"

  // first part of path is the extracted directory name, which is used as the source url mapping key
  val SourceUrlRegex = sourceUrlMarker("/([^/\\s]*)(/\\S*)").r

  def extractSourceUrl(sourceJar: File): Option[String] = {
    Using.jarFile(verify = false)(sourceJar) { jar =>
      try {
        Option(jar.getManifest.getMainAttributes.getValue(SourceUrlKey))
      } catch {
        case e: java.io.IOException =>
          // JDK jar APIs refuse to attribute jar files in their exceptions
          // And vegemite seems to have a corrupt jar
          throw new java.io.IOException(s"Error reading manifest attributes from $sourceJar", e)
      }
    }
  }

  def sourceUrlMarker(path: String): String = s"http://%SOURCE;${path}%"

  def getSourceUrls(extracted: Seq[Extracted]): Map[String, String] = {
    (extracted flatMap { source => source.url map source.dir.name.-> }).toMap
  }

  def rewriteSourceUrls(baseDir: File, sourceUrls: Map[String, String], prefix: String, suffix: String): File = {
    val files = baseDir.***.filter(!_.isDirectory).get
    files foreach { file =>
      val contents = IO.read(file)
      val newContents = SourceUrlRegex.replaceAllIn(contents, matched => {
        val key  = matched.group(1)
        val path = matched.group(2)
        sourceUrls.get(key).fold(NoSourceUrl)(_ + prefix + path + suffix)
      })
      if (newContents != contents) {
        IO.write(file, newContents)
      }
    }
    baseDir
  }

}
