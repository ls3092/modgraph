package dev.modgraph.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 单个加载器宇宙的有向图（SPEC §3.6.3）。
 *
 * <p>节点是 {@code modId}；同宇宙内同 {@code modId} 的多条记录已经合并成一个节点，边集取并集。
 * 邻接表按 ASCII 升序存放，只是为了两次运行的遍历顺序一致；这个顺序**不是**拓扑序，
 * 也不代表任何安装或加载次序（§3.6.5）。</p>
 */
public final class UniverseGraph {

    private final LoaderUniverse universe;
    private final Map<String, Set<String>> adjacency;

    UniverseGraph(final LoaderUniverse universe, final Map<String, Set<String>> adjacency) {
        this.universe = Objects.requireNonNull(universe, "universe");
        final Map<String, Set<String>> copy = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new TreeSet<>(entry.getValue())));
        }
        this.adjacency = Collections.unmodifiableMap(copy);
    }

    public LoaderUniverse universe() {
        return universe;
    }

    public int nodeCount() {
        return adjacency.size();
    }

    /** 去重后的边数：不同的 {@code (源节点, 目标节点)} 对。 */
    public int edgeCount() {
        int count = 0;
        for (Set<String> targets : adjacency.values()) {
            count += targets.size();
        }
        return count;
    }

    public boolean hasNode(final String modId) {
        return adjacency.containsKey(modId);
    }

    /** 节点的全部后继，按 ASCII 升序；没有后继时返回空集合。 */
    Set<String> successors(final String modId) {
        return adjacency.getOrDefault(modId, Set.of());
    }

    /** 全部节点，按 ASCII 升序；只供环检测内部遍历，永不作为输出暴露。 */
    Set<String> nodes() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }
}
