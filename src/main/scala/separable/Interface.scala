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

/** A version string */
final case class Version(major: Int, minor: Int, patch: Int)

/** A vendor-library-name-version string used to identify a module */
final case class VLNV(
  vendor:  String,
  library: String,
  name:    String,
  version: Version)

@implicitNotFound(
  "this method requires information from the separable compilation implementation, please bring one into scope as an `implicit val`. You can also consult the team that owns the implementation to refer to which one you should use!"
)
trait ConformsTo[A <: Record, B <: RawModule] {

  /** Return the module that conforms to a port-level interface. */
  def genModule: B

  /** Define how this module hooks up to the port-level interface. */
  def connect(lhs: A, rhs: B): Unit

  /** Some information that every module has to implement */
  def vlnv: VLNV

}

/** An interface between hardware units. Any module that implements this
  * interface may be separately compiled from any module that instantiates this
  * interface.
  */
trait Interface[A <: Record] {

  /** The name of this interface. This will be used as the name of any module
    * that implements this interface. I.e., this is the name of the `BlackBox`
    * and `Module` that are provided below.
    */
  def interfaceName: String

  /** Returns the Record that is the port-level interface. */
  def ports: A

  /** Return the vendor-library-name-version about the component that
    * _implements_ this interface. This is an example of extracting information
    * from a specific implementation which is different for different
    * implementations. This requires static knowledge about the component which
    * is passed via an implicit parameter. I.e., there must be a type class
    * implementation of ConformsTo for this Interface in scope for this method
    * to be used. If no such method is in scope, the Scala compiler will
    * statically reject any generator that tries to use this method.
    */
  final def vlnv[B <: RawModule](
  )(
    implicit conformance: ConformsTo[A, B]
  ): VLNV = conformance.vlnv

  /** The black box that has the same ports as this interface. This is what is
    * instantiated by any user of this interface, i.e., a test harness.
    */
  final class BlackBox extends chisel3.BlackBox {
    val io = IO(ports)

    override final def desiredName = interfaceName
  }

  /** The module that wraps any module which conforms to this Interface. Like
    * the `vlnv` method, this requires having an implementation of the interface
    * statically known to the compiler.
    */
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
