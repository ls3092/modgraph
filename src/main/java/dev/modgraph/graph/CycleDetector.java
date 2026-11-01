package dev.modgraph.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 环检测（SPEC §3.6.4）：拓扑排序判无环，强连通分量定位环组。
 *
 * <p>拓扑排序只用于回答“有没有环”。它移除节点的顺序**不出现在任何字段、日志或返回值里**，
 * 因为 §3.6.5 禁止任何形式的安装顺序、加载顺序或拓扑序。</p>
 *
 * <p>一致性不变量：拓扑排序能够完成 ⇔ 环组数为 0。两者不一致即为程序缺陷，抛异常让运行失败。</p>
 */
public final class CycleDetector {

    public CycleReport detect(final DependencyGraph graph) {
        Objects.requireNonNull(graph, "graph");
        final List<CycleGroup> cycleGroups = new ArrayList<>();
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            final UniverseGraph current = graph.universe(universe);
            final boolean acyclic = topologicalSortCompletes(current);
            final int before = cycleGroups.size();
            for (Set<String> component : stronglyConnectedComponents(current)) {
                if (component.size() > 1 || hasSelfLoop(current, component)) {
                    cycleGroups.add(new CycleGroup(universe, List.copyOf(component)));
                }
            }
            final boolean foundCycle = cycleGroups.size() > before;
            if (acyclic == foundCycle) {
                throw new IllegalStateException(
                        "拓扑排序与强连通分量不一致：宇宙 " + universe
                                + "，拓扑排序可完成=" + acyclic + "，环组数=" + (cycleGroups.size() - before));
            }
        }
        return new CycleReport(graph, cycleGroups);
    }

    /**
     * Kahn 拓扑排序，只返回“是否所有节点都被移除”。
     * 移除顺序是局部变量，不记录、不返回、不打日志。
     */
    private static boolean topologicalSortCompletes(final UniverseGraph graph) {
        final Map<String, Integer> inDegree = new HashMap<>();
        for (String node : graph.nodes()) {
            inDegree.put(node, 0);
        }
        for (String node : graph.nodes()) {
            for (String successor : graph.successors(node)) {
                inDegree.merge(successor, 1, Integer::sum);
            }
        }
        final Deque<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        int removed = 0;
        while (!ready.isEmpty()) {
            final String node = ready.remove();
            removed++;
            for (String successor : graph.successors(node)) {
                final int remaining = inDegree.merge(successor, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(successor);
                }
            }
        }
        return removed == graph.nodeCount();
    }

    /** Kosaraju 的迭代实现；返回全部强连通分量的成员集合。 */
    private static List<Set<String>> stronglyConnectedComponents(final UniverseGraph graph) {
        final Map<String, Set<String>> reverse = new TreeMap<>();
        for (String node : graph.nodes()) {
            reverse.put(node, new TreeSet<>());
        }
        for (String node : graph.nodes()) {
            for (String successor : graph.successors(node)) {
                reverse.get(successor).add(node);
            }
        }

        final Set<String> visited = new HashSet<>();
        final Deque<String> finished = new ArrayDeque<>();
        for (String start : graph.nodes()) {
            if (!visited.contains(start)) {
                finishOrder(graph, start, visited, finished);
            }
        }

        final Set<String> assigned = new HashSet<>();
        final List<Set<String>> components = new ArrayList<>();
        for (String start : finished) {
            if (!assigned.add(start)) {
                continue;
            }
            final Set<String> component = new TreeSet<>();
            component.add(start);
            final Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                final String current = stack.pop();
                for (String predecessor : reverse.get(current)) {
                    if (assigned.add(predecessor)) {
                        component.add(predecessor);
                        stack.push(predecessor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private static void finishOrder(final UniverseGraph graph,
                                    final String start,
                                    final Set<String> visited,
                                    final Deque<String> finished) {
        final Deque<String> nodes = new ArrayDeque<>();
        final Deque<Iterator<String>> iterators = new ArrayDeque<>();
        visited.add(start);
        nodes.push(start);
        iterators.push(graph.successors(start).iterator());
        while (!nodes.isEmpty()) {
            final Iterator<String> iterator = iterators.peek();
            if (iterator.hasNext()) {
                final String next = iterator.next();
                if (visited.add(next)) {
                    nodes.push(next);
                    iterators.push(graph.successors(next).iterator());
                }
            } else {
                finished.push(nodes.pop());
                iterators.pop();
            }
        }
    }

    private static boolean hasSelfLoop(final UniverseGraph graph, final Set<String> component) {
        if (component.size() != 1) {
            return false;
        }
        final String only = component.iterator().next();
        return graph.successors(only).contains(only);
    }
}
