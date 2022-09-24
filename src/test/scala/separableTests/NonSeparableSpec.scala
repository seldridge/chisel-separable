// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.Drivers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class NonSeparableSpec extends AnyFunSpec with Matchers {

  /** Here, everything is sitting in a single compilation unit. */
  object CompilationUnit1 {

    /** This is the "DUT". */
    class Bar extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      b := ~a
    }

    /** This is the "Top".  It instantiates the "DUT" multiple times. */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = Module(new Bar)
      bar1.a := a
      bar2.a := bar1.a
      b := bar2.b
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
