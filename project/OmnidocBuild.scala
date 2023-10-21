import sbt._
import sbt.Artifact.SourceClassifier
import sbt.Keys._
import sbt.io.Using
import sbt.librarymanagement.{GetClassifiersConfiguration, GetClassifiersModule, SemanticSelector, UpdateConfiguration, VersionNumber}
import sbtdynver.DynVerPlugin.autoImport._

import java.io.IOException

object OmnidocBuild {

  val scala213 = "2.13.12"
  val scala3 = "3.3.1"

  val playOrganisation = "org.playframework"
  val scalaTestPlusPlayOrganisation = "org.scalatestplus.play"
  val playOrganisations = Seq(playOrganisation, scalaTestPlusPlayOrganisation)

  val snapshotVersionLabel = "3.0.x"

  val playVersion              = sys.props.getOrElse("play.version",               "3.0.0-M3")
  val scalaTestPlusPlayVersion = sys.props.getOrElse("scalatestplus-play.version", "6.0.0-RC2")
  val playJsonVersion          = sys.props.getOrElse("play-json.version",          "2.10.1")
  val playSlickVersion         = sys.props.getOrElse("play-slick.version",         "5.2.0-RC1")
  val maybeTwirlVersion        = sys.props.get("twirl.version")

  // List Play artifacts so that they can be added as dependencies
  // and later Omnidoc will read the javadoc artifact.
  //
  // Of course there are dependencies between these projects and
  // we would need to list them all here, but it is better if we
  // do so that we won't need to worry about the dependency tree.
  val playProjects = Seq(
    "play",
    "play-ahc-ws",
    "play-pekko-http-server",
    "play-pekko-http2-support",
    "play-cache",
    "play-caffeine-cache",
    "play-cluster-sharding",
    "play-configuration",
    "play-ehcache",
    "play-guice",
    "play-java",
    "play-java-cluster-sharding",
    "play-java-forms",
    "play-java-jdbc",
    "play-java-jpa",
    "play-jcache",
    "play-jdbc",
    "play-jdbc-api",
    "play-jdbc-evolutions",
    "play-joda-forms",
    "play-logback",
    "play-netty-server",
    "play-openid",
    "play-server",
    "play-specs2",
    "play-streams",
    "play-test",
    "play-ws",
    "play-filters-helpers",
  )

  val excludeArtifacts = Seq(
    "play-build-link",
    "play-exceptions",
    "play-netty-utils",
  )

  val playModules = Seq(
    scalaTestPlusPlayOrganisation %% "scalatestplus-play" % scalaTestPlusPlayVersion,
    playOrganisation %% "play-functional"       % playJsonVersion,
    playOrganisation %% "play-json"             % playJsonVersion,
    playOrganisation %% "play-slick"            % playSlickVersion,
    playOrganisation %% "play-slick-evolutions" % playSlickVersion
  )

  val maybeTwirlModule = maybeTwirlVersion.map(v => playOrganisation %% "twirl-api" % v).toSeq

  val externalModules = playModules ++ maybeTwirlModule

  val nameFilter = excludeArtifacts.foldLeft(new SimpleFilter(!_.contains("-standalone")): NameFilter)(_ - _)
  val organizationFilter = playOrganisations.map(new ExactFilter(_): NameFilter).reduce(_ | _)
  val playModuleFilter = moduleFilter(organization = organizationFilter, name = nameFilter)

  val Omnidoc = config("omnidoc").hide

  val PlaydocClassifier = "playdoc"

  val extractedSources = TaskKey[Seq[Extracted]]("extractedSources")
  val sourceUrls       = TaskKey[Map[String, String]]("sourceUrls")
  val javadoc          = TaskKey[File]("javadoc")
  val scaladoc         = TaskKey[File]("scaladoc")
  val playdoc          = TaskKey[File]("playdoc")

  // Duplicate of sbt's updateClassifiers, for 'playdoc' specific use
  val updatePlaydocClassifiers = taskKey[(UpdateReport, UpdateReport)]("")

  lazy val omnidoc = project
    .in(file("."))
    .settings(omnidocSettings)
    .configs(Omnidoc)

  def omnidocSettings: Seq[Setting[_]] =
    projectSettings ++
    dependencySettings ++
    inConfig(Omnidoc) {
      updateSettings ++
      extractSettings ++
      compilerReporterSettings ++
      scaladocSettings ++
      javadocSettings ++
      packageSettings
    }

