// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.{ConformsTo, Drivers, Interface}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** This modifies `InterfaceSpec` to make the interface take a Scala parameter.
  * Notably, a concrete interface object with the parameter resolved is then
  * used. The interface is not generated from either the client or the
  * component, but used to configure both.
  */
class ParametricInterfaceSpec extends AnyFunSpec with Matchers {

  /** This is the agreed-upon port-level interface. */
  class BarBundle(width: Int) extends Bundle {
    val a = Input(UInt(width.W))
    val b = Output(UInt(width.W))
  }

  /** This is the definition of the interface. This has an integer parameter. */
  class BarInterface(width: Int) extends Interface[BarBundle, Unit, Int] {

    override def interfaceName = "BarWrapper"

    override def ports(width: Int) = new BarBundle(width)

    override def parameters: Int = width

  }

  object CompilationUnit1 {

    /** This is the "DUT" that will be compiled in one compilation unit and
      * reused multiple times. The designer is free to do whatever they want
      * with this, internally or at the boundary. The port-level interface of
      * this module does not align with the interface. The width of the ports is
      * Scala-parametric.
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

        override def properties = ()
      }
  }

  object CompilationUnit2 {

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list. This is dependent upon having a
      * `BarInterface` in order to configure itself. This is an example of
      * bottom-up parameterization where something at the leaf of the instance
      * hierarchy (an `iface.BlackBox`) affects its parents.
      */
    class Foo(iface: BarInterface) extends RawModule {
      val a = IO(Input(UInt(iface.parameters.W)))
      val b = IO(Output(UInt(iface.parameters.W)))

      private val bar1, bar2 = chisel3.Module(new (iface.BlackBox))

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

      /** Create an object that has the width of the interface known. The
        * interface is in charge of configuring itself. If either the client or
        * the component want to configure themselves based on the interface,
        * they need to query the interface.
        */
      val interface = new BarInterface(width = 32)

      /** Import Bar's conformance so that we can build it's conforming wrapper.
        */
      import CompilationUnit1.barConformance

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit2.Foo(interface)),
        Drivers.CompilationUnit(() => new interface.Module)
      )

      info("link okay!")
      Drivers.link(dir, "Foo.sv")

    }

  }

}
