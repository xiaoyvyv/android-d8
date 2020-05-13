// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.MoveType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import java.util.Collections;
import java.util.function.Consumer;

// Source code representing synthesized lambda class constructor.
// Used for stateless lambdas to instantiate singleton instance.
final class LambdaClassConstructorSourceCode extends SynthesizedLambdaSourceCode {

  LambdaClassConstructorSourceCode(LambdaClass lambda) {
    super(null /* Class initializer is static */, lambda, lambda.classConstructor);
    assert lambda.instanceField != null;
  }

  @Override
  protected void prepareInstructions() {
    // Create and initialize an instance.
    int instance = nextRegister(MoveType.OBJECT);
    add(new Consumer<IRBuilder>() {
      @Override
      public void accept(IRBuilder builder) {
        builder.addNewInstance(instance, lambda.type);
      }
    });
    add(new Consumer<IRBuilder>() {
      @Override
      public void accept(IRBuilder builder) {
        builder.addInvoke(
                Invoke.Type.DIRECT, lambda.constructor, lambda.constructor.proto,
                Collections.singletonList(MoveType.OBJECT), Collections.singletonList(instance));
      }
    });

    // Assign to a field.
    add(new Consumer<IRBuilder>() {
      @Override
      public void accept(IRBuilder builder) {
        builder.addStaticPut(MemberType.OBJECT, instance, lambda.instanceField);
      }
    });

    // Final return.
    add(new Consumer<IRBuilder>() {
      @Override
      public void accept(IRBuilder irBuilder) {
        irBuilder.addReturn();
      }
    });
  }
}
