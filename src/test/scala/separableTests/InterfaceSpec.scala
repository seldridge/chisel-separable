// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.{ConformsTo, Drivers, Interface}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class InterfaceSpec extends AnyFunSpec with Matchers {

  /** This is the agreed-upon interface for our separable compilation unit. This
    * is set by specification.
    */
  class BarBundle extends Bundle {
    val a = Input(Bool())
    val b = Output(Bool())
  }

  case class BarProperties(id: Int)

  val iface = new Interface[BarBundle, BarProperties, Unit] {

    override def interfaceName = "BarWrapper"

    override def ports(params: Unit) = new BarBundle

    override def parameters = ()

  }

  object CompilationUnit1 {

    /** This is the "DUT" that will be compiled in one compilation unit and
      * reused multiple times. The designer is free to do whatever they want
      * with this, internally or at the boundary. Note: this has ports which do
      * not match the names of the ports on the agreed upon interface.
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
        override def properties = BarProperties(id = 42)
      }
  }

  import CompilationUnit1.barConformance

  object CompilationUnit2 {

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = chisel3.Module(new iface.BlackBox)

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

    it("should compile a design separably") {

      info("compile okay!")
      Drivers.compile(
        dir,
        () => new CompilationUnit2.Foo,
        () => new (iface.Module)
      )

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
