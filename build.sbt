import Dependencies._

ThisBuild / scalaVersion         := "2.13.8"
ThisBuild / organization         := "com.sifive"
ThisBuild / organizationName     := "SiFive"
ThisBuild / organizationHomepage := Some(url("https://www.sifive.com/"))
ThisBuild / description          := "Infrastructure to define and compile separable Chisel circuits"
ThisBuild / homepage             := Some(url("https://github.com/seldridge/chisel-separable"))
ThisBuild / licenses             := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / developers           := List(
  Developer(
    "seldridge",
    "Schuyler Eldridge",
    "schuyler.eldridge@gmail.com",
    url("https://www.seldridge.dev")
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "chisel-separable",
    libraryDependencies += scalaTest % Test,
    libraryDependencies ++= Seq(chisel3),
    addCompilerPlugin(chiselCompilerPlugin cross CrossVersion.full),
    resolvers +=
      "Sonatype OSS Snapshots" at "https://s01.oss.sonatype.org/content/repositories/snapshots",
    scalacOptions += "-Ymacro-annotations"
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
