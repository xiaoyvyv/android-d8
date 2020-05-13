// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.code;

import com.android.tools.r8.ir.code.MoveType;
import com.android.tools.r8.ir.conversion.IRBuilder;
public class MoveObjectFrom16 extends Format22x {

  public static final int OPCODE = 0x8;
  public static final String NAME = "MoveObjectFrom16";
  public static final String SMALI_NAME = "move-object-from/16";

  MoveObjectFrom16(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public MoveObjectFrom16(int dest, int src) {
    super(dest, src);
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
    builder.addMove(MoveType.OBJECT, AA, BBBB);
  }
}
