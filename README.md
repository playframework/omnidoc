# Omnidoc
Omnidoc is an sbt script piggy-backing the `publish` task that aggregates all source code and manuals produced across the Play ecosystem and produces a single deliverable with:

 * manual (also referred to as `playdoc`)
 * javadoc
 * scaladoc

## How it works

Omnidoc defines [5 tasks]([omnidoc/OmnidocBuild.scala at 70f04533d0f881a9a7f6c1ac5ec6af1d8bb335f9 · playframework/omnidoc · GitHub](https://github.com/playframework/omnidoc/blob/70f04533d0f881a9a7f6c1ac5ec6af1d8bb335f9/project/OmnidocBuild.scala#L88-L92)) to:

1. download `-sources.jar` and `-playdoc.jar` artifacts for each dependency
2. extract the dowloaded `-sources`artifacts into `omnidoc/sources/` 
3. extract the dowloaded `-playdoc`artifacts into `omnidoc/playdoc/` 
4. use `omnidoc/sources/` to produce `omnidoc/javadoc/`
5. use `omnidoc/sources/` to produce `omnidoc/scaladoc/`
6. package `playdoc` into `play/docs/content`
7. package `javadoc` into `play/docs/content/api/java`
8. package `scaladoc` into `play/docs/content/api/scala`

*NOTE:* all the paths above are relative to `omnidoc/target/scala2.1x/`