  def projectSettings: Seq[Setting[_]] = Seq(
                               name := "play-omnidoc",
                 crossScalaVersions := Seq(scala213, scala3),
                       scalaVersion := (Seq(scala213, scala3)
                          .filter(v => SemanticSelector(sys.props.get("scala.version").getOrElse(scala213)).matches(VersionNumber(v))) match {
                            case Nil => sys.error("Unable to detect scalaVersion!")
                            case Seq(version) => version
                            case multiple => sys.error(s"Multiple crossScalaVersions matched query '${sys.props("scala.version")}': ${multiple.mkString(", ")}")
                          }),
                          resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++
                                        Resolver.sonatypeOssRepos("releases"),
                        useCoursier := false, // so updatePlaydocClassifiers isn't empty
 updateSbtClassifiers / useCoursier := true, // https://github.com/sbt/sbt/issues/5263#issuecomment-626462593
       ThisBuild / dynverVTagPrefix := false, // Customise sbt-dynver's behaviour to make it work with tags which aren't v-prefixed
                    Global / onLoad := (Global / onLoad).value.andThen { s =>
                                         dynverAssertTagVersion.value // Sanity-check: assert that version comes from a tag (e.g. not a too-shallow clone)
                                         s                            // https://github.com/dwijnand/sbt-dynver/#sanity-checking-the-version
                                       },
  )

  def dependencySettings: Seq[Setting[_]] = Seq(
      ivyConfigurations +=  Omnidoc,
    libraryDependencies ++= playProjects.map(playOrganisation %% _ % playVersion % Omnidoc.name),
    libraryDependencies ++= externalModules.map(_ % Omnidoc.name),
    libraryDependencies ++= Seq(
      playOrganisation %% "play-docs" % playVersion,
    )
  )

  def updateSettings: Seq[Setting[_]] = Seq(
    transitiveClassifiers := Seq(SourceClassifier, PlaydocClassifier),
    updatePlaydocClassifiers := updatePlaydocClassifiersTask.value
  )

  def extractSettings: Seq[Setting[_]] = Seq(
                 target := crossTarget.value / "omnidoc",
       sources / target := target.value / "sources",
       extractedSources := extractSources.value,
                sources := extractedSources.value.map(_.dir),
             sourceUrls := getSourceUrls(extractedSources.value),
    dependencyClasspath := Classpaths.managedJars(configuration.value, classpathTypes.value, update.value),
       playdoc / target := target.value / "playdoc",
                playdoc := extractPlaydocs.value
  )

  def scaladocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(scaladoc) ++ Seq(
          scaladoc / sources := {
            val s = sources.value
            // Exclude a Play JSON file from the Scaladoc build because
            // it includes a macro from a third party library and we don't
            // want to deal with bringing extra libraries into Omnidoc.
            ((s ** "*.scala") --- (s ** "JsMacroImpl.scala")).get
          },
            Compile / sources := (scaladoc / sources).value, // Needed for Scala 3 compilation, see comments below
Compile / dependencyClasspath := dependencyClasspath.value, // Needed for Scala 3 compilation, see comments below
            scaladoc / target := target.value / "scaladoc",
     scaladoc / scalacOptions := scaladocOptions.value,
                     scaladoc := {
                       if (ScalaArtifacts.isScala3(scalaVersion.value)) {
                         // Scala 3 scaladoc ("dottydoc") does not support .scala files at the moment (unless Scala 2 scaladoc
                         // which does). It requires .tasty and/or .jar files to generate the scala doc. That means, when on Scala 3,
                         // we need to trigger a full compilation to generate those .tasty files.
                         // (This requires to set Compile / dependencyClasspath and Compile / sources)
                         // See
                         // https://github.com/lampepfl/dotty/issues/11454#issuecomment-1636872223
                         // https://github.com/lampepfl/dotty/pull/18215
                         (Compile / compile).value
                       } else {
                         // No compilation needed for Scala 2
                       }
                       rewriteSourceUrls(scaladoc.value, sourceUrls.value, "/src/main/scala", ".scala")
                     }
  )

  def javadocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(javadoc) ++ Seq(
         (javadoc / sources) := (sources.value ** "*.java").get,
          (javadoc / target) := target.value / "javadoc",
    (javadoc / javacOptions) := javadocOptions.value
  )

  private val compilerReporter = taskKey[xsbti.Reporter]("Experimental hook to listen (or send) compilation failure messages.")

  def compilerReporterSettings = Seq(
    compile / compilerReporter := {
      new sbt.internal.inc.LoggedReporter(
        maxErrors.value,
        streams.value.log,
        foldMappers(sourcePositionMappers.value)
      )
    },
    tastyFiles := (Compile / compile / tastyFiles).value, // not 100% sure but it seems to work
    bspReporter := (Compile / compile / bspReporter).value, // same
  )

  private def foldMappers[A](mappers: Seq[A => Option[A]]): A => A =
    mappers.foldRight(idFun[A]) { (mapper, acc) =>
      p: A => mapper(p).getOrElse(acc(p))
    }

