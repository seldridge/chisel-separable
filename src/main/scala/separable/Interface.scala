package separable

import chisel3.{BlackBox => _, Module => _, _}
import chisel3.experimental.{ChiselAnnotation, FlatIO, RunFirrtlTransform}
import chisel3.util.experimental.InlineInstance
import firrtl.annotations.Annotation
import firrtl.Transform
import firrtl.passes.{InlineAnnotation, InlineInstances}
import firrtl.transforms.NoDedupAnnotation
import scala.annotation.implicitNotFound

@implicitNotFound(
  "this method requires information from the separable compilation implementation, please bring one into scope as an `implicit val`. You can also consult the team that owns the implementation to refer to which one you should use!"
)
trait ConformsTo[A <: Record, B <: RawModule] {

  protected def genModule: B

  protected def genInterface: Interface[A]

  protected def connect(lhs: A, rhs: B): Unit

  final class Module extends RawModule {
    val io = FlatIO(genInterface.ports)

    val internal = chisel3.Module(genModule)

    val w = Wire(io.cloneType)
    connect(w, internal)

    io <> w

    // TODO: This needs to not be explicitly specified.
    override def desiredName = genInterface.interfaceName

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

  def clockFrequency: BigInt

}

trait Interface[A <: Record] {

  def interfaceName: String

  def ports: A

  final def clockFrequency[B <: RawModule](
  )(
    implicit conformance: ConformsTo[A, B]
  ): BigInt = conformance.clockFrequency

  final class BlackBox extends chisel3.BlackBox {
    val io = IO(ports)

    override final def desiredName = interfaceName
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

  class BarInterface extends Interface[BarBundle] {

    override def interfaceName = "BarWrapper"

    override def ports = new BarBundle

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
    implicit val barConformance = new ConformsTo[BarBundle, Bar] {
      protected override def genModule = new Bar
      protected override def genInterface = new BarInterface
      protected override def connect(lhs: BarBundle, bar: Bar) = {
        bar.x := lhs.a
        lhs.b := bar.y
      }
      override def clockFrequency = 9001
    }
  }

  object CompilationUnit2 {

    import CompilationUnit1.barConformance

    /** This is a module above the "DUT" (Bar). This stamps out the "DUT" twice,
      * but using the blackbox version of it that conforms to the
      * specification-set port list.
      */
    class Foo extends RawModule {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val iface = new BarInterface
      val bar1, bar2 = chisel3.Module(new iface.BlackBox)

      println(s"bar1's clock frequency is: ${iface.clockFrequency()}")

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
    () => new (CompilationUnit1.barConformance.Module)
  )
  Drivers.link(new java.io.File(dir + "/Foo.sv"))

}
