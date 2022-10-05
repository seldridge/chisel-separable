// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import chisel3.experimental.FlatIO
import chisel3.util.experimental.InlineInstance
import separable.Drivers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** This modifies the `separableTests.NonSeparableSpec` to manually elaborate
  * and compile `Foo` and `Bar` separately. This is done by manually
  * instantiating a `BlackBox`, called `BarBlackBox`, inside `Foo` and wrapping
  * `Bar` in a wrapper `Module`, `BarWrapper`, that has the same port-level
  * interface as `BarBlackBox`.
  */
class SeparableBlackBoxSpec extends AnyFunSpec with Matchers {

  /** This is the port-level interface between the separable compilation units.
    */
  class BarBundle extends Bundle {
    val a = Input(Bool())
    val b = Output(Bool())
  }

  /** This is everything in the first compilation unit. */
  object CompilationUnit1 {

    /** This is the top-module of the first compilation unit. The port-level
      * interface of this module has to line up with `BarBlackBox` or else
      * linking will fail.
      */
    class BarWrapper extends RawModule {
      val io = FlatIO(new BarBundle)

      /** Here, `Bar` is instantiated and connected to the port-level interface
        * of `BarWrapper`. This is simple connections, but may be more
        * complicated logic if necessary.
        */
      private val bar = Module(new Bar with InlineInstance)
      bar.x := io.a
      io.b := bar.y
    }

    /** This module is a "DUT" and will be instantiated multiple times. This has
      * a different port-level interface that is different from `BarBundle`.
      */
    class Bar extends RawModule {
      val x = IO(Input(Bool()))
      val y = IO(Output(Bool()))

      y := ~x
    }
  }

  /** This is everything in the second compilation unit. */
  object CompilationUnit2 {

    /** This module is a "DUT" without implementation. It will be used anytime
      * somebody would instantiate the "DUT". This is manually written and needs
      * to have a port-level interface that lines up with `BarWrapper.
      */
    class BarBlackBox extends BlackBox {
      val io = IO(new BarBundle)
      override def desiredName = "BarWrapper"
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

  /** We then build the "Top" and "DUT" in entirely separate compilation units.
    * They dump out Verilog into the output directory. We then do a low-level
    * "link" by checking that Verilator lints the entire design, i.e., that the
    * "DUT" matches the ports expected by the "Top".
    */
  describe("Separable Compilation Using BlackBoxes") {

    it("should compile a design non-separably") {

      val dir = new java.io.File("build/SeparableBlackBox")

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit2.Foo),
        Drivers.CompilationUnit(() => new CompilationUnit1.BarWrapper)
      )

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
