// SPDX-License-Identifier: Apache-2.0

package sketches

import chisel3.{
  dontTouch,
  fromBooleanToLiteral,
  Bool,
  Input,
  Module,
  Output,
  RawModule,
  WireDefault
}

import future.Ref

/** This is a module that has no "real" IO. It only has a single reference type
  * port. This is used to enable a parent to read the value of an internal wire.
  */
class Submodule extends RawModule {

  /** This is a "reftype" output port. */
  val ref_a = IO(Ref(Output(Bool())))

  /** This is a register that we want to expose a reference to. */
  private val a = WireDefault(true.B)
  dontTouch(a)

  ref_a := Ref.send(a)

}

/** This is the top module. This constructs an XMR that reads the value of the
  * wire in the submodule.
  */
class Top extends RawModule {
  val a = IO(Output(Bool()))

  val sub = Module(new Submodule)

  a := Ref.resolve(sub.ref_a)
}

// Top _should_ compile to the following FIRRTL.  This uses features that do not
// exist!
//
//     circuit Top: %[[
//       {
//         "class":"firrtl.transforms.DontTouch",
//         "~Top|Submodule>a"
//       }
//     ]]
//       module Submodule:
//         output ref_a: Ref<UInt<1>>
//
//         wire a: UInt<1>
//         ref_a <= refSend(a)
//
//       module Top:
//         output a: UInt<1>
//
//         inst sub of Submodule
//         a <= refResolve(sub.ref_a)
//
//
// This is equivalent to the following FIRRTL Dialect (MLIR).  This works today.
//
//    firrtl.circuit "Top"  {
//      firrtl.module private @Submodule(out %ref_a: !firrtl.ref<uint<1>>) {
//        %true = firrtl.constant true : !firrtl.uint<1>
/* %a = firrtl.wire {annotations = [{class =
 * "firrtl.transforms.DontTouchAnnotation"}]} : !firrtl.uint<1> */
//        firrtl.strictconnect %a, %true : !firrtl.uint<1>
//        %_a = firrtl.ref.send %a : !firrtl.uint<1>
//        firrtl.strictconnect %ref_a, %_a : !firrtl.ref<uint<1>>
//      }
//      firrtl.module @Top(out %a: !firrtl.uint<1>) {
/* %sub_ref_a = firrtl.instance sub interesting_name @Submodule(out ref_a:
 * !firrtl.ref<uint<1>>) */
//        %_a = firrtl.ref.resolve %sub_ref_a : !firrtl.ref<uint<1>>
//        firrtl.strictconnect %a, %_a : !firrtl.uint<1>
//      }
//    }
//
// This produces the following Verilog.  This works today:
//
//    module Submodule();
//      wire a = 1'h1;
//    endmodule
//
//    module Top(
//      output a);
//
//      Submodule sub ();
//      assign a = Top.sub.a;
//    endmodule
