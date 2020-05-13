// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.*;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import java.util.Set;
import java.util.function.Consumer;

public class DiscardedChecker {

  private final Set<DexItem> checkDiscarded;
  private final DexApplication application;
  private boolean fail = false;

  public DiscardedChecker(RootSet rootSet, DexApplication application) {
    this.checkDiscarded = rootSet.checkDiscarded;
    this.application = application;
  }

  public void run() {
    for (DexProgramClass clazz : application.classes()) {
      checkItem(clazz);
      clazz.forEachMethod(new Consumer<DexEncodedMethod>() {
        @Override
        public void accept(DexEncodedMethod item) {
          DiscardedChecker.this.checkItem(item);
        }
      });
      clazz.forEachField(new Consumer<DexEncodedField>() {
        @Override
        public void accept(DexEncodedField item) {
          DiscardedChecker.this.checkItem(item);
        }
      });
    }
    if (fail) {
      throw new CompilationError("Discard checks failed.");
    }
  }

  private void checkItem(DexItem item) {
    if (checkDiscarded.contains(item)) {
      System.err.println("Item " + item.toSourceString() + " was not discarded.");
      fail = true;
    }
  }
}
