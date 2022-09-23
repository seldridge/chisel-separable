package separable

import java.io.File
import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import sys.process._

object Drivers {

  private val stage = new ChiselStage

  /** Compile one or more Chisel Modules into an output directory. The first
    * compile will be put into the output directory. All subsequent compiles
    * will be put in directories called "compile-n" with "n" starting at 0 and
    * incrementing.
    */
  def compile(dir: java.io.File, main: () => RawModule, other: () => RawModule*) = {
    (main +: other).zipWithIndex.foreach {
      case (a, i) =>
        val outputDir = i match {
          case 0 => FirtoolOption(dir.getPath())
          case i => FirtoolOption(dir.getPath() + s"/compile-${i - 1}")
        }

        stage.execute(
          Array("--target", "systemverilog"),
          Seq(
            ChiselGeneratorAnnotation(a),
            FirtoolOption("-split-verilog"),
            FirtoolOption("-o"),
            outputDir
          )
        )
    }
  }

  /** Perform a pseudo-link by running Verilator --lint-only on one or more
    * compiles done using the `compile` method above.
    */
  def link(dir: File, top: String) = {

    /** Generate includes from any subdirectories of "dir". */
    val includes = dir
      .listFiles()
      .collect {
        case f if f.isDirectory => f
      }
      .map(dir => s"-I${dir.getPath()}")

    val cmd: Seq[String] = Seq(
      "verilator",
      "-lint-only",
      dir + "/" + top,
      s"-I${dir.getPath()}"
    ) ++ includes
    cmd !
  }

}
