import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.7"
  private val chiselVersion = "5.0.0-M1+32-baed66d4-SNAPSHOT"
  lazy val chisel3 = "org.chipsalliance" %% "chisel" % chiselVersion
  lazy val chiselCompilerPlugin = "org.chipsalliance" %% "chisel-plugin" % chiselVersion
}
