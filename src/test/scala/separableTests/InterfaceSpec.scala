// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.{ConformsTo, Drivers, Interface}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** This modifies the `separableTests.SeparableBlackBoxSpec` to make the
  * generation of that example's `BarBlackBox` and `BarWrapper` automated using
  * `separable.Interface` and `separable.ConformsTo`.
  */
class InterfaceSpec extends AnyFunSpec with Matchers {

  /** This is the definition of the interface. */
  object BarInterface extends Interface {

    case class Struct(a: Int, b: String, c: Data)

    /** This is the agreed-upon port-level interface. */
    final class BarBundle extends Bundle {
      val a = Input(Bool())
      val b = Output(Bool())
    }

    override type Ports = BarBundle

    /** Generate the ports given the parameters. */
    override def ports() = new Ports

    /** This is a Scala integer value on the Interface. */
    final val int = 42

    /** This is a Scala string value on the Interface. */
    final val string = "hello world"

  }

  object CompilationUnit1 {

    /** This is the "DUT" that will be compiled in one compilation unit and
      * reused multiple times. The designer is free to do whatever they want
      * with this, internally or at the boundary. The port-level interface of
      * this module does not align with the interface.
      */
    class Bar extends RawModule {
      val x = IO(Input(Bool()))
      val y = IO(Output(Bool()))
      y := ~x
    }

    /** The owner of the "DUT" (Bar) needs to write this. This defines how to
      * hook up the "DUT" to the specification-set interface.
      */
    implicit val barConformance =
      new ConformsTo[BarInterface.type, Bar] {

        override def genModule() = new Bar

        override def portMap = Seq(
          _.x -> _.a,
          _.y -> _.b
        )

      }
  }

  object CompilationUnit2 {

    /** This is an alternative "DUT" that is differnet from the actual DUT, Bar.
      * It differs in its ports and internals.
      */
    class Baz extends RawModule {
      val hello = IO(Input(Bool()))
      val world = IO(Output(Bool()))
      world := hello
    }

    /** The owner of the "DUT" (Bar) needs to write this. This defines how to
      * hook up the "DUT" to the specification-set interface.
      */
    implicit val bazConformance =
      new ConformsTo[BarInterface.type, Baz] {

        override def genModule() = new Baz

        override def portMap = Seq(
          _.hello -> _.a,
          _.world -> _.b
        )

      }
  }

  object CompilationUnit3 {

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = chisel3.Module(new BarInterface.Wrapper.BlackBox)

      println(s"""|BarInterface has the following Scala values:
                  |  - int: ${BarInterface.int}
                  |  - string: ${BarInterface.string}""".stripMargin)

      bar1.io.a := a
      bar2.io.a := bar1.io.b
      b := bar2.io.b
    }
  }

  describe("Behavior of Interfaces") {

    it("should compile a design separably") {

      /** Now we compile the design into the "build/Interfaces" directory. Both
        * "Foo" and one copy of the "DUT", using the utility in "BarInterface",
        * are compiled in separate processes. Finally, Verilator is run to check
        * that everything works.
        */
      val dir = new java.io.File("build/Interfaces_Foo_Bar")

      /** Bring the conformance into scope so that we can build the wrapper
        * module. If this is not brought into scope, trying to build a
        * `BarInterface.Module` will fail during Scala compilation.
        */
      import CompilationUnit1.barConformance

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit3.Foo),
        Drivers.CompilationUnit(() => new (BarInterface.Wrapper.Module))
      )

      info("link okay!")
      Drivers.link(dir, "compile-0/Foo.sv")

    }

    it("should compile an alternative design separately") {

      val dir = new java.io.File("build/Interfaces_Foo_Baz")

      import CompilationUnit2.bazConformance

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit3.Foo),
        Drivers.CompilationUnit(() => new (BarInterface.Wrapper.Module))
      )

      info("link okay!")
      Drivers.link(dir, "compile-0/Foo.sv")

    }

  }

}
