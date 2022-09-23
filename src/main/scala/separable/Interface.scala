package separable

import chisel3.{BlackBox => _, _}
import chisel3.experimental.FlatIO
import chisel3.util.experimental.InlineInstance

trait Interface[A <: Record, B <: RawModule] {

  protected def genIO: A

  protected def genModule: B with InlineInstance

  protected def convert(b: B): A

  class Module extends RawModule {
    val io = FlatIO(Input(genIO))

    io := convert(Module(genModule))
  }

}

object InterfacesMain extends App {

  /** This is the agreed-upon interface for our separable compilation unit. This
    * is set by specification.
    */
  class BarBundle extends Bundle {
    val a = Bool()
    val b = Flipped(Bool())
  }

  /** This is the "DUT" that will be compiled in one compilation unit and reused
    * multiple times. The designer is free to do whatever they want with this,
    * internally or at the boundary. Note: this has ports which do not match the
    * names of the ports on the agreed upon interface.
    */
  class Bar extends RawModule {
    val x = IO(Input(Bool()))
    val y = IO(Output(Bool()))

    y := ~x
  }

  /** The owner of the "DUT" (Bar) needs to write this. This defines how to hook
    * up the "DUT" to the specification-set interface.
    */
  val BarInterface = new Interface[BarBundle, Bar] {
    protected override def genIO = new BarBundle
    protected override def genModule = new Bar with InlineInstance
    protected override def convert(bar: Bar): BarBundle = {
      val w = Wire(genIO)
      // bar.x := w.a
      // w.b := bar.ya
      w
    }
  }

  /** This is the boundary of a separable compilation unit. This is just a
    * BlackBox that has the interface as its sole io. This is written by the
    * spec author. (This can be simplified to be auto-generated from BarBundle.)
    */
  class BarBlackBox extends chisel3.BlackBox {
    val io = IO(Input(new BarBundle))

    override def desiredName = "Bar"
  }

  /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
    * but using the blackbox version of it that conforms to the
    * specification-set port list.
    */
  class Foo extends RawModule {
    val a = IO(Input(Bool()))
    val b = IO(Output(Bool()))

    val bar1 = Module(new BarBlackBox)
    val bar2 = Module(new BarBlackBox)

    bar1.io.a := a
    bar2.io.a := bar1.io.b
    b := bar2.io.b
  }

  /** Now we compile the design into the "build/Interfaces" directory. Both
    * "Foo" and one copy of the "DUT", using the utility in "BarInterface", are
    * compiled in separate processes. Finally, Verilator is run to check that
    * everything works.
    */
  private val dir = new java.io.File("build/Interfaces")
  Drivers.compile(dir, () => new Foo, () => new (BarInterface.Module))
  Drivers.link(new java.io.File(dir + "/Foo.sv"))

}
