package separable

import chisel3._

object NonSeparableMain extends App {

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

  private val dir = new java.io.File("build/NonSeparable")

  Drivers.compile(dir, () => new CompilationUnit1.Foo)
  Drivers.lint(new java.io.File(dir + "/Foo.sv"))

}
