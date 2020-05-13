// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.Sets;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Call graph representation.
 * <p>
 * Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 * <p>
 * Only methods in the program - not library methods - are represented.
 * <p>
 * The directional edges are represented as sets of nodes in each node (called methods and callees).
 * <p>
 * A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 * <p>
 * Recursive calls are not present.
 */
public class CallGraph {

  private CallGraph(InternalOptions options) {
    this.shuffle = options.testing.irOrdering;
  }

  private static class Node {

    public final DexEncodedMethod method;
    private int invokeCount = 0;
    private boolean isSelfRecursive = false;

    // Outgoing calls from this method.
    private final Set<Node> callees = new LinkedHashSet<>();

    // Incoming calls to this method.
    private final Set<Node> callers = new LinkedHashSet<>();

    private Node(DexEncodedMethod method) {
      this.method = method;
    }

    public boolean isBridge() {
      return method.accessFlags.isBridge();
    }

    private void addCallee(Node method) {
      callees.add(method);
    }

    private void addCaller(Node method) {
      callers.add(method);
    }

    boolean isSelfRecursive() {
      return isSelfRecursive;
    }

    boolean isLeaf() {
      return callees.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("MethodNode for: ");
      builder.append(method.qualifiedName());
      builder.append(" (");
      builder.append(callees.size());
      builder.append(" callees, ");
      builder.append(callers.size());
      builder.append(" callers");
      if (isBridge()) {
        builder.append(", bridge");
      }
      if (isSelfRecursive()) {
        builder.append(", recursive");
      }
      builder.append(", invoke count " + invokeCount);
      builder.append(").\n");
      if (callees.size() > 0) {
        builder.append("Callees:\n");
        for (Node call : callees) {
          builder.append("  ");
          builder.append(call.method.qualifiedName());
          builder.append("\n");
        }
      }
      if (callers.size() > 0) {
        builder.append("Callers:\n");
        for (Node caller : callers) {
          builder.append("  ");
          builder.append(caller.method.qualifiedName());
          builder.append("\n");
        }
      }
      return builder.toString();
    }
  }

  private final Map<DexEncodedMethod, Node> nodes = new LinkedHashMap<>();
  private final Map<DexEncodedMethod, Set<DexEncodedMethod>> breakers = new HashMap<>();
  private final Function<List<DexEncodedMethod>, List<DexEncodedMethod>> shuffle;

  // Returns whether the method->callee edge has been removed from the call graph
  // to break a cycle in the call graph.
  public boolean isBreaker(DexEncodedMethod method, DexEncodedMethod callee) {
    Set<DexEncodedMethod> value = breakers.get(method);
    return (value != null) && value.contains(callee);
  }

  private Set<DexEncodedMethod> singleCallSite = Sets.newIdentityHashSet();
  private Set<DexEncodedMethod> doubleCallSite = Sets.newIdentityHashSet();

  public static CallGraph build(DexApplication application, AppInfoWithSubtyping appInfo,
      GraphLense graphLense, InternalOptions options) {
    CallGraph graph = new CallGraph(options);
    DexClass[] classes = application.classes().toArray(new DexClass[application.classes().size()]);
    Arrays.sort(classes, new Comparator<DexClass>() {
      @Override
      public int compare(DexClass a, DexClass b) {
        return a.type.slowCompareTo(b.type);
      }
    });
    for (DexClass clazz : classes) {
      for (DexEncodedMethod method : clazz.allMethodsSorted()) {
        Node node = graph.ensureMethodNode(method);
        InvokeExtractor extractor = new InvokeExtractor(appInfo, graphLense, node, graph);
        method.registerReachableDefinitions(extractor);
      }
    }
    assert allMethodsExists(application, graph);
    graph.breakCycles();
    assert graph.breakCycles() == 0;  // This time the cycles should be gone.
    graph.fillCallSiteSets(appInfo);
    return graph;
  }

  /**
   * Check if the <code>method</code> is guaranteed to only have a single call site.
   * <p>
   * For pinned methods (methods kept through Proguard keep rules) this will always answer
   * <code>false</code>.
   */
  public boolean hasSingleCallSite(DexEncodedMethod method) {
    return singleCallSite.contains(method);
  }

  public boolean hasDoubleCallSite(DexEncodedMethod method) {
    return doubleCallSite.contains(method);
  }

  private void fillCallSiteSets(AppInfoWithSubtyping appInfo) {
    assert singleCallSite.isEmpty();
    AppInfoWithLiveness liveAppInfo = appInfo.withLiveness();
    if (liveAppInfo == null) {
      return;
    }
    for (Node value : nodes.values()) {
      // For non-pinned methods we know the exact number of call sites.
      if (!appInfo.withLiveness().pinnedItems.contains(value.method)) {
        if (value.invokeCount == 1) {
          singleCallSite.add(value.method);
        } else if (value.invokeCount == 2) {
          doubleCallSite.add(value.method);
        }
      }
    }
  }

