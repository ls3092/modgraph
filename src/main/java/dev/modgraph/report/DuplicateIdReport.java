package dev.modgraph.report;

import dev.modgraph.graph.LoaderUniverse;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * SPEC §3.7.3：重复 {@code modId} 的只读报告。
 *
 * <p>只在单个加载器宇宙内判定；同一 {@code modId} 同时出现在 {@code FABRIC} 与 {@code NEOFORGE}
 * 是两个永不同时存在的节点（§3.6.2），不算重复。组先按宇宙名、再按 {@code modId} 的 ASCII 升序排列。</p>
 */
public final class DuplicateIdReport {

    private final List<DuplicateIdGroup> groups;

    private DuplicateIdReport(final List<DuplicateIdGroup> groups) {
        final List<DuplicateIdGroup> sorted = new ArrayList<>(groups);
        sorted.sort(Comparator.comparing((DuplicateIdGroup group) -> group.universe().name())
                .thenComparing(DuplicateIdGroup::modId));
        this.groups = Collections.unmodifiableList(sorted);
    }

    public static DuplicateIdReport of(final List<ModCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        final Map<LoaderUniverse, Map<String, List<String>>> versionsByUniverse =
                new EnumMap<>(LoaderUniverse.class);
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            versionsByUniverse.put(universe, new TreeMap<>());
        }
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final Optional<LoaderUniverse> universe = LoaderUniverse.of(candidate.loaderFamily());
            if (universe.isEmpty()) {
                continue;
            }
            versionsByUniverse.get(universe.get())
                    .computeIfAbsent(candidate.modId(), modId -> new ArrayList<>())
                    .add(candidate.version());
        }

        final List<DuplicateIdGroup> groups = new ArrayList<>();
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            for (Map.Entry<String, List<String>> entry : versionsByUniverse.get(universe).entrySet()) {
                final List<String> versions = entry.getValue();
                if (versions.size() >= 2) {
                    groups.add(new DuplicateIdGroup(universe, entry.getKey(), versions.size(), versions));
                }
            }
        }
        return new DuplicateIdReport(groups);
    }

    /** 全部重复组，已按 §3.7.3 排序；没有重复时为空列表。 */
    public List<DuplicateIdGroup> groups() {
        return groups;
    }

    public int groupCount() {
        return groups.size();
    }

    public int pairCount() {
        int total = 0;
        for (DuplicateIdGroup group : groups) {
            total += group.pairCount();
        }
        return total;
    }

    public int sameVersionPairs() {
        return pairsOf(VersionRelation.SAME_VERSION);
    }

    public int differentVersionPairs() {
        return pairsOf(VersionRelation.DIFFERENT_VERSION);
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    private int pairsOf(final VersionRelation relation) {
        int total = 0;
        for (DuplicateIdGroup group : groups) {
            if (group.relation() == relation) {
                total += group.pairCount();
            }
        }
        return total;
    }
}
