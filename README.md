# Omnidoc

[![Twitter Follow](https://img.shields.io/twitter/follow/playframework?label=follow&style=flat&logo=twitter&color=brightgreen)](https://twitter.com/playframework)
[![Discord](https://img.shields.io/discord/931647755942776882?logo=discord&logoColor=white)](https://discord.gg/g5s2vtZ4Fa)
[![GitHub Discussions](https://img.shields.io/github/discussions/playframework/playframework?&logo=github&color=brightgreen)](https://github.com/playframework/playframework/discussions)
[![StackOverflow](https://img.shields.io/static/v1?label=stackoverflow&logo=stackoverflow&logoColor=fe7a16&color=brightgreen&message=playframework)](https://stackoverflow.com/tags/playframework)
[![YouTube](https://img.shields.io/youtube/channel/views/UCRp6QDm5SDjbIuisUpxV9cg?label=watch&logo=youtube&style=flat&color=brightgreen&logoColor=ff0000)](https://www.youtube.com/channel/UCRp6QDm5SDjbIuisUpxV9cg)
[![Twitch Status](https://img.shields.io/twitch/status/playframework?logo=twitch&logoColor=white&color=brightgreen&label=live%20stream)](https://www.twitch.tv/playframework)
[![OpenCollective](https://img.shields.io/opencollective/all/playframework?label=financial%20contributors&logo=open-collective)](https://opencollective.com/playframework)

[![Build Status](https://github.com/playframework/omnidoc/actions/workflows/build-test.yml/badge.svg)](https://github.com/playframework/omnidoc/actions/workflows/build-test.yml)
[![Maven](https://img.shields.io/maven-central/v/org.playframework/play-omnidoc_2.13.svg?logo=apache-maven)](https://mvnrepository.com/artifact/org.playframework/play-omnidoc_2.13)
[![Repository size](https://img.shields.io/github/repo-size/playframework/omnidoc.svg?logo=git)](https://github.com/playframework/omnidoc)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Mergify Status](https://img.shields.io/endpoint.svg?url=https://api.mergify.com/v1/badges/playframework/omnidoc&style=flat)](https://mergify.com)

Omnidoc is an sbt build that adds to sbt's `mappings in (Compile, packageBin)` to aggregate source code and manuals produced within the Play ecosystem and produce a single deliverable. This must not be confused with Interplay's [Omnidoc](https://github.com/playframework/interplay/blob/main/src/main/scala/interplay/Omnidoc.scala) which _simply_ adds some metadata on the `-source.jar` artifact of every Play library. 

The resulting deliverable includes:

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

## Releasing a new version

Omnidoc will be released as part of a Play Framework release. See https://github.com/playframework/.github/blob/main/RELEASING.md
