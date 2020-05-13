// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A pass to rename methods using common, short names.
 * <p>
 * To assign names, we model the scopes of methods names and overloading/shadowing based on the
 * subtyping tree of classes. Such a naming scope is encoded by {@link NamingState}. It keeps
 * track of its parent node, names that have been reserved (due to keep annotations or otherwise)
 * and what names have been used for renaming so far.
 * <p>
 * As in the Dalvik VM method dispatch takes argument and return types of methods into account, we
 * can further reuse names if the prototypes of two methods differ. For this, we store the above
 * state separately for each proto using a map from protos to {@link NamingState.InternalState}
 * objects. These internal state objects are also linked.
 * <p>
 * Name assignment happens in 4 stages. In the first stage, we record all names that are used by
 * library classes or are flagged using a keep rule as reserved. This step also allocates the
 * {@link NamingState} objects for library classes. We can fully allocate these objects as we
 * never perform naming for library classes. For non-library classes, we only allocate a state
 * for the highest non-library class, i.e., we allocate states for every direct subtype of a library
 * class. The states at the boundary between library and program classes are referred to as the
 * frontier states in the code.
 * <p>
 * When reserving names in program classes, we reserve them in the state of the corresponding
 * frontier class. This is to ensure that the names are not used for renaming in any supertype.
 * Thus, they will still be available in the subtype where they are reserved. Note that name
 * reservation only blocks names from being used for minification. We assume that the input program
 * is correctly named.
 * <p>
 * In stage 2, we reserve names that stem from interfaces. These are not propagated to
 * subinterfaces or implementing classes. Instead, stage 3 makes sure to query related states when
 * making naming decisions.
 * <p>
 * In stage 3, we compute minified names for all interface methods. We do this first to reduce
 * assignment conflicts. Interfaces do not build a tree-like inheritance structure we can exploit.
 * Thus, we have to infer the structure on the fly. For this, we compute a sets of reachable
 * interfaces. i.e., interfaces that are related via subtyping. Based on these sets, we then
 * find, for each method signature, the classes and interfaces this method signature is defined in.
 * For classes, as we still use frontier states at this point, we do not have to consider subtype
 * relations. For interfaces, we reserve the name in all reachable interfaces and thus ensure
 * availability.
 * <p>
 * Name assignment in this phase is a search over all impacted naming states. Using the naming state
 * of the interface this method first originated from, we propose names until we find a matching
 * one. We use the naming state of the interface to not impact name availability in naming states of
 * classes. Hence, skipping over names during interface naming does not impact their availability in
 * the next phase.
 * <p>
 * In the final stage, we assign names to methods by traversing the subtype tree, now allocating
 * separate naming states for each class starting from the frontier. In the first swoop, we allocate
 * all non-private methods, updating naming states accordingly. In a second swoop, we then allocate
 * private methods, as those may safely use names that are used by a public method further down in
 * the subtyping tree.
 * <p>
 * Finally, the computed renamings are returned as a map from {@link DexMethod} to
 * {@link DexString}. The MethodNameMinifier object should not be retained to ensure all
 * intermediate state is freed.
 * <p>
 * TODO(herhut): Currently, we do not minify members of annotation interfaces, as this would require
 * parsing and minification of the string arguments to annotations.
 */
class MethodNameMinifier {

  private final AppInfoWithSubtyping appInfo;
  private final RootSet rootSet;
  private final Map<DexType, NamingState<DexProto>> states = new IdentityHashMap<>();
  private final NamingState<DexProto> globalState;
  private MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
  private final ImmutableList<String> dictionary;

  MethodNameMinifier(AppInfoWithSubtyping appInfo, RootSet rootSet, InternalOptions options) {
    this.appInfo = appInfo;
    this.rootSet = rootSet;
    this.dictionary = options.proguardConfiguration.getObfuscationDictionary();
    this.globalState = NamingState.createRoot(appInfo.dexItemFactory, dictionary);
  }

