// SPDX-License-Identifier: Apache-2.0

import chisel3.Data

/** This package contains prototypes of future Chisel APIs.
  */
package object future {

  /** This object defines utilities for creating and working with reference
    * types. This closely follows existing Chisel design patterns where methods
    * mutate state and return the underlying data type. The fact that something
    * is a reference is recorded in the Builder.
    *
    * Note: These methods do not do anything. This is intentional. They are used
    * to show what Chisel code could look like without dealing with
    * implementation.
    */
  object Ref {

    /** Create a reference type from a Chisel type. This is not used for
      * hardware.
      */
    def apply[A <: Data](a: A): A = a

    /** Resolve a reference type to a real type. An XMR will be created at the
      * location where this method is called.
      */
    def resolve[A <: Data](a: A): A = a

    /** Convert a Chisel hardware type to a reference of that type. This is used
      * to create, e.g., a reference type with the value of a register or wire.
      */
    def send[A <: Data](a: A): A = a

    /** A connection between two ref types. This is intentionally different than
      * a normal connect to make it obvious that this is something else. An
      * alternative design point would be to re-use the ":=" connect operator
      * (at the cost of user confusion about the types they are connecting).
      */
    def forward[A <: Data](a: A, b: A): Unit = {}
  }

}
