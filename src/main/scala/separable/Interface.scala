// SPDX-License-Identifier: Apache-2.0

package separable

import chisel3.{BlackBox => _, Module => _, _}
import chisel3.experimental.{BaseModule, FlatIO}
import chisel3.experimental.dataview._
import scala.annotation.implicitNotFound

@implicitNotFound(
  "this method requires information from the separable compilation implementation, please bring one into scope as an `implicit val`. You can also consult the team that owns the implementation to refer to which one you should use!"
)
trait ConformsTo[Intf <: Interface, Mod <: BaseModule] {

  /** Return the module that conforms to a port-level interface. */
  private[separable] def genModule(): Mod

  /** Define how this module hooks up to the port-level interface. */
  private[separable] def portMap: Seq[(Mod, Intf#Ports) => (Data, Data)]

}

/** Functionality which is common */
sealed trait InterfaceCommon {

  private[separable] type Ports <: Record

  /** Returns the Record that is the port-level interface. */
  private[separable] def ports(): Ports

}

/** A generator of different Interfaces. Currently, this is just an Interface
  * that is not a Singleton.
  */
trait InterfaceGenerator extends InterfaceCommon {

  type Key

}

/** An interface between hardware units. Any module that implements this
  * interface may be separately compiled from any module that instantiates this
  * interface.
  */
trait Interface extends InterfaceCommon { self: Singleton =>

  /** This types represents the type of a valid conformance to this Interface.
    */
  private type Conformance[Mod <: RawModule] = ConformsTo[this.type, Mod]

  /** The name of this interface. This will be used as the name of any module
    * that implements this interface.
    * I.e., this is the name of the `BlackBox` and `Module` that are provided
    * below. The implementation of this method is just coming up with a good
    * name derived from the class name. (The name of the Interface in FIRRTL
    * will be the name of the Scala class that extends the Interface.)
    */
  private[separable] def interfaceName: String = {
    val className = getClass().getName()
    className
      .drop(className.lastIndexOf('.') + 1)
      .split('$')
      .toSeq
      .filter {
        case str if str.forall(_.isDigit) => false
        case str                          => true
      }
      .last
  }

  sealed trait Entity { this: BaseModule =>
    val io: Ports
  }

  object Wrapper {

    /** The black box that has the same ports as this interface. This is what is
      * instantiated by any user of this interface, i.e., a test harness.
      */
    final class BlackBox extends chisel3.BlackBox with Entity {
      final val io = IO(ports())

      override final def desiredName = interfaceName
    }

    /** The module that wraps any module which conforms to this Interface.
      */
    final class Module[B <: RawModule](
    )(
      implicit conformance: Conformance[B])
        extends RawModule
        with Entity {
      final val io = FlatIO(ports())

      // Use a dummy clock and reset connection when constructing the module.
      // This is fine as we rely on DataView to catch missing connections to
      // clock and reset.  The dummy clock and reset will never be used.
      private val internal = withClockAndReset(
        WireInit(Clock(), DontCare),
        WireInit(Reset(), DontCare)
      ) {
        chisel3.Module(conformance.genModule())
      }

      private implicit val pm = PartialDataView[B, Ports](
        _ => ports(),
        conformance.portMap: _*
      )
      io :<>= internal.viewAs[Ports]

      override def desiredName = interfaceName
    }

    /** A stub module that implements the interface. All IO of this module are
      * just tied off.
      */
    final class Stub extends RawModule with Entity {
      final val io = FlatIO(ports())
      io := DontCare
      dontTouch(io)
    }

  }

}
