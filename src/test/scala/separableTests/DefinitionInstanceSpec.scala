// SPDX-License-Identifier: Apache-2.0

package separableTests

import chisel3._
import chisel3.experimental.hierarchy.{
  instantiable,
  public,
  Definition,
  Instance,
  IsInstantiable
}
import chisel3.stage.{ChiselGeneratorAnnotation, DesignAnnotation}
import chisel3.experimental.hierarchy.core.ImportDefinitionAnnotation
import circt.stage.ChiselStage
import firrtl.AnnotationSeq
import separable.Drivers
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

/** This was designed with help from Adam to show and compare existing
  * Definition/Instance (D/I) infrastructure to Interface/ConformsTo. The main
  * difference is that, in its current state, D/I requires information from the
  * elaboration of the component (a `Definition[_]` to be available to the
  * elaboration of the client (to build an `Instance[_]`).
  */
class DefinitionInstanceSpec extends AnyFunSpec with Matchers {

  /** This is the "Interface". This defines the port-level interface (all the
    * "@public" members of type `Data`) as well as properties (all the "@public"
    * members not of type `Data`). This interface may have any number of
    * non-public members or ports in the D/I sense (anything not "@public") as
    * well as members that have different levels of Scala access modifiers. All
    * "@public" members must be in a class annotated as "@instantiable".
    * Additionally, all "@public" members must be of a type which has a
    * "Lookupable" object of their type in scope. "Lookupable" is defined for
    * most types, but not all. E.g., a "Lookupable" cannot currently be an
    * arbitrary "case class" which is why I use a "properties_id: Int" instead
    * of a "case class BarProperties(id: Int)".
    */
  @instantiable
  sealed trait BarInterface extends RawModule {
    @public val a = IO(Input(Bool()))
    @public val b = IO(Output(Bool()))
    @public val properties_id: Int
  }

  /** This is the compilation unit used to build the "DUT" (component). The
    * "DUT", like other examples, is called "Bar". This will be instantiated
    * multiple times by the client, "Foo".
    */
  object CompilationUnit1 {

    class Bar extends BarInterface {
      override val properties_id = 42

      b := ~a
    }

  }

  /** This is the compilation unit used to build the client which instantiates
    * the "DUT" (Bar). This instantiates an Instance of the "DUT" which requires
    * having a Definition of the "DUT" in scope.
    */
  object CompilationUnit2 {

    class Foo(definition: Definition[BarInterface]) extends Module {
      val a = IO(Input(Bool()))
      val b = IO(Output(Bool()))

      val bar1, bar2 = Instance(definition)

      bar1.a := a
      bar2.a := bar1.b
      b := bar2.b
    }

  }

  /** Utility to reconstruct a Definition from */
  def getDefinition[T <: RawModule](annos: AnnotationSeq): Definition[T] = {
    val designAnnos = annos.flatMap { a =>
      a match {
        case a: DesignAnnotation[T @unchecked] => Some(a)
        case _ => None
      }
    }
    require(
      designAnnos.length == 1,
      s"Exactly one DesignAnnotation should exist, but found: $designAnnos."
    )
    designAnnos.head.design.asInstanceOf[T].toDefinition
  }

  private val dir = new java.io.File("build/DefinitionInstance")

  describe("Behavior of separable compilation using Definition/Instance") {

    it("should compile a design separately") {

      /** This elaborates and packages the "DUT" into a Definition. This is
        * similar to the BarInterface of the Interface/ConformsTo examples, but
        * it requires a Chisel elaboration. This enables reusing the results of
        * one elaboartion in another elaboration. However, it requires
        * elaboration of the "DUT" to happen before the "DUT can be
        * instantiated.
        */
      val barInterface: Definition[BarInterface] = {

        val dutAnnos = (new ChiselStage).execute(
          Array("--target", "chirrtl", "--target-dir", s"$dir/definition"),
          Seq(
            ChiselGeneratorAnnotation(() => new CompilationUnit1.Bar)
          )
        )

        getDefinition[BarInterface](dutAnnos)
      }

      info("compile okay!")
      Drivers.compile(
        dir,
        Drivers.CompilationUnit(
          () => new CompilationUnit2.Foo(barInterface),
          annotations = Seq(ImportDefinitionAnnotation(barInterface))
        ),
        Drivers.CompilationUnit(() => new CompilationUnit1.Bar)
      )

      info("link okay!")
      Drivers.link(dir, "compile-0/Foo.sv")

    }

  }

}
