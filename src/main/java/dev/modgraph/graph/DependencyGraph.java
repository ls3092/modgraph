package dev.modgraph.graph;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 全部宇宙的依赖图与五桶计数（SPEC §3.6.3）。
 *
 * <p>本对象只暴露计数与单个宇宙的图，不暴露任何有序节点序列：拓扑排序的结果不会出现在这里，
 * 也不会出现在任何报告对象里（§3.6.5）。</p>
 */
public final class DependencyGraph {

    private final Map<LoaderUniverse, UniverseGraph> graphs;
    private final Map<LoaderUniverse, Map<GraphBucket, Integer>> buckets;
    private final Map<LoaderUniverse, Integer> hardDeclarations;
    private final int excludedRecords;

    DependencyGraph(final Map<LoaderUniverse, UniverseGraph> graphs,
                    final Map<LoaderUniverse, Map<GraphBucket, Integer>> buckets,
                    final Map<LoaderUniverse, Integer> hardDeclarations,
                    final int excludedRecords) {
        final Map<LoaderUniverse, UniverseGraph> graphCopy = new EnumMap<>(LoaderUniverse.class);
        graphCopy.putAll(graphs);
        this.graphs = Collections.unmodifiableMap(graphCopy);

        final Map<LoaderUniverse, Map<GraphBucket, Integer>> bucketCopy =
                new EnumMap<>(LoaderUniverse.class);
        for (Map.Entry<LoaderUniverse, Map<GraphBucket, Integer>> entry : buckets.entrySet()) {
            final Map<GraphBucket, Integer> perUniverse = new EnumMap<>(GraphBucket.class);
            perUniverse.putAll(entry.getValue());
            bucketCopy.put(entry.getKey(), Collections.unmodifiableMap(perUniverse));
        }
        this.buckets = Collections.unmodifiableMap(bucketCopy);

        final Map<LoaderUniverse, Integer> hardCopy = new EnumMap<>(LoaderUniverse.class);
        hardCopy.putAll(hardDeclarations);
        this.hardDeclarations = Collections.unmodifiableMap(hardCopy);
        this.excludedRecords = excludedRecords;
    }

    public UniverseGraph universe(final LoaderUniverse universe) {
        return graphs.get(Objects.requireNonNull(universe, "universe"));
    }

    /** 某个宇宙里落在该桶的硬依赖声明数。 */
    public int bucket(final LoaderUniverse universe, final GraphBucket bucket) {
        return buckets.getOrDefault(universe, Map.of()).getOrDefault(bucket, 0);
    }

    /** 全部宇宙合计的某个桶的声明数。 */
    public int totalBucket(final GraphBucket bucket) {
        int total = 0;
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            total += bucket(universe, bucket);
        }
        return total;
    }

    /** 某个宇宙的硬依赖声明数。 */
    public int hardDeclarations(final LoaderUniverse universe) {
        return hardDeclarations.getOrDefault(universe, 0);
    }

    /** 全部宇宙的硬依赖声明数。 */
    public int totalHardDeclarations() {
        int total = 0;
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            total += hardDeclarations(universe);
        }
        return total;
    }

    /** {@code loaderFamily = UNKNOWN} 的 OK 记录数，永不成节点。 */
    public int excludedRecords() {
        return excludedRecords;
    }
}
