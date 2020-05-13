// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class IgetBoolean extends Format22c {

  public static final int OPCODE = 0x55;
  public static final String NAME = "IgetBoolean";
  public static final String SMALI_NAME = "iget-boolean";

  /*package*/ IgetBoolean(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getFieldMap());
  }

  public IgetBoolean(int destRegister, int objectRegister, DexField field) {
    super(destRegister, objectRegister, field);
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
    registry.registerInstanceFieldRead(getField());
  }

  @Override
  public DexField getField() {
    return (DexField) CCCC;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInstanceGet(MemberType.BOOLEAN, A, B, getField());
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
