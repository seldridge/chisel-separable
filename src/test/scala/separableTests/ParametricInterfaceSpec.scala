// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import separable.{ConformsTo, Drivers, Interface, InterfaceGenerator}
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

  /** This is an Interface Generator. It can be used to assist in the definition
    * of multiple copies of a related Interface.
    */
  class BarInterfaceGenerator private[separableTests] (width: Int)
      extends InterfaceGenerator {

    override type Key = Int

    override type Ports = BarBundle

    override def ports() = new BarBundle(width)

  }

  /** This is a package of different BarInterfaces. */
  object Package extends separable.Package[BarInterfaceGenerator] {

    /** A BarInterfaceGenerator with width 32. */
    object BarInterface32 extends BarInterfaceGenerator(32) with Interface

    /** A BarInterfaceGenerator with width 64. */
    object BarInterface64 extends BarInterfaceGenerator(64) with Interface

    override protected def members = Map(
      32 -> BarInterface32,
      64 -> BarInterface64
    )

  }

  /** Bring necessary things from the Package into scope. */
  import Package.{BarInterface32, BarInterface64}

  object CompilationUnit1 {

    /** This is the "DUT" that will be compiled in one compilation unit and
      * reused multiple times. The designer is free to do whatever they want
      * with this, internally or at the boundary. The port-level interface of
      * this module does not align with the interface. The width of the ports is
      * Scala-parametric.
      */
    class Bar32 extends RawModule {
      val x = IO(Input(UInt(32.W)))
      val y = IO(Output(UInt(32.W)))
      y := ~x
    }

    /** Define how a Bar32 conforms to a BarInterface32. This is a straight
      * mapping of ports.
      */
    implicit val bar32Conformance =
      new ConformsTo[BarInterface32.type, Bar32] {

        override def genModule() = new Bar32

        override def portMap(lhs: BarBundle, bar: Bar32) = {
          bar.x := lhs.a
          lhs.b := bar.y
        }

      }

    /** Define how a Bar32 conforms to a BarInterface64. This requires some
      * squeezing/padding of ports which are incorrectly sized.
      */
    implicit val bar64Conformance =
      new ConformsTo[BarInterface64.type, Bar32] {

        override def genModule() = new Bar32

        override def portMap(lhs: BarBundle, bar: Bar32) = {
          bar.x := lhs.a(31, 0)
          lhs.b := bar.y.pad(64)
        }

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
    class Foo32 extends RawModule {
      val a = IO(Input(UInt(32.W)))
      val b = IO(Output(UInt(32.W)))

      private val intf = Package.lookup(32)
      private val bar1, bar2 = chisel3.Module(new intf.BlackBox)

      bar1.io.a := a
      bar2.io.a := bar1.io.b
      b := bar2.io.b
    }
  }

  object CompilationUnit3 {

    /** This testharness wants to use a different, 64-bit Interface.
      */
    class Foo64 extends RawModule {
      val a = IO(Input(UInt(64.W)))
      val b = IO(Output(UInt(64.W)))

      private val intf = Package.lookup(64)
      private val bar1, bar2 = chisel3.Module(new intf.BlackBox)

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
  describe("Behavior of Parametric Interfaces") {

    it(
      "should compile a design separably for a 32-bit variant of the Interface"
    ) {

      val dir = new java.io.File("build/ParametricInterfaces32")

      /** Import Bar's conformance so that we can build it's conforming wrapper.
        */
      import CompilationUnit1.bar32Conformance

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit2.Foo32),
        Drivers.CompilationUnit(() => new (BarInterface32.Module))
      )

      info("link okay!")
      Drivers.link(dir, "compile-0/Foo32.sv")

    }

    it(
      "should compile a design separably for a 64-bit variant of the Interface"
    ) {

      val dir = new java.io.File("build/ParametricInterfaces64")

      /** Import Bar's conformance so that we can build it's conforming wrapper.
        */
      import CompilationUnit1.bar64Conformance

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(() => new CompilationUnit3.Foo64),
        Drivers.CompilationUnit(() => new (BarInterface64.Module))
      )

      info("link okay!")
      Drivers.link(dir, "compile-0/Foo64.sv")

    }

  }

}
