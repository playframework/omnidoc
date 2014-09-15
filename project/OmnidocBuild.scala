import sbt._
import sbt.Artifact.SourceClassifier
import sbt.Keys._

object OmnidocBuild extends Build {

  val playVersion = "2.4-SNAPSHOT"

  val playOrganisation = "com.typesafe.play"

  val playScalaVersion = "2.10.4"

  // these dependencies pull in all the others
  val playProjects = Seq(
    "anorm",
    "play-cache",
    "play-integration-test",
    "play-java-ebean",
    "play-java-jpa"
    // TODO: play-slick (on 2.4)
  )

  val excludeArtifacts = Seq(
    "build-link",
    "play-exceptions",
    "play-netty-utils"
  )

  val nameFilter = excludeArtifacts.foldLeft(AllPassFilter: NameFilter)(_ - _)
  val playModuleFilter = moduleFilter(organization = playOrganisation, name = nameFilter)

  val Omnidoc = config("omnidoc")

  val extractedSources = TaskKey[Seq[Extracted]]("extractedSources")
  val sourceUrls       = TaskKey[Map[String, String]]("sourceUrls")
  val javadoc          = TaskKey[File]("javadoc")
  val scaladoc         = TaskKey[File]("scaladoc")

  lazy val omnidoc = project
    .in(file("."))
    .settings(omnidocSettings: _*)

  def omnidocSettings: Seq[Setting[_]] =
    projectSettings ++
    inConfig(Omnidoc) {
      updateSettings ++
      extractSettings ++
      scaladocSettings ++
      javadocSettings
    }

  def projectSettings: Seq[Setting[_]] = Seq(
                version :=  playVersion,
           scalaVersion :=  playScalaVersion,
              resolvers +=  Resolver.typesafeRepo("releases"),
      ivyConfigurations +=  Omnidoc,
    libraryDependencies ++= playProjects map (playOrganisation %% _ % playVersion % Omnidoc.name),
             initialize :=  { PomParser.registerParser }
  )

  def updateSettings: Seq[Setting[_]] = Seq(
    transitiveClassifiers :=  Seq(SourceClassifier),
        updateClassifiers <<= updateClassifiersTask
  )

  def extractSettings: Seq[Setting[_]] = Seq(
                 target := target.value / "omnidoc",
      target in sources := target.value / "sources",
       extractedSources := extractSources.value,
                sources := extractedSources.value.map(_.dir),
             sourceUrls := getSourceUrls(extractedSources.value),
    dependencyClasspath := Classpaths.managedJars(configuration.value, classpathTypes.value, update.value)
  )

  def scaladocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(scaladoc) ++ Seq(
          sources in scaladoc := (sources.value ** "*.scala").get,
           target in scaladoc := target.value / "api" / "scala",
    scalacOptions in scaladoc := scaladocOptions.value,
                     scaladoc := rewriteSourceUrls(scaladoc.value, sourceUrls.value, "/src/main/scala", ".scala")
  )

  def javadocSettings: Seq[Setting[_]] = Defaults.docTaskSettings(javadoc) ++ Seq(
         sources in javadoc := (sources.value ** "*.java").get,
          target in javadoc := target.value / "api" / "java",
    javacOptions in javadoc := javadocOptions.value
  )

  def updateClassifiersTask = Def.task {
    val playModules       = update.value.configuration(Omnidoc.name).toSeq.flatMap(_.allModules.filter(playModuleFilter))
    val classifiersModule = GetClassifiersModule(projectID.value, playModules, Seq(Omnidoc), transitiveClassifiers.value)
    val classifiersConfig = GetClassifiersConfiguration(classifiersModule, Map.empty, updateConfiguration.value, ivyScala.value)
    IvyActions.updateClassifiers(ivySbt.value, classifiersConfig, streams.value.log)
  }

  def extractSources = Def.task {
    val log          = streams.value.log
    val cacheDir     = streams.value.cacheDirectory
    val targetDir    = (target in sources).value
    val dependencies = (updateClassifiers.value filter artifactFilter(classifier = SourceClassifier)).toSeq
    log.info("Extracting sources...")
    IO.delete(targetDir)
    dependencies map { case (conf, module, artifact, file) =>
      val name = s"${module.organization}-${module.name}-${module.revision}"
      val dir = targetDir / name
      log.debug(s"Extracting $name")
      IO.unzip(file, dir)
      val sourceUrl = module.extraAttributes.get(SourceUrlKey)
      if (sourceUrl.isEmpty) log.warn(s"Source url not found for ${module.name}")
      Extracted(dir, sourceUrl)
    }
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
    val label = "Play " + version.value
    Seq(
      "-windowtitle", label,
      "-notimestamp",
      "-subpackages", "play",
      "-exclude", "play.api:play.core"
    )
  }

  // Source linking

  case class Extracted(dir: File, url: Option[String])

  val SourceUrlKey = "info.sourceUrl"

  val NoSourceUrl = "javascript:;"

  // first part of path is the extracted directory name, which is used as the source url mapping key
  val SourceUrlRegex = sourceUrlMarker("/([^/\\s]*)(/\\S*)").r

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

  object PomParser {

    import org.apache.ivy.core.module.descriptor.ModuleDescriptor
    import org.apache.ivy.plugins.parser.{ ModuleDescriptorParser, ModuleDescriptorParserRegistry }
    import org.apache.ivy.plugins.parser.m2.PomModuleDescriptorBuilder

    val extraKeys = Set(SourceUrlKey)

    val extraParser = new CustomPomParser(CustomPomParser.default, addExtra)

    def registerParser = ModuleDescriptorParserRegistry.getInstance.addParser(extraParser)

    def addExtra(parser: ModuleDescriptorParser, descriptor: ModuleDescriptor): ModuleDescriptor = {
      val properties = getExtraProperties(descriptor, extraKeys)
      CustomPomParser.addExtra(properties, Map.empty, parser, descriptor)
    }

    def getExtraProperties(descriptor: ModuleDescriptor, keys: Set[String]): Map[String, String] = {
      import scala.collection.JavaConverters._
      PomModuleDescriptorBuilder.extractPomProperties(descriptor.getExtraInfo)
        .asInstanceOf[java.util.Map[String, String]].asScala.toMap
        .filterKeys(keys)
        .map { case (k, v) => ("e:" + k, v) }
    }

  }

}
