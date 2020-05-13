package com.android.tools.r8.graph;

import com.android.tools.r8.Resource;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Kind of the application class. Can be program, classpath or library. */
public enum ClassKind {
  PROGRAM(new Factory() {
    @Override
    public DexClass create(DexType type, Resource.Kind origin, DexAccessFlags accessFlags, DexType superType, DexTypeList interfaces, DexString sourceFile, DexAnnotationSet classAnnotations, DexEncodedField[] staticFields, DexEncodedField[] instanceFields, DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods) {
      return new DexProgramClass(type, origin, accessFlags, superType, interfaces, sourceFile, classAnnotations, staticFields, instanceFields, directMethods, virtualMethods);
    }
  }, new Predicate<DexClass>() {
    @Override
    public boolean test(DexClass dexClass) {
      return dexClass.isProgramClass();
    }
  }),
  CLASSPATH(new Factory() {
    @Override
    public DexClass create(DexType type, Resource.Kind origin, DexAccessFlags accessFlags, DexType superType, DexTypeList interfaces, DexString sourceFile, DexAnnotationSet annotations, DexEncodedField[] staticFields, DexEncodedField[] instanceFields, DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods) {
      return new DexClasspathClass(type, origin, accessFlags, superType, interfaces, sourceFile, annotations, staticFields, instanceFields, directMethods, virtualMethods);
    }
  }, new Predicate<DexClass>() {
    @Override
    public boolean test(DexClass dexClass) {
      return dexClass.isClasspathClass();
    }
  }),
  LIBRARY(new Factory() {
    @Override
    public DexClass create(DexType type, Resource.Kind origin, DexAccessFlags accessFlags, DexType superType, DexTypeList interfaces, DexString sourceFile, DexAnnotationSet annotations, DexEncodedField[] staticFields, DexEncodedField[] instanceFields, DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods) {
      return new DexLibraryClass(type, origin, accessFlags, superType, interfaces, sourceFile, annotations, staticFields, instanceFields, directMethods, virtualMethods);
    }
  }, new Predicate<DexClass>() {
    @Override
    public boolean test(DexClass dexClass) {
      return dexClass.isLibraryClass();
    }
  });

  private interface Factory {
    DexClass create(DexType type, Resource.Kind origin, DexAccessFlags accessFlags,
        DexType superType,
        DexTypeList interfaces, DexString sourceFile, DexAnnotationSet annotations,
        DexEncodedField[] staticFields, DexEncodedField[] instanceFields,
        DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods);
  }

  private final Factory factory;
  private final Predicate<DexClass> check;

  ClassKind(Factory factory, Predicate<DexClass> check) {
    this.factory = factory;
    this.check = check;
  }

  public DexClass create(
      DexType type, Resource.Kind origin, DexAccessFlags accessFlags, DexType superType,
      DexTypeList interfaces, DexString sourceFile, DexAnnotationSet annotations,
      DexEncodedField[] staticFields, DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods, DexEncodedMethod[] virtualMethods) {
    return factory.create(type, origin, accessFlags, superType, interfaces, sourceFile,
        annotations, staticFields, instanceFields, directMethods, virtualMethods);
  }

  public boolean isOfKind(DexClass clazz) {
    return check.test(clazz);
  }

  public <T extends DexClass> Consumer<DexClass> bridgeConsumer(Consumer<T> consumer) {
    return new Consumer<DexClass>() {
      @Override
      public void accept(DexClass clazz) {
        assert ClassKind.this.isOfKind(clazz);
        @SuppressWarnings("unchecked") T specialized = (T) clazz;
        consumer.accept(specialized);
      }
    };
  }
}
