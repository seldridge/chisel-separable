package separable

import chisel3._
import chisel3.experimental.FlatIO
import circt.stage.ChiselStage

object SeparableBlackBoxMain extends App {

  /** This is the agreed-upon interface between the separable compilation units. */
  class BarBundle extends Bundle {
    val a = Input(Bool())
    val b = Output(Bool())
  }

  /** This is everything in the first compilation unit. */
  object CompilationUnit1 {

    /** This module is a "DUT" and will be stamped out multiple times.  It implements the interface. */
    class Bar extends RawModule {
      val io = FlatIO(new BarBundle)

      io.b := ~io.a
    }

  }

  /** This is everything in the second compilation unit. */
  object CompilationUnit2 {

    /** This module is a "DUT" without implementation.  It will be used anytime somebody would instantiate the "DUT". */
    class BarBlackBox extends BlackBox {
      val io = IO(new BarBundle)

      override def desiredName = "Bar"
    }

    /** This is the "Top" and it stamps out the "DUT" twice. */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = Module(new BarBlackBox)

      bar1.io.a := a
      bar2.io.a := bar1.io.b
      b := bar2.io.b
    }
  }

  /** We then build the "Top" and "DUT" in entirely separate compilation units.  They dump out Verilog into the output
    * directory.  We then do a low-level "link" by checking that Verilator lints the entire design, i.e., that the "DUT"
    * matches the ports expected by the "Top".
    */
  private val dir = new java.io.File("build/SeparableBlackBox")
  Drivers.compile(dir, () => new CompilationUnit1.Bar, () => new CompilationUnit2.Foo)
  Drivers.lint(new java.io.File(dir + "/Foo.sv"))

}
