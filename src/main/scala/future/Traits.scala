// SPDX-License-Identifier: Apache-2.0

package future

import chisel3.Data
import chisel3.reflect.DataMirror

object Traits {

  /** A mix-in to Data that indicates that something is a reference. */
  trait Ref { this: Data => }

  /** A mix-in to Data that indicates that a port is a reference. */
  trait Const { this: Data =>
    require(!DataMirror.isReg(this))
  }

}
