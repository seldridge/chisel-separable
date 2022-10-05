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

  /** This is the agreed-upon port-level interface. */
  class BarBundle extends Bundle {
    val a = Input(Bool())
    val b = Output(Bool())
  }

  /** These are properties that a valid conformance must define. This represents
    * information flowing from a component to a client through the interface.
    */
  case class BarProperties(id: Int)

  /** This is the definition of the interface. */
  object BarInterface extends Interface[BarBundle, BarProperties, Unit] {

    /** This will be used to name the BlackBox and wrapper module. */
    override def interfaceName = "BarWrapper"

    /** Generate the ports given the parameters. */
    override def ports(params: Unit) = new BarBundle

    /** Return the parameters of this interface. */
    override def parameters = ()

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
      new ConformsTo[BarBundle, Bar, BarProperties, Unit] {

        override def genModule(a: Unit) = new Bar

        override def connect(lhs: BarBundle, bar: Bar) = {
          bar.x := lhs.a
          lhs.b := bar.y
        }

        /** Return the properties. The conformance is free to do whatever they
          * want here. They may run Chisel elaboration, FIRRTL compilation,
          * search through a place-and-route report, or just return the
          * information if it is already known.
          */
        override def properties = BarProperties(id = 42)

      }
  }

  object CompilationUnit2 {

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = chisel3.Module(new BarInterface.BlackBox)

      bar1.io.a := a
      bar2.io.a := bar1.io.b
      b := bar2.io.b
    }
  }

  /** Now we compile the design into the "build/Interfaces" directory. Both
    * "Foo" and one copy of the "DUT", using the utility in "BarInterface", are
    * compiled in separate processes. Finally, Verilator is run to check that
    * everything works.
    */
  private val dir = new java.io.File("build/Interfaces")

  describe("Behavior of Interfaces") {

    /** Bring the conformance into scope so that we can build the wrapper
      * module. If this is not brought into scope, trying to build a
      * `BarInterface.Module` will fail during Scala compilation.
      */
    import CompilationUnit1.barConformance

    it("should compile a design separably") {

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit2.Foo),
        Drivers.CompilationUnit(() => new (BarInterface.Module))
      )

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
