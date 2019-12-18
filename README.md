# Omnidoc

Omnidoc is an sbt build that adds to sbt's `mappings in (Compile, packageBin)` to aggregate source code and manuals produced within the Play ecosystem and produce a single deliverable which includes:

 * manual (also referred to as `playdoc`)
 * javadoc
 * scaladoc

## How it works

Omnidoc defines [5 tasks](https://github.com/playframework/omnidoc/blob/70f04533d0f881a9a7f6c1ac5ec6af1d8bb335f9/project/OmnidocBuild.scala#L88-L92) to:

1. download `-sources.jar` and `-playdoc.jar` artifacts for each dependency
2. extract the dowloaded `-sources`artifacts into `omnidoc/sources/` 
3. extract the dowloaded `-playdoc`artifacts into `omnidoc/playdoc/` 
4. use `omnidoc/sources/` to produce `omnidoc/javadoc/`
5. use `omnidoc/sources/` to produce `omnidoc/scaladoc/`
6. package `playdoc` into `play/docs/content`
7. package `javadoc` into `play/docs/content/api/java`
8. package `scaladoc` into `play/docs/content/api/scala`

*NOTE:* all the paths above are relative to `target/scala-2.1x/` (for example `target/scala-2.12/`). 
