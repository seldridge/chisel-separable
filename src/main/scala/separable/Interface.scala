// SPDX-License-Identifier: Apache-2.0

package separable

import chisel3.{BlackBox => _, Module => _, _}
import chisel3.experimental.{BaseModule, ChiselAnnotation, FlatIO}
import chisel3.util.experimental.InlineInstance
import firrtl.annotations.Annotation
import firrtl.passes.InlineAnnotation
import firrtl.transforms.NoDedupAnnotation
import scala.annotation.implicitNotFound

@implicitNotFound(
  "this method requires information from the separable compilation implementation, please bring one into scope as an `implicit val`. You can also consult the team that owns the implementation to refer to which one you should use!"
)
trait ConformsTo[Ports <: Record, Mod <: BaseModule] {

  /** Return the module that conforms to a port-level interface. */
  private[separable] def genModule(): Mod

  /** Define how this module hooks up to the port-level interface. */
  private[separable] def portMap(lhs: Ports, rhs: Mod): Unit

}

/** An interface between hardware units. Any module that implements this
  * interface may be separately compiled from any module that instantiates this
  * interface.
  */
trait Interface[Ports <: Record] {

  private type Conformance[Mod <: RawModule] = ConformsTo[Ports, Mod]

  /** The name of this interface. This will be used as the name of any module
    * that implements this interface. I.e., this is the name of the `BlackBox`
    * and `Module` that are provided below.
    */
  private[separable] def interfaceName: String

  /** Returns the Record that is the port-level interface. */
  private[separable] def ports(): Ports

  /** The black box that has the same ports as this interface. This is what is
    * instantiated by any user of this interface, i.e., a test harness.
    */
  final class BlackBox extends chisel3.BlackBox {
    val io = IO(ports())

    override final def desiredName = interfaceName
  }

  /** The module that wraps any module which conforms to this Interface.
    */
  final class Module[B <: RawModule](
  )(
    implicit conformance: Conformance[B])
      extends RawModule {
    val io = FlatIO(ports())

    val internal = chisel3.Module(conformance.genModule())

    val w = Wire(io.cloneType)
    conformance.portMap(w, internal)

    io <> w

    override def desiredName = interfaceName

  }

  /** A stub module that implements the interface. All IO of this module are
    * just tied off.
    */
  final class Stub extends RawModule {
    val io = FlatIO(ports())
    io := DontCare
    dontTouch(io)
  }

}
