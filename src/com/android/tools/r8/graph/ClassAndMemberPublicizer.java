// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.function.Consumer;

public abstract class ClassAndMemberPublicizer {
  /**
   * Marks all package private and protected methods and fields as public.
   * <p>
   * This will destructively update the DexApplication passed in as argument.
   */
  public static DexApplication run(DexApplication application) {
    for (DexClass clazz : application.classes()) {
      clazz.accessFlags.promoteToPublic();
      clazz.forEachMethod(new Consumer<DexEncodedMethod>() {
        @Override
        public void accept(DexEncodedMethod method) {
          method.accessFlags.promoteNonPrivateToPublic();
        }
      });
      clazz.forEachField(new Consumer<DexEncodedField>() {
        @Override
        public void accept(DexEncodedField field) {
          field.accessFlags.promoteToPublic();
        }
      });
    }
    return application;
  }
}