  private static boolean allMethodsExists(DexApplication application, final CallGraph graph) {
    for (DexProgramClass clazz : application.classes()) {
      clazz.forEachMethod(new Consumer<DexEncodedMethod>() {
        @Override
        public void accept(DexEncodedMethod method) {
          assert graph.nodes.get(method) != null;
        }
      });
    }
    return true;
  }

  /**
   * Extract the next set of leaves (nodes with an call (outgoing) degree of 0) if any.
   * <p>
   * All nodes in the graph are extracted if called repeatedly until null is returned.
   * Please note that there are no cycles in this graph (see {@link #breakCycles}).
   * <p>
   *
   * @return List of {@link DexEncodedMethod}.
   */
  private List<DexEncodedMethod> extractLeaves() {
    if (isEmpty()) {
      return Collections.emptyList();
    }
    // First identify all leaves before removing them from the graph.
    List<Node> leaves = nodes.values().stream().filter(new Predicate<Node>() {
      @Override
      public boolean test(Node node) {
        return node.isLeaf();
      }
    }).collect(Collectors.toList());
    leaves.forEach(new Consumer<Node>() {
      @Override
      public void accept(Node leaf) {
        leaf.callers.forEach(new Consumer<Node>() {
          @Override
          public void accept(Node caller) {
            caller.callees.remove(leaf);
          }
        });
        nodes.remove(leaf.method);
      }
    });
    return shuffle.apply(leaves.stream().map(new Function<Node, DexEncodedMethod>() {
      @Override
      public DexEncodedMethod apply(Node leaf) {
        return leaf.method;
      }
    }).collect(Collectors.toList()));
  }

  private int traverse(Node node, Set<Node> stack, Set<Node> marked) {
    int numberOfCycles = 0;
    if (!marked.contains(node)) {
      assert !stack.contains(node);
      stack.add(node);
      ArrayList<Node> toBeRemoved = null;
      // Sort the callees before calling traverse recursively.
      // This will ensure cycles are broken the same way across
      // multiple invocations of the R8 compiler.
      Node[] callees = node.callees.toArray(new Node[node.callees.size()]);
      Arrays.sort(callees, new Comparator<Node>() {
        @Override
        public int compare(Node a, Node b) {
          return a.method.method.slowCompareTo(b.method.method);
        }
      });
      for (Node callee : callees) {
        if (stack.contains(callee)) {
          if (toBeRemoved == null) {
            toBeRemoved = new ArrayList<>();
          }
          // We have a cycle; break it by removing node->callee.
          toBeRemoved.add(callee);
          callee.callers.remove(node);
          breakers.computeIfAbsent(node.method,
                  new Function<DexEncodedMethod, Set<DexEncodedMethod>>() {
                    @Override
                    public Set<DexEncodedMethod> apply(DexEncodedMethod ignore) {
                      return Sets.newIdentityHashSet();
                    }
                  }).add(callee.method);
        } else {
          numberOfCycles += traverse(callee, stack, marked);
        }
      }
      if (toBeRemoved != null) {
        numberOfCycles += toBeRemoved.size();
        node.callees.removeAll(toBeRemoved);
      }
      stack.remove(node);
      marked.add(node);
    }
    return numberOfCycles;
  }

  private int breakCycles() {
    // Break cycles in this call graph by removing edges causing cycles.
    // The remove edges are stored in @breakers.
    int numberOfCycles = 0;
    Set<Node> stack = Sets.newIdentityHashSet();
    Set<Node> marked = Sets.newIdentityHashSet();
    for (Node node : nodes.values()) {
      numberOfCycles += traverse(node, stack, marked);
    }
    return numberOfCycles;
  }

  synchronized private Node ensureMethodNode(DexEncodedMethod method) {
    return nodes.computeIfAbsent(method, new Function<DexEncodedMethod, Node>() {
      @Override
      public Node apply(DexEncodedMethod k) {
        return new Node(method);
      }
    });
  }

  synchronized private void addCall(Node caller, Node callee) {
    assert caller != null;
    assert callee != null;
    if (caller != callee) {
      caller.addCallee(callee);
      callee.addCaller(caller);
    } else {
      caller.isSelfRecursive = true;
    }
    callee.invokeCount++;
  }

  public boolean isEmpty() {
    return nodes.size() == 0;
  }

