package dev.modgraph.graph;

import dev.modgraph.model.Constraint;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.resolve.EnvironmentFacts;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 按 SPEC §3.6 建图：每个加载器宇宙一张图，只有 {@code depends} 建边。
 *
 * <p>硬依赖的判据是“从 {@code depends} 读出的 {@link Constraint}”，**不是** {@code required} 标志：
 * §2.1 与 §2.2 给 {@code breaks} 也设了 {@code required=true}，用标志当判据会把冲突声明误建成边。
 * {@code recommends}、{@code breaks} 与 {@code ordering} 一律不建边（§3.6.3）。</p>
 *
 * <p>每条硬依赖恰好归入一个桶；归桶不完整或落空即为程序缺陷，直接抛异常让本次运行失败，
 * 不跳过、不降级为警告。</p>
 */
public final class GraphBuilder {

    private final EnvironmentFacts environment;

    public GraphBuilder() {
        this(EnvironmentFacts.declared());
    }

    GraphBuilder(final EnvironmentFacts environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public DependencyGraph build(final List<ModCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");

        final Map<LoaderUniverse, Set<String>> nodes = emptyUniverseMap();
        final Map<LoaderUniverse, Set<String>> provides = emptyUniverseMap();
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            nodes.put(universe, new TreeSet<>());
            provides.put(universe, new TreeSet<>());
        }

        int excluded = 0;
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final Optional<LoaderUniverse> universe = LoaderUniverse.of(candidate.loaderFamily());
            if (universe.isEmpty()) {
                excluded++;
                continue;
            }
            nodes.get(universe.get()).add(candidate.modId());
            provides.get(universe.get()).addAll(candidate.provides());
        }

        final Map<LoaderUniverse, Map<GraphBucket, Integer>> buckets = emptyUniverseMap();
        final Map<LoaderUniverse, Map<String, Set<String>>> adjacency = emptyUniverseMap();
        final Map<LoaderUniverse, Integer> hardDeclarations = new EnumMap<>(LoaderUniverse.class);
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            final Map<GraphBucket, Integer> perBucket = new EnumMap<>(GraphBucket.class);
            for (GraphBucket bucket : GraphBucket.values()) {
                perBucket.put(bucket, 0);
            }
            buckets.put(universe, perBucket);
            final Map<String, Set<String>> perUniverse = new TreeMap<>();
            for (String node : nodes.get(universe)) {
                // 没有出边的节点同样是节点，必须先占位，否则节点计数和强连通分量都会漏掉它。
                perUniverse.put(node, new TreeSet<>());
            }
            adjacency.put(universe, perUniverse);
            hardDeclarations.put(universe, 0);
        }

        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final Optional<LoaderUniverse> universe = LoaderUniverse.of(candidate.loaderFamily());
            if (universe.isEmpty()) {
                continue;
            }
            final LoaderUniverse current = universe.get();
            for (Constraint constraint : candidate.depends()) {
                hardDeclarations.merge(current, 1, Integer::sum);
                final String target = constraint.targetId();
                final GraphBucket bucket = classify(current, target, nodes, provides);
                buckets.get(current).merge(bucket, 1, Integer::sum);
                if (bucket == GraphBucket.EDGE) {
                    adjacency.get(current)
                            .computeIfAbsent(candidate.modId(), modId -> new TreeSet<>())
                            .add(target);
                }
            }
        }

        final Map<LoaderUniverse, UniverseGraph> graphs = new EnumMap<>(LoaderUniverse.class);
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            graphs.put(universe, new UniverseGraph(universe, adjacency.get(universe)));
        }
        final DependencyGraph graph = new DependencyGraph(graphs, buckets, hardDeclarations, excluded);
        verifyBuckets(graph);
        return graph;
    }

    private GraphBucket classify(final LoaderUniverse universe,
                                 final String target,
                                 final Map<LoaderUniverse, Set<String>> nodes,
                                 final Map<LoaderUniverse, Set<String>> provides) {
        if (nodes.get(universe).contains(target)) {
            return GraphBucket.EDGE;
        }
        if (environment.versionOf(target).isPresent()) {
            return GraphBucket.ENVIRONMENT;
        }
        for (LoaderUniverse other : LoaderUniverse.values()) {
            if (other != universe && nodes.get(other).contains(target)) {
                return GraphBucket.OTHER_UNIVERSE;
            }
        }
        if (provides.get(universe).contains(target)) {
            return GraphBucket.ALIAS_ONLY;
        }
        return GraphBucket.ABSENT;
    }

    private static void verifyBuckets(final DependencyGraph graph) {
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            final int sum = bucketSum(graph, universe);
            final int declared = graph.hardDeclarations(universe);
            if (sum != declared) {
                throw new IllegalStateException(
                        "硬依赖归桶不完整：宇宙 " + universe + " 五桶合计 " + sum
                                + " != 硬依赖声明数 " + declared);
            }
            final int edges = graph.universe(universe).edgeCount();
            final int edgeDeclarations = graph.bucket(universe, GraphBucket.EDGE);
            if (edges > edgeDeclarations) {
                throw new IllegalStateException(
                        "依赖图的边数超过 EDGE 声明数：宇宙 " + universe + " 边 " + edges
                                + " > EDGE 声明 " + edgeDeclarations);
            }
        }
        final int totalSum = totalBucketSum(graph);
        final int totalDeclared = graph.totalHardDeclarations();
        if (totalSum != totalDeclared) {
            throw new IllegalStateException(
                    "硬依赖归桶总数不守恒：五桶合计 " + totalSum + " != 硬依赖声明数 " + totalDeclared);
        }
    }

    private static int bucketSum(final DependencyGraph graph, final LoaderUniverse universe) {
        int sum = 0;
        for (GraphBucket bucket : GraphBucket.values()) {
            sum += graph.bucket(universe, bucket);
        }
        return sum;
    }

    private static int totalBucketSum(final DependencyGraph graph) {
        int sum = 0;
        for (GraphBucket bucket : GraphBucket.values()) {
            sum += graph.totalBucket(bucket);
        }
        return sum;
    }

    private static <T> Map<LoaderUniverse, T> emptyUniverseMap() {
        return new EnumMap<>(LoaderUniverse.class);
    }
}
