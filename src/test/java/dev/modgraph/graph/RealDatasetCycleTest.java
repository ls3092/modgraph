package dev.modgraph.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.parse.FabricMetadataParser;
import dev.modgraph.parse.NeoForgeMetadataParser;
import dev.modgraph.report.ReportWriter;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import dev.modgraph.testsupport.DatasetRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 真实数据集上的 M1 不变量（SPEC §3.6.7 第 4、6、8、9 条）。
 *
 * <p>断言只依赖“文件系统真实列举出来的记录”和结构等式，不写死任何 jar 数量或环组数；
 * 内置样本模式下同样全部执行，不跳过。</p>
 */
class RealDatasetCycleTest {

    private static final boolean FULL_DATASET = DatasetRoot.hasFullDataset();
    private static final Path FABRIC_ROOT = DatasetRoot.fabricRoot();
    private static final Path NEOFORGE_ROOT = DatasetRoot.neoforgeRoot();

    private static List<ModCandidate> candidates;
    private static CycleReport cycleReport;

    @BeforeAll
    static void buildGraphFromRealDataset() throws IOException {
        DatasetRoot.logMode("RealDatasetCycleTest");
        final List<ModCandidate> all = new ArrayList<>();
        if (FULL_DATASET) {
            all.addAll(parseFabric());
            all.addAll(parseNeoForge());
        } else {
            all.addAll(parseBundled());
        }
        candidates = List.copyOf(all);
        cycleReport = new CycleDetector().detect(new GraphBuilder().build(candidates));
    }

    @Test
    void everyInputFileProducedExactlyOneRecord() {
        final long ok = candidates.stream().filter(c -> c.status() == Status.OK).count();
        final long failed = candidates.stream().filter(c -> c.status() == Status.FAILED).count();
        assertThat(ok + failed).isEqualTo(candidates.size());
        assertThat(candidates).isNotEmpty();
    }

    @Test
    void bucketEquationHoldsOnTheRealDataset() {
        final DependencyGraph graph = cycleReport.graph();
        final Map<LoaderUniverse, Integer> expected = countHardDeclarations();
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            int sum = 0;
            for (GraphBucket bucket : GraphBucket.values()) {
                sum += graph.bucket(universe, bucket);
            }
            assertThat(sum).isEqualTo(graph.hardDeclarations(universe));
            assertThat(graph.hardDeclarations(universe))
                    .isEqualTo(expected.getOrDefault(universe, 0));
        }

        int sum = 0;
        for (GraphBucket bucket : GraphBucket.values()) {
            sum += graph.totalBucket(bucket);
        }
        assertThat(sum).isEqualTo(graph.totalHardDeclarations());
        assertThat(graph.totalHardDeclarations())
                .isEqualTo(expected.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void nodeCountsMatchDistinctModIdsPerUniverse() {
        final Map<LoaderUniverse, Set<String>> distinct = new EnumMap<>(LoaderUniverse.class);
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            distinct.put(universe, new HashSet<>());
        }
        int excluded = 0;
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final var universe = LoaderUniverse.of(candidate.loaderFamily());
            if (universe.isEmpty()) {
                excluded++;
                continue;
            }
            distinct.get(universe.get()).add(candidate.modId());
        }

        for (LoaderUniverse universe : LoaderUniverse.values()) {
            assertThat(cycleReport.graph().universe(universe).nodeCount())
                    .isEqualTo(distinct.get(universe).size());
        }
        assertThat(cycleReport.graph().excludedRecords()).isEqualTo(excluded);
    }

    @Test
    void topologicalSortAndCycleGroupsAlwaysAgree() {
        assertThat(cycleReport.acyclic())
                .isEqualTo(cycleReport.cycleGroupCount() == 0);
        for (CycleGroup group : cycleReport.cycleGroups()) {
            assertThat(group.universe()).isNotNull();
            assertThat(group.members()).isNotEmpty();
            assertThat(group.members()).isSorted();
            assertThat(group.memberCount()).isEqualTo(group.members().size());
            final UniverseGraph universe = cycleReport.graph().universe(group.universe());
            for (String member : group.members()) {
                assertThat(universe.hasNode(member)).isTrue();
            }
            if (group.memberCount() == 1) {
                final String only = group.members().get(0);
                assertThat(universe.successors(only))
                        .as("单成员环组必须是自环：%s", only)
                        .contains(only);
            }
        }
    }

    @Test
    void edgesNeverExceedEdgeDeclarations() {
        for (LoaderUniverse universe : LoaderUniverse.values()) {
            assertThat(cycleReport.graph().universe(universe).edgeCount())
                    .isLessThanOrEqualTo(
                            cycleReport.graph().bucket(universe, GraphBucket.EDGE));
        }
    }

    @Test
    void cyclesCsvRowCountMatchesCycleGroups(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("cycles.csv");
        new ReportWriter().writeCycles(file, cycleReport.cycleGroups());

        final List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(cycleReport.cycleGroupCount() + 1);
        assertThat(lines.get(0)).isEqualTo("universe,memberCount,members");
        for (String line : lines.subList(1, lines.size())) {
            assertThat(line.split(",", -1)).hasSize(3);
        }
    }

    private static Map<LoaderUniverse, Integer> countHardDeclarations() {
        final Map<LoaderUniverse, Integer> counts = new EnumMap<>(LoaderUniverse.class);
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final var universe = LoaderUniverse.of(candidate.loaderFamily());
            universe.ifPresent(value -> counts.merge(value, candidate.depends().size(), Integer::sum));
        }
        return counts;
    }

    private static List<ModCandidate> parseFabric() throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser parser = new FabricMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(parser.parse(jar));
        }
        return result;
    }

    private static List<ModCandidate> parseNeoForge() throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(NEOFORGE_ROOT);
        final NeoForgeMetadataParser parser = new NeoForgeMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(parser.parse(jar));
        }
        return result;
    }

    /** 内置样本是扁平目录，按 jar 内元数据选择解析器。 */
    private static List<ModCandidate> parseBundled() throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser fabric = new FabricMetadataParser();
        final NeoForgeMetadataParser neoforge = new NeoForgeMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(jar.entryNames().contains("fabric.mod.json")
                    ? fabric.parse(jar)
                    : neoforge.parse(jar));
        }
        return result;
    }
}
