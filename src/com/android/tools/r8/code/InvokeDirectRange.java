// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class InvokeDirectRange extends Format3rc {

  public static final int OPCODE = 0x76;
  public static final String NAME = "InvokeDirectRange";
  public static final String SMALI_NAME = "invoke-direct/range";

  InvokeDirectRange(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getMethodMap());
  }

  public InvokeDirectRange(int firstArgumentRegister, int argumentCount, DexMethod method) {
    super(firstArgumentRegister, argumentCount, method);
  }

  public String getName() {
    return NAME;
  }

  public String getSmaliName() {
    return SMALI_NAME;
  }

  public int getOpcode() {
    return OPCODE;
  }

  public DexMethod getMethod() {
    return (DexMethod) BBBB;
  }

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerInvokeDirect(getMethod());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInvokeRange(Type.DIRECT, getMethod(), getProto(), AA, CCCC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