  Map<DexMethod, DexString> computeRenaming(Timing timing) {
    // Phase 1: Reserve all the names that need to be kept and allocate linked state in the
    //          library part.
    timing.begin("Phase 1");
    Map<DexType, DexType> frontierMap = new IdentityHashMap<>();
    reserveNamesInClasses(appInfo.dexItemFactory.objectType,
        appInfo.dexItemFactory.objectType,
        null, frontierMap);
    timing.end();
    // Phase 2: Reserve all the names that are required for interfaces.
    timing.begin("Phase 2");
    DexType.forAllInterfaces(appInfo.dexItemFactory, new Consumer<DexType>() {
      @Override
      public void accept(DexType iface) {
        MethodNameMinifier.this.reserveNamesInInterfaces(iface, frontierMap);
      }
    });
    timing.end();
    // Phase 3: Assign names to interface methods. These are assigned by finding a name that is
    //          free in all naming states that may hold an implementation.
    timing.begin("Phase 3");
    Map<DexMethod, DexString> renaming = new IdentityHashMap<>();
    assignNamesToInterfaceMethods(frontierMap, renaming, timing);
    timing.end();
    // Phase 4: Assign names top-down by traversing the subtype hierarchy.
    timing.begin("Phase 4");
    assignNamesToClassesMethods(appInfo.dexItemFactory.objectType, false, renaming);
    timing.end();
    // Phase 4: Do the same for private methods.
    timing.begin("Phase 5");
    assignNamesToClassesMethods(appInfo.dexItemFactory.objectType, true, renaming);
    timing.end();

    return renaming;
  }

  private void assignNamesToClassesMethods(DexType type, boolean doPrivates,
      Map<DexMethod, DexString> renaming) {
    DexClass holder = appInfo.definitionFor(type);
    if (holder != null && !holder.isLibraryClass()) {
      NamingState<DexProto> state = states
          .computeIfAbsent(type, new Function<DexType, NamingState<DexProto>>() {
            @Override
            public NamingState<DexProto> apply(DexType k) {
              return states.get(holder.superType).createChild();
            }
          });
      holder.forEachMethod(new Consumer<DexEncodedMethod>() {
        @Override
        public void accept(DexEncodedMethod method) {
          MethodNameMinifier.this.assignNameToMethod(method, state, doPrivates, renaming);
        }
      });
    }
    type.forAllExtendsSubtypes(
            new Consumer<DexType>() {
              @Override
              public void accept(DexType subtype) {
                MethodNameMinifier.this.assignNamesToClassesMethods(subtype, doPrivates, renaming);
              }
            });
  }

  private void assignNameToMethod(DexEncodedMethod encodedMethod,
      NamingState<DexProto> state, boolean doPrivates, Map<DexMethod, DexString> renaming) {
    if (encodedMethod.accessFlags.isPrivate() != doPrivates) {
      return;
    }
    DexMethod method = encodedMethod.method;
    if (!state.isReserved(method.name, method.proto)
        && !encodedMethod.accessFlags.isConstructor()) {
      renaming.put(method, state.assignNewNameFor(method.name, method.proto, !doPrivates));
    }
  }

  private Set<NamingState<DexProto>> getReachableStates(DexType type,
      Map<DexType, DexType> frontierMap) {
    Set<DexType> interfaces = Sets.newIdentityHashSet();
    interfaces.add(type);
    collectSuperInterfaces(type, interfaces);
    collectSubInterfaces(type, interfaces);
    Set<NamingState<DexProto>> reachableStates = new HashSet<>();
    for (DexType iface : interfaces) {
      // Add the interface itself
      reachableStates.add(states.get(iface));
      // And the frontiers that correspond to the classes that implement the interface.
      iface.forAllImplementsSubtypes(new Consumer<DexType>() {
        @Override
        public void accept(DexType t) {
          NamingState<DexProto> state = states.get(frontierMap.get(t));
          assert state != null;
          reachableStates.add(state);
        }
      });
    }
    return reachableStates;
  }

