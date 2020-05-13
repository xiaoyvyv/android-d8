// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class MulIntLit16 extends Format22s {

  public static final int OPCODE = 0xd2;
  public static final String NAME = "MulIntLit16";
  public static final String SMALI_NAME = "mul-int/lit16";

  MulIntLit16(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public MulIntLit16(int dest, int register, int constant) {
    super(dest, register, constant);
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
    builder.addMulLiteral(NumericType.INT, A, B, CCCC);
  }
}
