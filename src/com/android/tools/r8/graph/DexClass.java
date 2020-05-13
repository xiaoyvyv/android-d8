// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.Resource;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class DexClass extends DexItem {

  private static final DexEncodedMethod[] NO_METHODS = {};
  private static final DexEncodedField[] NO_FIELDS = {};

  public final Resource.Kind origin;
  public final DexType type;
  public final DexAccessFlags accessFlags;
  public DexType superType;
  public DexTypeList interfaces;
  public final DexString sourceFile;
  protected DexEncodedField[] staticFields;
  protected DexEncodedField[] instanceFields;
  protected DexEncodedMethod[] directMethods;
  protected DexEncodedMethod[] virtualMethods;
  public DexAnnotationSet annotations;

  public DexClass(
      DexString sourceFile, DexTypeList interfaces, DexAccessFlags accessFlags, DexType superType,
      DexType type, DexEncodedField[] staticFields, DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods,
      DexAnnotationSet annotations, Resource.Kind origin) {
    this.origin = origin;
    this.sourceFile = sourceFile;
    this.interfaces = interfaces;
    this.accessFlags = accessFlags;
    this.superType = superType;
    this.type = type;
    setStaticFields(staticFields);
    setInstanceFields(instanceFields);
    setDirectMethods(directMethods);
    setVirtualMethods(virtualMethods);
    this.annotations = annotations;
    if (type == superType) {
      throw new CompilationError("Class " + type.toString() + " cannot extend itself");
    }
    for (DexType interfaceType : interfaces.values) {
      if (type == interfaceType) {
        throw new CompilationError("Interface " + type.toString() + " cannot implement itself");
      }
    }
    if (!type.descriptor.isValidClassDescriptor()) {
      throw new CompilationError(
          "Class descriptor '"
              + type.descriptor.toString()
              + "' cannot be represented in dex format.");
    }
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    throw new Unreachable();
  }

  public DexEncodedMethod[] directMethods() {
    return directMethods;
  }

  public void setDirectMethods(DexEncodedMethod[] values) {
    directMethods = MoreObjects.firstNonNull(values, NO_METHODS);
  }

  public DexEncodedMethod[] virtualMethods() {
    return virtualMethods;
  }

  public void setVirtualMethods(DexEncodedMethod[] values) {
    virtualMethods = MoreObjects.firstNonNull(values, NO_METHODS);
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer) {
    for (DexEncodedMethod method : directMethods()) {
      consumer.accept(method);
    }
    for (DexEncodedMethod method : virtualMethods()) {
      consumer.accept(method);
    }
  }

  public DexEncodedMethod[] allMethodsSorted() {
    int vLen = virtualMethods().length;
    int dLen = directMethods().length;
    DexEncodedMethod[] result = new DexEncodedMethod[vLen+dLen];
    System.arraycopy(virtualMethods(), 0, result, 0, vLen);
    System.arraycopy(directMethods(), 0, result, vLen, dLen);
    Arrays.sort(result,
            new Comparator<DexEncodedMethod>() {
              @Override
              public int compare(DexEncodedMethod a, DexEncodedMethod b) {
                return a.method.slowCompareTo(b.method);
              }
            });
    return result;
  }

  public void forEachField(Consumer<DexEncodedField> consumer) {
    for (DexEncodedField field : staticFields()) {
      consumer.accept(field);
    }
    for (DexEncodedField field : instanceFields()) {
      consumer.accept(field);
    }
  }

  public DexEncodedField[] staticFields() {
    return staticFields;
  }

  public void setStaticFields(DexEncodedField[] values) {
    staticFields = MoreObjects.firstNonNull(values, NO_FIELDS);
  }

  public boolean definesStaticField(DexField field) {
    for (DexEncodedField encodedField : staticFields()) {
      if (encodedField.field == field) {
        return true;
      }
    }
    return false;
  }

  public DexEncodedField[] instanceFields() {
    return instanceFields;
  }

  public void setInstanceFields(DexEncodedField[] values) {
    instanceFields = MoreObjects.firstNonNull(values, NO_FIELDS);
  }

  /**
   * Find direct method in this class matching method
   */
  public DexEncodedMethod findDirectTarget(DexMethod method) {
    return findTarget(directMethods(), method);
  }

  /**
   * Find static field in this class matching field
   */
  public DexEncodedField findStaticTarget(DexField field) {
    return findTarget(staticFields(), field);
  }

  /**
   * Find virtual method in this class matching method
   */
  public DexEncodedMethod findVirtualTarget(DexMethod method) {
    return findTarget(virtualMethods(), method);
  }

  /**
   * Find instance field in this class matching field
   */
  public DexEncodedField findInstanceTarget(DexField field) {
    return findTarget(instanceFields(), field);
  }

  private <T extends DexItem, S extends Descriptor<T, S>> T findTarget(T[] items, S descriptor) {
    for (T entry : items) {
      if (descriptor.match(entry)) {
        return entry;
      }
    }
    return null;
  }

  // Tells whether this is an interface.
  public boolean isInterface() {
    return accessFlags.isInterface();
  }

  public abstract void addDependencies(MixedSectionCollection collector);

  public boolean isProgramClass() {
    return false;
  }

  public DexProgramClass asProgramClass() {
    return null;
  }

  public boolean isClasspathClass() {
    return false;
  }

  public boolean isLibraryClass() {
    return false;
  }

  public DexLibraryClass asLibraryClass() {
    return null;
  }

  public DexEncodedMethod getClassInitializer() {
    return Arrays.stream(directMethods()).filter(new Predicate<DexEncodedMethod>() {
      @Override
      public boolean test(DexEncodedMethod dexEncodedMethod) {
        return dexEncodedMethod.isClassInitializer();
      }
    }).findAny()
        .orElse(null);
  }

  public Resource.Kind getOrigin() {
    return this.origin;
  }

  public boolean hasClassInitializer() {
    return getClassInitializer() != null;
  }

  public boolean hasTrivialClassInitializer() {
    DexEncodedMethod clinit = getClassInitializer();
    return clinit != null
        && clinit.getCode() != null
        && clinit.getCode().asDexCode().isEmptyVoidMethod();
  }


  public boolean hasNonTrivialClassInitializer() {
    DexEncodedMethod clinit = getClassInitializer();
    if (clinit == null || clinit.getCode() == null) {
      return false;
    }
    if (clinit.getCode().isDexCode()) {
      return !clinit.getCode().asDexCode().isEmptyVoidMethod();
    }
    // For non-dex code we don't try to check the code.
    return true;
  }

  public boolean defaultValuesForStaticFieldsMayTriggerAllocation() {
    return Arrays.stream(staticFields())
        .anyMatch(new Predicate<DexEncodedField>() {
          @Override
          public boolean test(DexEncodedField field) {
            return !field.staticValue.mayTriggerAllocation();
          }
        });
  }
}
