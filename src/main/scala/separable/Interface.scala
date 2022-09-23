package separable

import chisel3.{BlackBox => _, Module => _, _}
import chisel3.experimental.{ChiselAnnotation, FlatIO, RunFirrtlTransform}
import chisel3.util.experimental.InlineInstance
import firrtl.annotations.Annotation
import firrtl.Transform
import firrtl.passes.{InlineAnnotation, InlineInstances}
import firrtl.transforms.NoDedupAnnotation

trait Interface[A <: Record, B <: RawModule] {

  protected def genIO: A

  protected def genModule: B

  protected def connect(lhs: A, rhs: B): Unit

  class Module extends RawModule {
    val io = FlatIO(genIO)

    val internal = chisel3.Module(genModule)

    val w = Wire(io.cloneType)
    connect(w, internal)

    io <> w

    // TODO: This needs to not be explicitly specified.
    override def desiredName = "BarWrapper"

    Seq(
      new ChiselAnnotation with RunFirrtlTransform {
        def toFirrtl:       Annotation = InlineAnnotation(internal.toNamed)
        def transformClass: Class[_ <: Transform] = classOf[InlineInstances]
      },
      new ChiselAnnotation {
        def toFirrtl: Annotation = NoDedupAnnotation(internal.toNamed)
      }
    )
      .map(chisel3.experimental.annotate(_))

  }

}

object InterfacesMain extends App {

  /** This is the agreed-upon interface for our separable compilation unit. This
    * is set by specification.
    */
  class BarBundle extends Bundle {
    val a = Input(Bool())
    val b = Output(Bool())
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
    val BarInterface = new Interface[BarBundle, Bar] {
      protected override def genIO = new BarBundle
      protected override def genModule = new Bar
      protected override def connect(lhs: BarBundle, bar: Bar) = {
        bar.x := lhs.a
        lhs.b := bar.y
      }
    }
  }

  object CompilationUnit2 {

    /** This is the boundary of a separable compilation unit. This is just a
      * BlackBox that has the interface as its sole io. This is written by the
      * spec author. (This can be simplified to be auto-generated from
      * BarBundle.)
      */
    class BarBlackBox extends chisel3.BlackBox {
      val io = IO(new BarBundle)

      // TODO: This needs to not be explicitly specified.
      override def desiredName = "BarWrapper"
    }

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1 = chisel3.Module(new BarBlackBox)
      val bar2 = chisel3.Module(new BarBlackBox)

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
  Drivers.compile(
    dir,
    () => new CompilationUnit2.Foo,
    () => new (CompilationUnit1.BarInterface.Module)
  )
  Drivers.link(new java.io.File(dir + "/Foo.sv"))

}
