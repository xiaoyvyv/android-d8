// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.CachedHashValueDexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class NamingState<T extends CachedHashValueDexItem> {

  private final NamingState<T> parent;
  private final Map<T, InternalState> usedNames = new IdentityHashMap<>();
  private final DexItemFactory itemFactory;
  private final ImmutableList<String> dictionary;

  static <T extends CachedHashValueDexItem> NamingState<T> createRoot(
      DexItemFactory itemFactory, ImmutableList<String> dictionary) {
    return new NamingState<>(null, itemFactory, dictionary);
  }

  private NamingState(
      NamingState<T> parent,
      DexItemFactory itemFactory,
      ImmutableList<String> dictionary) {
    this.parent = parent;
    this.itemFactory = itemFactory;
    this.dictionary = dictionary;
  }

  public NamingState<T> createChild() {
    return new NamingState<>(this, itemFactory, dictionary);
  }

  private InternalState findInternalStateFor(T proto) {
    InternalState result = usedNames.get(proto);
    if (result == null && parent != null) {
      result = parent.findInternalStateFor(proto);
    }
    return result;
  }

  private InternalState getOrCreateInternalStateFor(T proto) {
    // TODO(herhut): Maybe allocate these sparsely and search via state chain.
    InternalState result = usedNames.get(proto);
    if (result == null) {
      if (parent != null) {
        InternalState parentState = parent.getOrCreateInternalStateFor(proto);
        result = parentState.createChild();
      } else {
        result = new InternalState(itemFactory, null, dictionary);
      }
      usedNames.put(proto, result);
    }
    return result;
  }

  public DexString getAssignedNameFor(DexString name, T proto) {
    InternalState state = findInternalStateFor(proto);
    if (state == null) {
      return null;
    }
    return state.getAssignedNameFor(name);
  }

  public DexString assignNewNameFor(DexString original, T proto, boolean markAsUsed) {
    DexString result = getAssignedNameFor(original, proto);
    if (result == null) {
      InternalState state = getOrCreateInternalStateFor(proto);
      result = state.getNameFor(original, markAsUsed);
    }
    return result;
  }

  public void reserveName(DexString name, T proto) {
    InternalState state = getOrCreateInternalStateFor(proto);
    state.reserveName(name);
  }

  public boolean isReserved(DexString name, T proto) {
    InternalState state = findInternalStateFor(proto);
    if (state == null) {
      return false;
    }
    return state.isReserved(name);
  }

  public boolean isAvailable(DexString original, T proto, DexString candidate) {
    InternalState state = findInternalStateFor(proto);
    if (state == null) {
      return true;
    }
    assert state.getAssignedNameFor(original) != candidate;
    return state.isAvailable(candidate);
  }

  public void addRenaming(DexString original, T proto, DexString newName) {
    InternalState state = getOrCreateInternalStateFor(proto);
    state.addRenaming(original, newName);
  }

  private static class InternalState {

    private static int INITIAL_NAME_COUNT = 1;
    private final static char[] EMPTY_CHAR_ARRARY = new char[0];

    protected final DexItemFactory itemFactory;
    private final InternalState parentInternalState;
    private Set<DexString> reservedNames = null;
    private Map<DexString, DexString> renamings = null;
    private int nameCount;
    private final Iterator<String> dictionaryIterator;

    private InternalState(DexItemFactory itemFactory, InternalState parentInternalState,
        Iterator<String> dictionaryIterator) {
      this.itemFactory = itemFactory;
      this.parentInternalState = parentInternalState;
      this.nameCount =
          parentInternalState == null ? INITIAL_NAME_COUNT : parentInternalState.nameCount;
      this.dictionaryIterator = dictionaryIterator;
    }

    private InternalState(DexItemFactory itemFactory, InternalState parentInternalState,
        List<String> dictionary) {
      this(itemFactory, parentInternalState, dictionary.iterator());
    }

    private boolean isReserved(DexString name) {
      return (reservedNames != null && reservedNames.contains(name))
          || (parentInternalState != null && parentInternalState.isReserved(name));
    }

    private boolean isAvailable(DexString name) {
      return !(renamings != null && renamings.containsValue(name))
          && !(reservedNames != null && reservedNames.contains(name))
          && (parentInternalState == null || parentInternalState.isAvailable(name));
    }

    public InternalState createChild() {
      return new InternalState(itemFactory, this, dictionaryIterator);
    }

    public void reserveName(DexString name) {
      if (reservedNames == null) {
        reservedNames = Sets.newIdentityHashSet();
      }
      reservedNames.add(name);
    }

    public DexString getAssignedNameFor(DexString original) {
      DexString result = renamings == null ? null : renamings.get(original);
      if (result == null && parentInternalState != null) {
        result = parentInternalState.getAssignedNameFor(original);
      }
      return result;
    }

    public DexString getNameFor(DexString original, boolean markAsUsed) {
      DexString name = getAssignedNameFor(original);
      if (name != null) {
        return name;
      }
      do {
        name = itemFactory.createString(nextSuggestedName());
      } while (!isAvailable(name));
      if (markAsUsed) {
        addRenaming(original, name);
      }
      return name;
    }

    public void addRenaming(DexString original, DexString newName) {
      if (renamings == null) {
        renamings = HashBiMap.create();
      }
      renamings.put(original, newName);
    }

    protected String nextSuggestedName() {
      if (dictionaryIterator.hasNext()) {
        return dictionaryIterator.next();
      } else {
        return StringUtils.numberToIdentifier(EMPTY_CHAR_ARRARY, nameCount++, false);
      }
    }
  }
}
