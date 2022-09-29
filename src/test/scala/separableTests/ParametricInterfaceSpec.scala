// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.{ConformsTo, Drivers, Interface}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ParametricInterfaceSpec extends AnyFunSpec with Matchers {

  /** This is the agreed-upon interface for our separable compilation unit. This
    * is set by specification.
    */
  class BarBundle(width: Int) extends Bundle {
    val a = Input(UInt(width.W))
    val b = Output(UInt(width.W))
  }

  class BarInterface(width: Int) extends Interface[BarBundle, Unit, Int] {

    override def interfaceName = "BarWrapper"

    override def ports(width: Int) = new BarBundle(width)

    override def scalaParameters: Int = width

  }

  object CompilationUnit1 {

    /** This is the "DUT" that will be compiled in one compilation unit and
      * reused multiple times. The designer is free to do whatever they want
      * with this, internally or at the boundary. Note: this has ports which do
      * not match the names of the ports on the agreed upon interface.
      */
    class Bar(width: Int) extends RawModule {
      val x = IO(Input(UInt(width.W)))
      val y = IO(Output(UInt(width.W)))
      y := ~x
    }

    /** The owner of the "DUT" (Bar) needs to write this. This defines how to
      * hook up the "DUT" to the specification-set interface.
      */
    implicit val barConformance =
      new ConformsTo[BarBundle, Bar, Unit, Int] {
        override def genModule(width: Int) = new Bar(width)
        override def connect(lhs: BarBundle, bar: Bar) = {
          bar.x := lhs.a
          lhs.b := bar.y
        }
        override def properties = Unit
      }
  }

  import CompilationUnit1.barConformance

  object CompilationUnit2 {

    private val width = 32

    implicit val interface = new BarInterface(width)

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(UInt(width.W)))
      val b = IO(Output(UInt(width.W)))

      val bar1, bar2 = chisel3.Module(new interface.BlackBox)

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
  private val dir = new java.io.File("build/ParametricInterfaces")

  describe("Behavior of Parametric Interfaces") {

    it("should compile a design separably") {

      import CompilationUnit2.interface

      info("compile okay!")
      Drivers.compile(
        dir,
        () => new CompilationUnit2.Foo,
        () => new (interface.Module)
      )

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
