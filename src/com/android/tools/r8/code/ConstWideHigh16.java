// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.MoveType;
import com.android.tools.r8.ir.code.WideConstant;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.StringUtils;

public class ConstWideHigh16 extends Format21h implements WideConstant {

  public static final int OPCODE = 0x19;
  public static final String NAME = "ConstWideHigh16";
  public static final String SMALI_NAME = "const-wide/high16";

  ConstWideHigh16(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public ConstWideHigh16(int dest, int constantHighBits) {
    super(dest, constantHighBits);
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

  public long decodedValue() {
    return ((long) BBBB) << 48;
  }

  public String toString(ClassNameMapper naming) {
    return formatString("v" + AA + ", " + StringUtils.hexString(decodedValue(), 16) +
        " (" + decodedValue() + ")");
  }

  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", " + StringUtils.hexString(decodedValue(), 16) +
        "L  # " + decodedValue());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConst(MoveType.WIDE, AA, decodedValue());
  }
}
