package dev.modgraph.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * M1 的构造数据测试（SPEC §3.6.7 第 1、2、3、5、6 条）。
 *
 * <p>真实数据集里环组为 0，所以 2 环、3 环、自环、无环、跨加载器假环全部由这里的构造记录覆盖，
 * 一个都不跳过。没有 assume，没有 mock 文件读取。</p>
 */
class CycleDetectionTest {

    private static CycleReport report(final ModCandidate... candidates) {
        final DependencyGraph graph = new GraphBuilder().build(List.of(candidates));
        return new CycleDetector().detect(graph);
    }

    @Test
    void crossLoaderDependencyPairIsTwoNodesAndNoCycle() {
        final CycleReport report = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.NEOFORGE, "alpha"));

        assertThat(report.cycleGroupCount()).isZero();
        assertThat(report.acyclic()).isTrue();
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(1);
        assertThat(report.graph().universe(LoaderUniverse.NEOFORGE).nodeCount()).isEqualTo(1);
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isZero();
        assertThat(report.graph().universe(LoaderUniverse.NEOFORGE).edgeCount()).isZero();
        assertThat(report.graph().bucket(LoaderUniverse.FABRIC, GraphBucket.OTHER_UNIVERSE))
                .isEqualTo(1);
        assertThat(report.graph().bucket(LoaderUniverse.NEOFORGE, GraphBucket.OTHER_UNIVERSE))
                .isEqualTo(1);
    }

    @Test
    void sameModIdInTwoUniversesProducesTwoNodes() {
        final CycleReport report = report(
                GraphFixtures.ok("shared", LoaderFamily.FABRIC, "fabricdep"),
                GraphFixtures.ok("fabricdep", LoaderFamily.FABRIC),
                GraphFixtures.ok("shared", LoaderFamily.NEOFORGE, "neodep"),
                GraphFixtures.ok("neodep", LoaderFamily.NEOFORGE));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(2);
        assertThat(report.graph().universe(LoaderUniverse.NEOFORGE).nodeCount()).isEqualTo(2);
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
        assertThat(report.graph().universe(LoaderUniverse.NEOFORGE).edgeCount()).isEqualTo(1);
        assertThat(report.cycleGroupCount()).isZero();
    }

    @Test
    void twoRecordsWithTheSameModIdCollapseIntoOneNodeWithUnionedEdges() {
        final CycleReport report = report(
                GraphFixtures.ok("core", LoaderFamily.FABRIC, "left"),
                GraphFixtures.ok("core", LoaderFamily.FABRIC, "right"),
                GraphFixtures.ok("left", LoaderFamily.FABRIC),
                GraphFixtures.ok("right", LoaderFamily.FABRIC));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(3);
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(2);
        assertThat(report.graph().bucket(LoaderUniverse.FABRIC, GraphBucket.EDGE)).isEqualTo(2);
    }

    @Test
    void repeatedDeclarationOfTheSameTargetCollapsesToOneEdge() {
        final CycleReport report = report(
                GraphFixtures.ok("core", LoaderFamily.FABRIC, "target"),
                GraphFixtures.ok("core", LoaderFamily.FABRIC, "target"),
                GraphFixtures.ok("target", LoaderFamily.FABRIC));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(2);
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
        assertThat(report.graph().bucket(LoaderUniverse.FABRIC, GraphBucket.EDGE)).isEqualTo(2);
    }

    @Test
    void recommendsNeverCreatesAnEdge() {
        final CycleReport report = report(
                GraphFixtures.ok("a", LoaderFamily.FABRIC,
                        List.of(), List.of("b"), List.of(), List.of()),
                GraphFixtures.ok("b", LoaderFamily.FABRIC, "a"));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
        assertThat(report.graph().bucket(LoaderUniverse.FABRIC, GraphBucket.EDGE)).isEqualTo(1);
        assertThat(report.cycleGroupCount()).isZero();
    }

    @Test
    void breaksNeverCreatesAnEdgeEvenThoughRequiredIsTrue() {
        final CycleReport report = report(
                GraphFixtures.ok("a", LoaderFamily.FABRIC,
                        List.of(), List.of(), List.of("c"), List.of()),
                GraphFixtures.ok("c", LoaderFamily.FABRIC, "a"),
                GraphFixtures.ok("b", LoaderFamily.FABRIC),
                GraphFixtures.ok("a2", LoaderFamily.FABRIC));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
        assertThat(report.graph().bucket(LoaderUniverse.FABRIC, GraphBucket.EDGE)).isEqualTo(1);
        assertThat(report.cycleGroupCount()).isZero();
    }

    @Test
    void twoCycleIsOneGroup() {
        final CycleReport report = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC, "alpha"));

        assertThat(report.cycleGroupCount()).isEqualTo(1);
        assertThat(report.acyclic()).isFalse();
        assertThat(report.cycleGroups().get(0).universe()).isEqualTo(LoaderUniverse.FABRIC);
        assertThat(report.cycleGroups().get(0).members()).containsExactly("alpha", "beta");
    }

    @Test
    void threeCycleIsOneGroup() {
        final CycleReport report = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC, "gamma"),
                GraphFixtures.ok("gamma", LoaderFamily.FABRIC, "alpha"));

        assertThat(report.cycleGroupCount()).isEqualTo(1);
        assertThat(report.cycleGroups().get(0).members())
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void selfLoopIsOneGroup() {
        final CycleReport report = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "alpha"));

        assertThat(report.cycleGroupCount()).isEqualTo(1);
        assertThat(report.cycleGroups().get(0).members()).containsExactly("alpha");
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
    }

    @Test
    void dagHasNoCycleGroups() {
        final CycleReport report = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta", "gamma"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC, "delta"),
                GraphFixtures.ok("gamma", LoaderFamily.FABRIC),
                GraphFixtures.ok("delta", LoaderFamily.FABRIC));

        assertThat(report.cycleGroupCount()).isZero();
        assertThat(report.acyclic()).isTrue();
        assertThat(report.graph().universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(3);
    }

    @Test
    void fiveBucketsAreDisjointAndSumToHardDeclarations() {
        final CycleReport report = report(
                GraphFixtures.ok("a", LoaderFamily.FABRIC,
                        List.of("b", "minecraft", "x", "fabric", "ghost"),
                        List.of(), List.of(), List.of()),
                GraphFixtures.ok("b", LoaderFamily.FABRIC),
                GraphFixtures.ok("c", LoaderFamily.FABRIC,
                        List.of(), List.of(), List.of(), List.of("fabric")),
                GraphFixtures.ok("x", LoaderFamily.NEOFORGE));

        final DependencyGraph graph = report.graph();
        assertThat(graph.bucket(LoaderUniverse.FABRIC, GraphBucket.EDGE)).isEqualTo(1);
        assertThat(graph.bucket(LoaderUniverse.FABRIC, GraphBucket.ENVIRONMENT)).isEqualTo(1);
        assertThat(graph.bucket(LoaderUniverse.FABRIC, GraphBucket.OTHER_UNIVERSE)).isEqualTo(1);
        assertThat(graph.bucket(LoaderUniverse.FABRIC, GraphBucket.ALIAS_ONLY)).isEqualTo(1);
        assertThat(graph.bucket(LoaderUniverse.FABRIC, GraphBucket.ABSENT)).isEqualTo(1);
        assertThat(graph.hardDeclarations(LoaderUniverse.FABRIC)).isEqualTo(5);
        assertThat(graph.totalBucket(GraphBucket.EDGE)
                + graph.totalBucket(GraphBucket.ENVIRONMENT)
                + graph.totalBucket(GraphBucket.OTHER_UNIVERSE)
                + graph.totalBucket(GraphBucket.ALIAS_ONLY)
                + graph.totalBucket(GraphBucket.ABSENT))
                .isEqualTo(graph.totalHardDeclarations());
        assertThat(graph.universe(LoaderUniverse.FABRIC).edgeCount()).isEqualTo(1);
        assertThat(report.cycleGroupCount()).isZero();
    }

    @Test
    void quiltFoldsIntoFabricAndUnknownNeverBecomesANode() {
        final CycleReport report = report(
                GraphFixtures.ok("quiltmod", LoaderFamily.QUILT),
                GraphFixtures.ok("unknownmod", LoaderFamily.UNKNOWN));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(1);
        assertThat(report.graph().universe(LoaderUniverse.FORGE).nodeCount()).isZero();
        assertThat(report.graph().universe(LoaderUniverse.NEOFORGE).nodeCount()).isZero();
        assertThat(report.graph().excludedRecords()).isEqualTo(1);
    }

    @Test
    void cycleMembersAreAsciiAscending() {
        final CycleReport report = report(
                GraphFixtures.ok("zulu", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "mike"),
                GraphFixtures.ok("mike", LoaderFamily.FABRIC, "zulu"));

        assertThat(report.cycleGroups().get(0).members())
                .containsExactly("alpha", "mike", "zulu");
        assertThat(report.cycleGroups().get(0).csvMembers()).isEqualTo("alpha;mike;zulu");
    }

    @Test
    void rowsAreSortedByUniverseThenMembers() {
        final CycleReport report = report(
                GraphFixtures.ok("zeta", LoaderFamily.FABRIC, "alpha"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "zeta"),
                GraphFixtures.ok("zz", LoaderFamily.NEOFORGE, "aa"),
                GraphFixtures.ok("aa", LoaderFamily.NEOFORGE, "zz"),
                GraphFixtures.ok("gamma", LoaderFamily.NEOFORGE, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.NEOFORGE, "gamma"));

        assertThat(report.cycleGroups())
                .extracting(group -> group.universe().name() + ":" + group.csvMembers())
                .containsExactly(
                        "FABRIC:alpha;zeta",
                        "NEOFORGE:aa;zz",
                        "NEOFORGE:beta;gamma");
    }

    @Test
    void failedRecordsNeverBecomeNodes() {
        final CycleReport report = report(
                GraphFixtures.failed("broken.jar"),
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC));

        assertThat(report.graph().universe(LoaderUniverse.FABRIC).nodeCount()).isEqualTo(1);
        assertThat(report.graph().excludedRecords()).isZero();
        assertThat(report.cycleGroupCount()).isZero();
    }

    @Test
    void acyclicityAndCycleGroupCountAlwaysAgree() {
        final CycleReport cyclic = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC, "alpha"));
        final CycleReport acyclic = report(
                GraphFixtures.ok("alpha", LoaderFamily.FABRIC, "beta"),
                GraphFixtures.ok("beta", LoaderFamily.FABRIC));

        assertThat(cyclic.acyclic()).isEqualTo(cyclic.cycleGroupCount() == 0);
        assertThat(acyclic.acyclic()).isEqualTo(acyclic.cycleGroupCount() == 0);
        assertThat(cyclic.acyclic()).isFalse();
        assertThat(acyclic.acyclic()).isTrue();
    }
}
