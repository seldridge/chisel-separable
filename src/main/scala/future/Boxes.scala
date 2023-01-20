// SPDX-License-Identifier: Apache-2.0

package future

import chisel3.Data
import chisel3.reflect.DataMirror

object Boxes {

  sealed trait Box[A <: Data] {

    /** Unwrap the box to return the underlying type. */
    def underlying: A

  }

  /** Something in the design that is a pure a type.  This is not a hardware
    * component.  This is approximately a FIRRTL type like "UInt<1>" or "{ a:
    * UInt<2>, flip b: SInt<3> }". */
  class ChiselType[A <: Data](val underlying: A) extends Box[A] {
    require(DataMirror.internal.isSynthesizable(underlying))
  }

  /** A reference type.  This is a type which has dataflow properties, but does
    * not result in real hardware.
    */
  class Ref[A <: Data](underlying: A) extends ChiselType[A](underlying)

  /** A constant type.  This is a type which is guaranteed to take a single constant value. */
  class Const[A <: Data](underlying:  A) extends ChiselType[A](underlying)


  /** Something in the design that is actual hardware.  This could be a register
    * or a wire, but not _the type_ of the wire or register.
    */
  trait HardwareType[A <: Data] extends Box[A] {
    require(!DataMirror.internal.isSynthesizable(underlying))
  }

  /** A wire */
  class Wire[A <: Data](val underlying: A) extends HardwareType[A] {
    require(DataMirror.isWire(underlying))
  }

  /** A register */
  class Register[A <: Data](val underlying: A) extends HardwareType[A] {
    require(DataMirror.isReg(underlying))
  }

  /** A port */
  class Port[A <: Data](val underlying: A) extends HardwareType[A] {
    require(DataMirror.isIO(underlying))
  }

}
