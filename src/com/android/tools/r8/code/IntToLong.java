// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class IntToLong extends Format12x {

  public static final int OPCODE = 0x81;
  public static final String NAME = "IntToLong";
  public static final String SMALI_NAME = "int-to-long";

  IntToLong(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public IntToLong(int dest, int source) {
    super(dest, source);
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
  public void buildIR(IRBuilder builder) {
    builder.addConversion(NumericType.LONG, NumericType.INT, A, B);
  }
}
