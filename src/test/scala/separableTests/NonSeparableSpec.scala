// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.Drivers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** This shows an example of a non-separately compiled circuit. A top-level
  * `Module` called `Foo` instantiates two copies of another `Module` called
  * `Bar`. `Foo` and `Bar` are elaborated and compiled together.
  */
class NonSeparableSpec extends AnyFunSpec with Matchers {

  /** Here, everything is sitting in a single compilation unit. */
  object CompilationUnit1 {

    /** This is a submodule instantiated by "Foo". */
    class Bar extends RawModule {
      val x = IO(Input(Bool()))
      val y = IO(Output(Bool()))

      y := ~x
    }

    /** This is the top module.  It instantiates "Bar" twice. */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = Module(new Bar)
      bar1.x := a
      bar2.x := bar1.y
      b := bar2.y
    }

  }

  describe("Non-separable Compilation") {

    it("should compile a design non-separably") {

      val dir = new java.io.File("build/NonSeparable")

      info("compile okay!")
      Drivers.compile(dir, () => new CompilationUnit1.Foo)

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
