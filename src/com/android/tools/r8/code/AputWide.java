// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class AputWide extends Format23x {

  public static final int OPCODE = 0x4c;
  public static final String NAME = "AputWide";
  public static final String SMALI_NAME = "aput-wide";

  /*package*/ AputWide(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public AputWide(int AA, int BB, int CC) {
    super(AA, BB, CC);
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
    builder.addArrayPut(MemberType.WIDE, AA, BB, CC);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