  def packageSettings: Seq[Setting[_]] = Seq(
    Compile / packageBin / mappings ++= {
      def mapped(dir: File, path: String) = dir.allPaths.pair(Path.rebase(dir, path))
      mapped(playdoc.value,  "play/docs/content") ++
      mapped(scaladoc.value, "play/docs/content/api/scala") ++
      mapped(javadoc.value,  "play/docs/content/api/java")
    }
  )

  /**
    * Custom update classifiers task that only resolves classifiers for Play modules.
    * Also redirects warnings to debug for any artifacts that can't be found.
    */
  private def updatePlaydocClassifiersTask: Def.Initialize[Task[(UpdateReport, UpdateReport)]] = Def.task {
    val updateConfig0 = updateConfiguration.value.withMetadataDirectory(dependencyCacheDirectory.value)
    val updateConfig1 = updateConfig0.withArtifactFilter(updateConfig0.artifactFilter.map(_.invert))

    def updateClassifiersTask0(updateConfig: UpdateConfiguration): UpdateReport = {
      val lm            = dependencyResolution.value
      val omnidocReport = update.value.configuration(Omnidoc.toConfigRef)
      val playModules   = omnidocReport.toVector.flatMap(_.allModules.filter(playModuleFilter))
      val classifiers   = transitiveClassifiers.value.toVector
      val playdocConfig = GetClassifiersConfiguration(
        GetClassifiersModule(projectID.value, None, playModules, Vector(Omnidoc), classifiers),
        Vector.empty,
        updateConfig,
        sourceArtifactTypes.value.toVector,
        docArtifactTypes.value.toVector,
      )
      val uwConfig = (update / unresolvedWarningConfiguration).value
      lm.updateClassifiers(playdocConfig, uwConfig, Vector.empty, streams.value.log).right.get
    }

    // Current impl of Ivy resolvers in sbt or the underlying Ivy client don't support
    // the combination of `playdoc` classifiers with `sources` and `docs` so we
    // run two passes with different (complementary) filters.
    (updateClassifiersTask0(updateConfig1), updateClassifiersTask0(updateConfig0))
  }.tag(Tags.Update, Tags.Network)

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
  }

  def extractSources = Def.task {
    val log          = streams.value.log
    val targetDir    = (sources / target).value
    val dependencies = updatePlaydocClassifiers.value._1.filter(artifactFilter(classifier = SourceClassifier)).toSeq
    log.info("Extracting sources...")
    IO.delete(targetDir)
    dependencies.map { case (conf, module, artifact, file) =>
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
    val targetDir    = (playdoc / target).value
    val dependencies = updatePlaydocClassifiers.value._2.matching(artifactFilter(classifier = PlaydocClassifier))
    log.info("Extracting playdocs...")
    IO.delete(targetDir)
    dependencies.foreach { file =>
      log.debug(s"Extracting $file")
      IO.unzip(file, targetDir, -"META-INF*")
    }
    targetDir
  }

  def scaladocOptions = Def.task {
    val sourcepath   = (sources / target).value.getAbsolutePath
    val docSourceUrl = sourceUrlMarker("€{FILE_PATH}")
    Seq(
      "-sourcepath", sourcepath,
      "-doc-source-url", docSourceUrl
    )
  }

  def javadocOptions = Def.task {
    val versionish = if (isSnapshot.value) snapshotVersionLabel else version.value
    val label      = s"Play $versionish"
    Seq(
      "-windowtitle", label,
      // Adding a user agent when we run `javadoc` was necessary to create link docs
      // with Akka (at least, maybe play too) because doc.akka.io is served by Cloudflare
      // which blocks requests without a User-Agent header.
      // Not sure we need that for Pekko however.
      "-J-Dhttp.agent=Play-Unidoc-Javadoc",
      "-link",
      "https://docs.oracle.com/en/java/javase/11/docs/api/",
      "-link",
      "https://pekko.apache.org/japi/pekko/1.0/",
      "-link",
      "https://pekko.apache.org/japi/pekko-http/1.0/",
      "-notimestamp",
      "-exclude", "play.api:play.core",
      "-Xdoclint:none",
      "-noqualifier",
      "java.lang",
      "-encoding",
      "UTF-8",
      "-source",
      "11",
      
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
        case e: IOException =>
          // JDK jar APIs refuse to attribute jar files in their exceptions
          // And vegemite seems to have a corrupt jar
          throw new IOException(s"Error reading manifest attributes from $sourceJar", e)
      }
    }
  }

  def sourceUrlMarker(path: String): String = s"http://_SOURCE;${path}_"

  def getSourceUrls(extracted: Seq[Extracted]): Map[String, String] = {
    (extracted flatMap { source => source.url map source.dir.name.-> }).toMap
  }

  def rewriteSourceUrls(baseDir: File, sourceUrls: Map[String, String], prefix: String, suffix: String): File = {
    val files = baseDir.allPaths.filter(!_.isDirectory).get
    files.foreach { file =>
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