  public void forEachMethod(Consumer<DexEncodedMethod> consumer, ExecutorService executorService)
      throws ExecutionException {
    while (!isEmpty()) {
      List<DexEncodedMethod> methods = extractLeaves();
      assert methods.size() > 0;
      List<Future<?>> futures = new ArrayList<>();
      for (DexEncodedMethod method : methods) {
        futures.add(executorService.submit(new Runnable() {
          @Override
          public void run() {
            consumer.accept(method);
          }
        }));
      }
      ThreadUtils.awaitFutures(futures);
    }
  }

  public void dump() {
    nodes.forEach(new BiConsumer<DexEncodedMethod, Node>() {
      @Override
      public void accept(DexEncodedMethod m, Node n) {
        System.out.println(n + "\n");
      }
    });
  }

  private static class InvokeExtractor extends UseRegistry {

    AppInfoWithSubtyping appInfo;
    GraphLense graphLense;
    Node caller;
    CallGraph graph;

    InvokeExtractor(AppInfoWithSubtyping appInfo, GraphLense graphLense, Node caller,
        CallGraph graph) {
      this.appInfo = appInfo;
      this.graphLense = graphLense;
      this.caller = caller;
      this.graph = graph;
    }

    private void addClassInitializerTarget(DexClass clazz) {
      assert clazz != null;
      if (clazz.hasClassInitializer() && !clazz.isLibraryClass()) {
        DexEncodedMethod possibleTarget = clazz.getClassInitializer();
        addTarget(possibleTarget);
      }
    }

    private void addClassInitializerTarget(DexType type) {
      if (type.isArrayType()) {
        type = type.toBaseType(appInfo.dexItemFactory);
      }
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz != null) {
        addClassInitializerTarget(clazz);
      }
    }

    private void addTarget(DexEncodedMethod target) {
      Node callee = graph.ensureMethodNode(target);
      graph.addCall(caller, callee);
    }

    private void addPossibleTarget(DexEncodedMethod possibleTarget) {
      DexClass possibleTargetClass =
          appInfo.definitionFor(possibleTarget.method.getHolder());
      if (possibleTargetClass != null && !possibleTargetClass.isLibraryClass()) {
        addTarget(possibleTarget);
      }
    }

    private void addPossibleTargets(
        DexEncodedMethod definition, Set<DexEncodedMethod> possibleTargets) {
      for (DexEncodedMethod possibleTarget : possibleTargets) {
        if (possibleTarget != definition) {
          addPossibleTarget(possibleTarget);
        }
      }
    }

    private void processInvoke(Type type, DexMethod method) {
      DexEncodedMethod source = caller.method;
      method = graphLense.lookupMethod(method, source);
      DexEncodedMethod definition = appInfo.lookup(type, method);
      if (definition != null) {
        assert !source.accessFlags.isBridge() || definition != caller.method;
        DexClass definitionHolder = appInfo.definitionFor(definition.method.getHolder());
        assert definitionHolder != null;
        if (!definitionHolder.isLibraryClass()) {
          addClassInitializerTarget(definitionHolder);
          addTarget(definition);
          // For virtual and interface calls add all potential targets that could be called.
          if (type == Type.VIRTUAL || type == Type.INTERFACE) {
            Set<DexEncodedMethod> possibleTargets;
            if (definitionHolder.isInterface()) {
              possibleTargets = appInfo.lookupInterfaceTargets(definition.method);
            } else {
              possibleTargets = appInfo.lookupVirtualTargets(definition.method);
            }
            addPossibleTargets(definition, possibleTargets);
          }
        }
      }
    }

    private void processFieldAccess(DexField field) {
      // Any field access implicitly calls the class initializer.
      addClassInitializerTarget(field.getHolder());
    }

    @Override
    public boolean registerInvokeVirtual(DexMethod method) {
      processInvoke(Type.VIRTUAL, method);
      return false;
    }

    @Override
    public boolean registerInvokeDirect(DexMethod method) {
      processInvoke(Type.DIRECT, method);
      return false;
    }

    @Override
    public boolean registerInvokeStatic(DexMethod method) {
      processInvoke(Type.STATIC, method);
      return false;
    }

    @Override
    public boolean registerInvokeInterface(DexMethod method) {
      processInvoke(Type.INTERFACE, method);
      return false;
    }

    @Override
    public boolean registerInvokeSuper(DexMethod method) {
      processInvoke(Type.SUPER, method);
      return false;
    }

    @Override
    public boolean registerInstanceFieldWrite(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerInstanceFieldRead(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerNewInstance(DexType type) {
      addClassInitializerTarget(type);
      return false;
    }

    @Override
    public boolean registerStaticFieldRead(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerStaticFieldWrite(DexField field) {
      processFieldAccess(field);
      return false;
    }

    @Override
    public boolean registerTypeReference(DexType type) {
      return false;
    }
  }
}
