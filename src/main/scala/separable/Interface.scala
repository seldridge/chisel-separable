// SPDX-License-Identifier: Apache-2.0

package separable

import chisel3.{BlackBox => _, Module => _, _}
import chisel3.experimental.{ChiselAnnotation, FlatIO}
import chisel3.util.experimental.InlineInstance
import firrtl.annotations.Annotation
import firrtl.passes.InlineAnnotation
import firrtl.transforms.NoDedupAnnotation
import scala.annotation.implicitNotFound

@implicitNotFound(
  "this method requires information from the separable compilation implementation, please bring one into scope as an `implicit val`. You can also consult the team that owns the implementation to refer to which one you should use!"
)
trait ConformsTo[Ports <: Record, Mod <: RawModule, Props, Params] {

  /** Return the module that conforms to a port-level interface. */
  private[separable] def genModule(params: Params): Mod

  /** Define how this module hooks up to the port-level interface. */
  private[separable] def connect(lhs: Ports, rhs: Mod): Unit

  /** Return implementation-specific information that is different for each
    * module that conforms to this interface.
    */
  def properties: Props

}

/** An interface between hardware units. Any module that implements this
  * interface may be separately compiled from any module that instantiates this
  * interface.
  */
trait Interface[Ports <: Record, Props, Params] {

  private type Conformance[Mod <: RawModule] =
    ConformsTo[Ports, Mod, Props, Params]

  /** The name of this interface. This will be used as the name of any module
    * that implements this interface. I.e., this is the name of the `BlackBox`
    * and `Module` that are provided below.
    */
  private[separable] def interfaceName: String

  /** Returns the Record that is the port-level interface. */
  private[separable] def ports(params: Params): Ports

  /** An object that can be used to configure the generation of the client.
    */
  def parameters: Params

  /** A method to query properties about a module that conforms to an
    * interface.g
    */
  def properties[Mod <: RawModule](
    implicit conformance: Conformance[Mod]
  ): Props =
    conformance.properties

  /** The black box that has the same ports as this interface. This is what is
    * instantiated by any user of this interface, i.e., a test harness.
    */
  final class BlackBox extends chisel3.BlackBox {
    val io = IO(ports(parameters))

    override final def desiredName = interfaceName
  }

  /** The module that wraps any module which conforms to this Interface.
    */
  final class Module[B <: RawModule](
  )(
    implicit conformance: Conformance[B])
      extends RawModule {
    val io = FlatIO(ports(parameters))

    val internal = chisel3.Module(conformance.genModule(parameters))

    val w = Wire(io.cloneType)
    conformance.connect(w, internal)

    io <> w

    override def desiredName = interfaceName

    Seq(
      new ChiselAnnotation {
        def toFirrtl: Annotation = InlineAnnotation(internal.toNamed)
      },
      new ChiselAnnotation {
        def toFirrtl: Annotation = NoDedupAnnotation(internal.toNamed)
      }
    )
      .map(chisel3.experimental.annotate(_))

  }

  /** A stub module that implements the interface. All IO of this module are
    * just tied off.
    */
  final class Stub extends RawModule {
    val io = FlatIO(ports(parameters))
    io := DontCare
    dontTouch(io)
  }

}
