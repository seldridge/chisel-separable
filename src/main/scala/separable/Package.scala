// SPDX-License-Identifier: Apache-2.0

package separable

trait Package[A <: InterfaceGenerator] { this: Singleton =>

  final type Key = A#Key

  protected def members: Map[Key, A with Interface]

  final def lookup(key: Key): A with Interface = members(key)

}
