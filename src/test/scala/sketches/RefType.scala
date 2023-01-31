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
class Bar extends RawModule {

  /** This is a "reftype" output port. */
  val ref_a = IO(Ref(Output(Bool())))

  /** This is a register that we want to expose a reference to. */
  private val a = WireDefault(true.B)

  /** This DontTouchAnnotation is unnecessary. It is here to block constant
    * propagation through "a" to ensure that a Verilog cross-module reference
    * appears in the output Verilog (as opposed to a constant).
    */
  dontTouch(a)

  ref_a := Ref.send(a)

}

class Foo extends RawModule {

  val ref_b = IO(Ref(Output(Bool())))

  private val bar = Module(new Bar)

  Ref.forward(bar.ref_a, ref_b)
}

/** This is the top module. This constructs an XMR that reads the value of the
  * wire in the submodule.
  */
class Top extends RawModule {
  val a = IO(Output(Bool()))

  val foo = Module(new Foo)

  a := Ref.resolve(foo.ref_b)
}

// format: off
//
// Top _should_ compile to the following FIRRTL.  This uses features that do not
// exist!
//
//     circuit Top: %[[
//       {
//         "class":"firrtl.transforms.DontTouch",
//         "~Top|Submodule>a"
//       }
//     ]]
//       module Bar:
//         output ref_a: Ref<UInt<1>>
//
//         wire a: UInt<1>
//         export a as ref_a
//
//       module Foo
//         output ref_b: Ref<UInt<1>>
//
//         inst bar of Bar
//         forward bar.ref_a as ref_b
//
//       module Top:
//         output a: UInt<1>
//
//         inst foo of Foo
//         a <= refResolve(foo.ref_b)
//
// This is equivalent to the following FIRRTL Dialect (MLIR).  This usese
// existing FIRRTL Dialect.  This may be changed to align more closely with the
// above FIRRTL text.
//
//     firrtl.circuit "Top"  {
//       firrtl.module private @Bar(out %ref_a: !firrtl.ref<uint<1>>) {
//         %true = firrtl.constant true : !firrtl.uint<1>
//         %a = firrtl.wire {annotations = [{class = "firrtl.transforms.DontTouchAnnotation"}]} : !firrtl.uint<1>
//         firrtl.strictconnect %a, %true : !firrtl.uint<1>
//         %_a = firrtl.ref.send %a : !firrtl.uint<1>
//         firrtl.strictconnect %ref_a, %_a : !firrtl.ref<uint<1>>
//       }
//       firrtl.module @Foo(out %ref_b: !firrtl.ref<uint<1>>) {
//         %bar_ref_a = firrtl.instance bar interesting_name @Bar(out ref_a: !firrtl.ref<uint<1>>)
//         firrtl.strictconnect %ref_b, %bar_ref_a : !firrtl.ref<uint<1>>
//       }
//       firrtl.module @Top(out %a: !firrtl.uint<1>) {
//         %foo_ref_b = firrtl.instance foo interesting_name @Foo(out ref_b: !firrtl.ref<uint<1>>)
//         %_a = firrtl.ref.resolve %foo_ref_b : !firrtl.ref<uint<1>>
//         firrtl.strictconnect %a, %_a : !firrtl.uint<1>
//       }
//     }
//
// This produces the following Verilog.  This works today:
//
//     module Bar();
//       wire a = 1'h1;
//     endmodule
//
//     module Foo();
//       Bar bar ();
//     endmodule
//
//     module Top(
//       output a);
//
//       Foo foo ();
//       assign a = Top.foo.bar.a;
//     endmodule