  private void assignNamesToInterfaceMethods(final Map<DexType, DexType> frontierMap,
                                             Map<DexMethod, DexString> renaming, Timing timing) {
    // First compute a map from method signatures to a set of naming states for interfaces and
    // frontier states of classes that implement them. We add the frontier states so that we can
    // reserve the names for later method naming.
    timing.begin("Compute map");
    // A map from DexMethods to all the states linked to interfaces they appear in.
    final Map<Wrapper<DexMethod>, Set<NamingState<DexProto>>> globalStateMap = new HashMap<>();
    // A map from DexMethods to all the definitions seen. Needed as the Wrapper equalizes them all.
    final Map<Wrapper<DexMethod>, Set<DexMethod>> sourceMethodsMap = new HashMap<>();
    // A map from DexMethods to the first interface state it was seen in. Used to pick good names.
    final Map<Wrapper<DexMethod>, NamingState<DexProto>> originStates = new HashMap<>();
    DexType.forAllInterfaces(appInfo.dexItemFactory, new Consumer<DexType>() {
      @Override
      public void accept(final DexType iface) {
        assert iface.isInterface();
        DexClass clazz = appInfo.definitionFor(iface);
        if (clazz != null) {
          final Set<NamingState<DexProto>> collectedStates = MethodNameMinifier.this.getReachableStates(iface, frontierMap);
          clazz.forEachMethod(new Consumer<DexEncodedMethod>() {
            @Override
            public void accept(DexEncodedMethod dexEncodedMethod) {
              MethodNameMinifier.this.addStatesToGlobalMapForMethod(
                      dexEncodedMethod, collectedStates, globalStateMap, sourceMethodsMap, originStates, iface);
            }
          });
        }
      }
    });
    timing.end();
    // Go over every method and assign a name.
    timing.begin("Allocate names");
    // Sort the methods by the number of dependent states, so that we use short names for methods
    // references in many places.
    List<Wrapper<DexMethod>> methods = new ArrayList<>(globalStateMap.keySet());
    methods.sort(new Comparator<Wrapper<DexMethod>>() {
      @Override
      public int compare(Wrapper<DexMethod> a, Wrapper<DexMethod> b) {
        return globalStateMap.get(b).size() - globalStateMap.get(a).size();
      }
    });
    for (Wrapper<DexMethod> key : methods) {
      DexMethod method = key.get();
      assignNameForInterfaceMethodInAllStates(method,
          globalStateMap.get(key),
          sourceMethodsMap.get(key),
          renaming,
          originStates.get(key));
    }
    timing.end();
  }

  private void collectSuperInterfaces(DexType iface, Set<DexType> interfaces) {
    DexClass clazz = appInfo.definitionFor(iface);
    // In cases where we lack the interface's definition, we can at least look at subtypes and
    // tie those up to get proper naming.
    if (clazz != null) {
      for (DexType type : clazz.interfaces.values) {
        if (interfaces.add(type)) {
          collectSuperInterfaces(type, interfaces);
        }
      }
    }
  }


  private void collectSubInterfaces(DexType iface, final Set<DexType> interfaces) {
    iface.forAllExtendsSubtypes(new Consumer<DexType>() {
      @Override
      public void accept(DexType subtype) {
        assert subtype.isInterface();
        if (interfaces.add(subtype)) {
          MethodNameMinifier.this.collectSubInterfaces(subtype, interfaces);
        }
      }
    });
  }

  private void addStatesToGlobalMapForMethod(
      DexEncodedMethod method, Set<NamingState<DexProto>> collectedStates,
      Map<Wrapper<DexMethod>, Set<NamingState<DexProto>>> globalStateMap,
      Map<Wrapper<DexMethod>, Set<DexMethod>> sourceMethodsMap,
      Map<Wrapper<DexMethod>, NamingState<DexProto>> originStates, DexType originInterface) {
    Wrapper<DexMethod> key = equivalence.wrap(method.method);
    Set<NamingState<DexProto>> stateSet =
        globalStateMap.computeIfAbsent(key, new Function<Wrapper<DexMethod>, Set<NamingState<DexProto>>>() {
          @Override
          public Set<NamingState<DexProto>> apply(Wrapper<DexMethod> k) {
            return new HashSet<>();
          }
        });
    stateSet.addAll(collectedStates);
    sourceMethodsMap.computeIfAbsent(key, new Function<Wrapper<DexMethod>, Set<DexMethod>>() {
      @Override
      public Set<DexMethod> apply(Wrapper<DexMethod> k) {
        return new HashSet<>();
      }
    }).add(method.method);
    originStates.putIfAbsent(key, states.get(originInterface));
  }

