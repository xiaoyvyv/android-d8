// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.ClassNameMapper;

public class PackedSwitch extends Format31t {

  public static final int OPCODE = 0x2b;
  public static final String NAME = "PackedSwitch";
  public static final String SMALI_NAME = "packed-switch";

  PackedSwitch(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public PackedSwitch(int valueRegister) {
    super(valueRegister, -1);
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
  public boolean isSwitch() {
    return true;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    int offset = getOffset();
    int payloadOffset = offset + getPayloadOffset();
    int fallthroughOffset = offset + getSize();
    builder.resolveAndBuildSwitch(AA, fallthroughOffset, payloadOffset);
  }

  public String toSmaliString(ClassNameMapper naming) {
    return formatSmaliString("v" + AA + ", :label_" + (getOffset() + BBBBBBBB));
  }
}
