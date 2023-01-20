// SPDX-License-Identifier: Apache-2.0

package sketches

/** An interface that exposes ports and references using abstract methods. */
trait InterfaceFunction {

  import future.Boxes._

  def ports: Seq[Port[_]]

  def references: Seq[Port[_]]

}