  private void assignNameForInterfaceMethodInAllStates(DexMethod method,
      Set<NamingState<DexProto>> collectedStates,
      Set<DexMethod> sourceMethods,
      Map<DexMethod, DexString> renaming, NamingState<DexProto> originState) {
    boolean isReserved = false;
    if (globalState.isReserved(method.name, method.proto)) {
      for (NamingState<DexProto> state : collectedStates) {
        if (state.isReserved(method.name, method.proto)) {
          isReserved = true;
          break;
        }
      }
      if (isReserved) {
        // This method's name is reserved in at least on naming state, so reserve it everywhere.
        for (NamingState<DexProto> state : collectedStates) {
          state.reserveName(method.name, method.proto);
        }
        return;
      }
    }
    // We use the origin state to allocate a name here so that we can reuse names between different
    // unrelated interfaces. This saves some space. The alternative would be to use a global state
    // for allocating names, which would save the work to search here.
    DexString candidate = null;
    do {
      candidate = originState.assignNewNameFor(method.name, method.proto, false);
      for (NamingState<DexProto> state : collectedStates) {
        if (!state.isAvailable(method.name, method.proto, candidate)) {
          candidate = null;
          break;
        }
      }
    } while (candidate == null);
    for (NamingState<DexProto> state : collectedStates) {
      state.addRenaming(method.name, method.proto, candidate);
    }
    // Rename all methods in interfaces that gave rise to this renaming.
    for (DexMethod sourceMethod : sourceMethods) {
      renaming.put(sourceMethod, candidate);
    }
  }

  private void reserveNamesInClasses(DexType type, DexType libraryFrontier,
      NamingState<DexProto> parent,
      Map<DexType, DexType> frontierMap) {
    assert !type.isInterface();
    DexClass holder = appInfo.definitionFor(type);
    NamingState<DexProto> state = allocateNamingStateAndReserve(holder, type, libraryFrontier,
        parent, frontierMap);
    // If this is a library class (or effectively a library class as it is missing) move the
    // frontier forward.
    type.forAllExtendsSubtypes(new Consumer<DexType>() {
      @Override
      public void accept(DexType subtype) {
        assert !subtype.isInterface();
        MethodNameMinifier.this.reserveNamesInClasses(subtype,
                holder == null || holder.isLibraryClass() ? subtype : libraryFrontier,
                state, frontierMap);
      }
    });
  }

  private void reserveNamesInInterfaces(DexType type, Map<DexType, DexType> frontierMap) {
    assert type.isInterface();
    frontierMap.put(type, type);
    DexClass holder = appInfo.definitionFor(type);
    allocateNamingStateAndReserve(holder, type, type, null, frontierMap);
  }

  private NamingState<DexProto> allocateNamingStateAndReserve(DexClass holder, DexType type,
                                                              DexType libraryFrontier,
                                                              final NamingState<DexProto> parent,
                                                              Map<DexType, DexType> frontierMap) {
    frontierMap.put(type, libraryFrontier);
    final NamingState<DexProto> state =
        states.computeIfAbsent(libraryFrontier,
                new Function<DexType, NamingState<DexProto>>() {
                  @Override
                  public NamingState<DexProto> apply(DexType ignore) {
                    return parent == null
                            ? NamingState.createRoot(appInfo.dexItemFactory, dictionary)
                            : parent.createChild();
                  }
                });
    if (holder != null) {
      final boolean keepAll = holder.isLibraryClass() || holder.accessFlags.isAnnotation();
      holder.forEachMethod(new Consumer<DexEncodedMethod>() {
        @Override
        public void accept(DexEncodedMethod method) {
          MethodNameMinifier.this.reserveNamesForMethod(method, keepAll, state);
        }
      });
    }
    return state;
  }

  private void reserveNamesForMethod(DexEncodedMethod method,
      boolean keepAll, NamingState<DexProto> state) {
    if (keepAll || rootSet.noObfuscation.contains(method)) {
      state.reserveName(method.method.name, method.method.proto);
      globalState.reserveName(method.method.name, method.method.proto);
    }
  }
}
