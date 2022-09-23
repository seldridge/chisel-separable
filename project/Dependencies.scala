import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.7"
  private val chiselVersion = "3.5.3"
  lazy val chisel3 = "edu.berkeley.cs" %% "chisel3" % chiselVersion
  lazy val chiselCompilerPlugin = "edu.berkeley.cs" %% "chisel3-plugin" % chiselVersion
  lazy val `chisel-circt` = "com.sifive" %% "chisel-circt" % "0.6.0"
}
