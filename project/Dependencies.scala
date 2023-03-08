import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.7"
  private val chiselVersion = "5.0.0-M1+7-73cdddb1-SNAPSHOT"
  lazy val chisel3 = "org.chipsalliance" %% "chisel" % chiselVersion
  lazy val chiselCompilerPlugin = "org.chipsalliance" %% "chisel-plugin" % chiselVersion
}
