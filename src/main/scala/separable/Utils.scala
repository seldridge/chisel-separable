package separable

import java.io.File
import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import circt.stage.{ChiselStage, FirtoolOption}
import sys.process._

object Drivers {

  private val stage = new ChiselStage

  def compile(dir: java.io.File, gen: () => RawModule*) = {
    dir.delete()

    gen.foreach { a =>
      stage.execute(
        Array("--target", "systemverilog"),
        Seq(
          ChiselGeneratorAnnotation(a),
          FirtoolOption("-split-verilog"),
          FirtoolOption("-o"),
          FirtoolOption(dir.getPath())
        )
      )
    }
  }

  def link(top: File) = {
    val cmd: Seq[String] = Seq(
      "verilator",
      "-lint-only",
      s"-I${top.getParent()}",
      top.getAbsolutePath()
    )
    cmd !
  }

}
