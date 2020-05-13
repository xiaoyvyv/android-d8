// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import static com.android.tools.r8.ir.code.MemberType.BOOLEAN;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class SputBoolean extends Format21c {

  public static final int OPCODE = 0x6a;
  public static final String NAME = "SputBoolean";
  public static final String SMALI_NAME = "sput-boolean";

  /*package*/ SputBoolean(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getFieldMap());
  }

  public SputBoolean(int AA, DexField BBBB) {
    super(AA, BBBB);
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

  @Override
  public void registerUse(UseRegistry registry) {
    registry.registerStaticFieldWrite(getField());
  }

  @Override
  public DexField getField() {
    return (DexField) BBBB;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addStaticPut(BOOLEAN, AA, getField());
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
