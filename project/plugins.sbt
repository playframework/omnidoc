addSbtPlugin("com.typesafe.play" % "interplay" % sys.props.getOrElse("interplay.version", "1.0.1"))

libraryDependencies += "com.typesafe.play" %% "play-doc" % "1.2.1"

lazy val plugins = (project in file(".")).enablePlugins(SbtTwirl)

import TwirlKeys._
sourceDirectories in (Compile, compileTemplates) := Seq(baseDirectory.value)
sources in (Compile, compileTemplates) := Seq(baseDirectory.value / "page.scala.html")
