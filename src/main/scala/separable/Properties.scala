// SPDX-License-Identifier: Apache-2.0

package separable

import chisel3.Data

/** Properties related to clocks */
object Clock {
  sealed trait Type {
    def name: String

    def frequencies: Seq[BigInt]

    private val associations =
      scala.collection.mutable.ArrayBuffer.empty[Data]
    def associate(data: Data): Unit = associations.append(data)

    private val equivalences =
      scala.collection.mutable.HashSet.empty[chisel3.Clock]
    def equate(clock: chisel3.Clock): Unit = equivalences.add(clock)
  }

  case class Sink(name: String, frequencies: Seq[BigInt]) extends Type

  case class Source(name: String, frequencies: Seq[BigInt]) extends Type

}
