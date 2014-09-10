import sbt._
import sbt.Keys._

object OmnidocBuild extends Build {

  val playVersion = "2.4.0-M1"

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

  val Omnidoc = config("omnidoc")

  val scaladoc         = TaskKey[File]("scaladoc")
  val javadoc          = TaskKey[File]("javadoc")
  val dependencyFilter = SettingKey[DependencyFilter]("dependencyFilter")

  val sourcesFilter = moduleFilter(
    organization = playOrganisation,
    name = excludeArtifacts.foldLeft(AllPassFilter: NameFilter)(_ - _)
  ) && artifactFilter(classifier = "sources")

  lazy val omnidoc = project
    .in(file("."))
    .settings(omnidocSettings: _*)
    .settings(projectSettings: _*)

  def omnidocSettings: Seq[Setting[_]] =
    projectSettings ++
    inConfig(Omnidoc) {
      omnidocScoped ++
      Defaults.docTaskSettings(scaladoc) ++
      Defaults.docTaskSettings(javadoc)
    }

  def projectSettings: Seq[Setting[_]] = Seq(
                  version :=  playVersion,
             scalaVersion :=  playScalaVersion,
                resolvers +=  Resolver.typesafeRepo("releases"),
      libraryDependencies ++= playProjects map (playOrganisation %% _ % playVersion),
    transitiveClassifiers :=  Seq(Artifact.SourceClassifier)
  )

  def omnidocScoped: Seq[Setting[_]] = Seq(
          dependencyClasspath := (dependencyClasspath in Compile).value,
             dependencyFilter := sourcesFilter,
                      sources := extractSources.value,
          sources in scaladoc := (sources.value ** "*.scala").get,
           sources in javadoc := (sources.value ** "*.java").get,
                       target := target.value / "omnidoc",
            target in sources := target.value / "sources",
           target in scaladoc := target.value / "api" / "scala",
            target in javadoc := target.value / "api" / "java",
    scalacOptions in scaladoc := scaladocOptions.value,
      javacOptions in javadoc := javadocOptions.value
  )

  // returns a sequence of directories containing each artifact's extracted sources
  def extractSources = Def.task {
    val log          = streams.value.log
    val cacheDir     = streams.value.cacheDirectory
    val targetDir    = (target in sources).value
    val dependencies = (updateClassifiers.value matching dependencyFilter.value).toSet
    val extract = FileFunction.cached(cacheDir / "extract-sources", FilesInfo.hash) { _ =>
      log.info("Extracting sources...")
      IO.delete(targetDir)
      dependencies map { file =>
        val extracted = targetDir / file.name
        log.debug("  from: " + file.name)
        IO.unzip(file, extracted)
        extracted
      }
    }
    extract(dependencies).toSeq
  }

  def scaladocOptions = Def.task {
    // val sourcepath = (target in sources).value.getAbsolutePath
    // val tree = if (isSnapshot.value) "master" else version.value
    // val docSourceUrl = s"https://github.com/playframework/playframework/tree/${tree}/framework€{FILE_PATH}.scala"
    // Seq(
    //   "-sourcepath", sourcepath,
    //   "-doc-source-url", docSourceUrl
    // )
    Seq.empty[String]
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

}
