// SPDX-License-Identifier: Apache-2.0

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

  def genModule: B

  def connect(lhs: A, rhs: B): Unit

  def clockFrequency: BigInt

}

trait Interface[A <: Record] {

  /** This is the name of the Interface. */
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

  final class Module[B <: RawModule]()(implicit conformance: ConformsTo[A, B])
      extends RawModule {
    val io = FlatIO(ports)

    val internal = chisel3.Module(conformance.genModule)

    val w = Wire(io.cloneType)
    conformance.connect(w, internal)

    io <> w

    override def desiredName = interfaceName

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
