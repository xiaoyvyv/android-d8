// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.conversion.IRBuilder;
public class DivDouble extends Format23x {

  public static final int OPCODE = 0xae;
  public static final String NAME = "DivDouble";
  public static final String SMALI_NAME = "div-double";

  DivDouble(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DivDouble(int dest, int left, int right) {
    super(dest, left, right);
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
    builder.addDiv(NumericType.DOUBLE, AA, BB, CC);
  }
}
