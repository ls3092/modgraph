package dev.modgraph.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 环检测的结果（SPEC §3.6.4）。
 *
 * <p>只暴露环组（成员集合，按 §3.6.4 规范化排序）与有无环的判定，不暴露任何有序节点序列。
 * 拓扑排序只用于判定是否有环，它的中间结果（移除顺序、入度为 0 的清单、分层、深度）
 * 不存在于本对象的任何字段里（§3.6.5）。</p>
 */
public final class CycleReport {

    private final DependencyGraph graph;
    private final List<CycleGroup> cycleGroups;
    private final boolean acyclic;

    CycleReport(final DependencyGraph graph, final List<CycleGroup> cycleGroups) {
        this.graph = Objects.requireNonNull(graph, "graph");
        final List<CycleGroup> sorted = new ArrayList<>(cycleGroups);
        // 行序：先按宇宙名 ASCII 升序（FABRIC < FORGE < NEOFORGE），再按 members 字符串 ASCII 升序。
        sorted.sort(Comparator.comparing((CycleGroup group) -> group.universe().name())
                .thenComparing(CycleGroup::csvMembers));
        this.cycleGroups = Collections.unmodifiableList(sorted);
        this.acyclic = sorted.isEmpty();
    }

    public DependencyGraph graph() {
        return graph;
    }

    /** 全部环组，已按 §3.6.4 规范排序；无环时为空列表。 */
    public List<CycleGroup> cycleGroups() {
        return cycleGroups;
    }

    public int cycleGroupCount() {
        return cycleGroups.size();
    }

    public boolean acyclic() {
        return acyclic;
    }
}
