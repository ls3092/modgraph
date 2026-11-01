package dev.modgraph.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.graph.LoaderUniverse;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.parse.FabricMetadataParser;
import dev.modgraph.parse.NeoForgeMetadataParser;
import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyReport;
import dev.modgraph.resolve.DependencyResolver;
import dev.modgraph.resolve.EnvironmentFacts;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import dev.modgraph.testsupport.DatasetRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 真实数据集上的 M1.5 不变量（SPEC §3.7.5）。
 *
 * <p>所有期望值都由记录与依赖行独立重算，不写死任何数量；内置样本模式下同样全部执行。</p>
 */
class RealDatasetSummaryTest {

    private static final boolean FULL_DATASET = DatasetRoot.hasFullDataset();
    private static final Path FABRIC_ROOT = DatasetRoot.fabricRoot();
    private static final Path NEOFORGE_ROOT = DatasetRoot.neoforgeRoot();

    private static List<ModCandidate> candidates;
    private static DependencyReport dependencies;
    private static ReportSummary summary;

    @BeforeAll
    static void analyzeRealDataset() throws IOException {
        DatasetRoot.logMode("RealDatasetSummaryTest");
        final List<ModCandidate> all = new ArrayList<>();
        if (FULL_DATASET) {
            all.addAll(parse(FABRIC_ROOT, new FabricMetadataParser()::parse));
            all.addAll(parse(NEOFORGE_ROOT, new NeoForgeMetadataParser()::parse));
        } else {
            all.addAll(parseBundled());
        }
        candidates = List.copyOf(all);
        dependencies = new DependencyResolver().resolve(candidates, EnvironmentFacts.declared());
        summary = ReportSummary.of(candidates, dependencies);
    }

    @Test
    void unsatisfiedRequiredEqualsAnIndependentRecount() {
        final UnsatisfiedRequired result = summary.unsatisfiedRequired();

        assertThat(result.requiredMissingId())
                .isEqualTo(count(DepKind.REQUIRED, DependencyOutcome.MISSING_ID));
        assertThat(result.requiredVersionMismatch())
                .isEqualTo(count(DepKind.REQUIRED, DependencyOutcome.VERSION_MISMATCH));
        assertThat(result.ambiguousAlias())
                .isEqualTo(count(DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS));
        assertThat(result.breaksViolated())
                .isEqualTo(count(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED));
        assertThat(result.softMissingId())
                .isEqualTo(count(DepKind.RECOMMENDS, DependencyOutcome.MISSING_ID));
        assertThat(result.softVersionMismatch())
                .isEqualTo(count(DepKind.RECOMMENDS, DependencyOutcome.VERSION_MISMATCH));
        assertThat(result.unparseable())
                .isEqualTo((int) dependencies.count(DependencyOutcome.UNPARSEABLE));

        assertThat(result.count())
                .isEqualTo(result.requiredMissingId() + result.requiredVersionMismatch());
        assertThat(dependencies.count(DependencyOutcome.SATISFIED)
                + result.count() + result.excludedCount())
                .isEqualTo(dependencies.results().size());
    }

    @Test
    void duplicateGroupsEqualAnIndependentGrouping() {
        final Map<String, List<String>> expected = recordsByUniverseAndModId();
        final List<DuplicateIdGroup> groups = summary.duplicateIds().groups();

        final List<String> expectedKeys = new ArrayList<>();
        expected.forEach((key, versions) -> {
            if (versions.size() >= 2) {
                expectedKeys.add(key);
            }
        });
        assertThat(groups).hasSize(expectedKeys.size());

        int expectedPairs = 0;
        for (DuplicateIdGroup group : groups) {
            final String key = group.universe() + "|" + group.modId();
            final List<String> versions = expected.get(key);
            assertThat(versions).as("重复组必须来自同一宇宙的记录：%s", key).isNotNull();
            assertThat(group.recordCount()).isEqualTo(versions.size());
            assertThat(group.versions()).containsExactlyElementsOf(new TreeSet<>(versions));
            assertThat(group.relation()).isEqualTo(
                    new TreeSet<>(versions).size() == 1
                            ? VersionRelation.SAME_VERSION
                            : VersionRelation.DIFFERENT_VERSION);
            assertThat(group.pairCount())
                    .isEqualTo(group.recordCount() * (group.recordCount() - 1) / 2);
            expectedPairs += group.pairCount();
        }
        assertThat(summary.duplicateIds().pairCount()).isEqualTo(expectedPairs);
        assertThat(summary.duplicateIds().sameVersionPairs()
                + summary.duplicateIds().differentVersionPairs()).isEqualTo(expectedPairs);
    }

    @Test
    void crossUniverseVariantsAreNeverReportedAsDuplicates() {
        final Map<String, List<String>> records = recordsByUniverseAndModId();
        final Map<String, Map<LoaderUniverse, Integer>> perUniverse = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            final String[] parts = entry.getKey().split("\\|", 2);
            perUniverse.computeIfAbsent(parts[1], modId -> new EnumMap<>(LoaderUniverse.class))
                    .merge(LoaderUniverse.valueOf(parts[0]), entry.getValue().size(), Integer::sum);
        }

        final Set<String> reported = new TreeSet<>();
        for (DuplicateIdGroup group : summary.duplicateIds().groups()) {
            reported.add(group.universe() + "|" + group.modId());
        }

        for (Map.Entry<String, Map<LoaderUniverse, Integer>> entry : perUniverse.entrySet()) {
            final Map<LoaderUniverse, Integer> counts = entry.getValue();
            if (counts.size() > 1 && counts.values().stream().allMatch(value -> value == 1)) {
                for (LoaderUniverse universe : counts.keySet()) {
                    assertThat(reported)
                            .as("跨宇宙各一条的 %s 不得被当成重复：%s", entry.getKey(), universe)
                            .doesNotContain(universe + "|" + entry.getKey());
                }
            }
        }
    }

    @Test
    void duplicatesCsvMatchesTheReport(@TempDir final Path tempDir) throws IOException {
        final Path file = tempDir.resolve("duplicates.csv");
        new ReportWriter().writeDuplicates(file, summary.duplicateIds());

        final List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(summary.duplicateIds().groupCount() + 1);
        assertThat(lines.get(0)).isEqualTo("universe,modId,recordCount,pairCount,relation,versions");
        for (String line : lines.subList(1, lines.size())) {
            final String[] columns = line.split(",", -1);
            assertThat(columns).hasSize(6);
            final int recordCount = Integer.parseInt(columns[2]);
            assertThat(Integer.parseInt(columns[3])).isEqualTo(recordCount * (recordCount - 1) / 2);
            assertThat(columns[4]).isIn("SAME_VERSION", "DIFFERENT_VERSION");
        }
    }

    private static int count(final DepKind kind, final DependencyOutcome outcome) {
        return Math.toIntExact(dependencies.count(kind, outcome));
    }

    /** key = {@code UNIVERSE|modId}，value = 该宇宙内该 modId 全部 OK 记录的版本原串（不去重）。 */
    private static Map<String, List<String>> recordsByUniverseAndModId() {
        final Map<String, List<String>> grouped = new TreeMap<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final Optional<LoaderUniverse> universe = LoaderUniverse.of(candidate.loaderFamily());
            if (universe.isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(universe.get() + "|" + candidate.modId(),
                    key -> new ArrayList<>()).add(candidate.version());
        }
        return grouped;
    }

    private static List<ModCandidate> parse(final Path root,
                                            final Function<ScannedJar, ModCandidate> parser)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(root);
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(parser.apply(jar));
        }
        return result;
    }

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
