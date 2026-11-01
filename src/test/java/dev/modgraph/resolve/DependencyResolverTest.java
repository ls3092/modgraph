package dev.modgraph.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import dev.modgraph.analysis.InvasivenessAnalyzer;
import dev.modgraph.model.Constraint;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Ordering;
import dev.modgraph.model.Side;
import dev.modgraph.model.Status;
import dev.modgraph.parse.FabricMetadataParser;
import dev.modgraph.parse.NeoForgeMetadataParser;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import dev.modgraph.testsupport.DatasetRoot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private static final boolean FULL_DATASET = DatasetRoot.hasFullDataset();
    private static final Set<String> ENVIRONMENT_IDS = Set.of(
            "minecraft", "minecraft_version", "fabricloader", "fabric-loader",
            "quilt_loader", "quilt-loader", "neoforge", "forge", "java");

    private static List<ModCandidate> candidates;
    private static DependencyReport report;

    @BeforeAll
    static void resolveRealFixtures() throws IOException {
        DatasetRoot.logMode("DependencyResolverTest");
        candidates = List.copyOf(loadCandidates());
        report = new DependencyResolver().resolve(candidates, EnvironmentFacts.declared());
    }

    @Test
    void everyDeclaredDependencyAppearsExactlyOnce() {
        final List<String> expected = new ArrayList<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            for (Constraint constraint : candidate.depends()) {
                expected.add(key(candidate, DepKind.REQUIRED, constraint));
            }
            for (Constraint constraint : candidate.recommends()) {
                expected.add(key(candidate, DepKind.RECOMMENDS, constraint));
            }
            for (Constraint constraint : candidate.breaks()) {
                expected.add(key(candidate, DepKind.BREAKS, constraint));
            }
        }

        final List<String> actual = report.results().stream()
                .map(result -> result.modId() + "|" + result.version() + "|" + result.depId()
                        + "|" + result.depKind() + "|" + result.declaredRange())
                .toList();

        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(report.results()).hasSize(expected.size());
        assertThat(report.count(DepKind.REQUIRED) + report.count(DepKind.RECOMMENDS)
                + report.count(DepKind.BREAKS)).isEqualTo(expected.size());
    }

    @Test
    void noEnvironmentProvidedIdIsEverMissing() {
        final List<DependencyResult> environmentRows = report.results().stream()
                .filter(result -> ENVIRONMENT_IDS.contains(result.depId()))
                .toList();
        if (!FULL_DATASET) {
            // 内置样本没有声明任何依赖，环境 ID 行必然为 0；如实断言这一点，
            // 而不是在缺数据时让这条断言空转或跳过。
            assertThat(report.results()).isEmpty();
            return;
        }
        assertThat(environmentRows).isNotEmpty();
        for (DependencyResult result : environmentRows) {
            assertThat(result.outcome()).isNotEqualTo(DependencyOutcome.MISSING_ID);
            assertThat(result.resolvedVersion()).isNotNull();
        }
        assertThat(environmentRows).anySatisfy(result ->
                assertThat(result.depId()).isEqualTo("minecraft"));
        assertThat(environmentRows).anySatisfy(result ->
                assertThat(result.depId()).isEqualTo("neoforge"));
        assertThat(environmentRows).anySatisfy(result ->
                assertThat(result.depId()).isEqualTo("java"));
    }

    @Test
    void everyOutcomeRespectsItsDefinition() {
        for (DependencyResult result : report.results()) {
            switch (result.outcome()) {
                case MISSING_ID -> {
                    assertThat(result.resolvedVersion()).isNull();
                    assertThat(result.depKind())
                            .isIn(DepKind.REQUIRED, DepKind.RECOMMENDS);
                }
                case BREAKS_VIOLATED -> {
                    assertThat(result.depKind()).isEqualTo(DepKind.BREAKS);
                    assertThat(result.resolvedVersion()).isNotNull();
                }
                case VERSION_MISMATCH, SATISFIED -> {
                    if (result.depKind() == DepKind.BREAKS && result.resolvedVersion() == null) {
                        // breaks 的目标不在集合里：没有冲突，也不是“缺失依赖”。
                        continue;
                    }
                    assertThat(result.resolvedVersion()).isNotNull();
                }
                case UNPARSEABLE -> assertThat(result.depKind()).isNotNull();
            }
        }
    }

    @Test
    void breaksOnAbsentTargetIsSatisfiedNotMissing() {
        final List<DependencyResult> absentBreaks = report.results().stream()
                .filter(result -> result.depKind() == DepKind.BREAKS)
                .filter(result -> result.resolvedVersion() == null)
                .toList();
        assertThat(absentBreaks).allSatisfy(result -> {
            assertThat(result.outcome()).isEqualTo(DependencyOutcome.SATISFIED);
            assertThat(result.outcome()).isNotEqualTo(DependencyOutcome.MISSING_ID);
        });
        assertThat(report.results().stream()
                .filter(result -> result.depKind() == DepKind.BREAKS)
                .filter(result -> result.resolvedVersion() != null))
                .allSatisfy(result -> assertThat(result.outcome())
                        .isIn(DependencyOutcome.SATISFIED, DependencyOutcome.BREAKS_VIOLATED));
    }

    @Test
    void unparseableRangesAreNeverTreatedAsSatisfied() {
        final ModCandidate owner = syntheticCandidate("synthetic_owner", "1.0.0",
                LoaderFamily.FABRIC,
                List.of(constraint("synthetic_target", ">=${placeholder}")),
                List.of(), List.of());
        final ModCandidate target = syntheticCandidate("synthetic_target", "1.0.0",
                LoaderFamily.FABRIC, List.of(), List.of(), List.of());

        final DependencyReport synthetic = new DependencyResolver()
                .resolve(List.of(owner, target), EnvironmentFacts.declared());

        assertThat(synthetic.results()).hasSize(1);
        assertThat(synthetic.results().getFirst().outcome())
                .isEqualTo(DependencyOutcome.UNPARSEABLE);
        assertThat(synthetic.count(DependencyOutcome.SATISFIED)).isZero();
        assertThat(synthetic.count(DependencyOutcome.UNPARSEABLE)).isEqualTo(1);
    }

    @Test
    void unparseableTargetVersionIsReportedAsUnparseable() {
        final ModCandidate owner = syntheticCandidate("synthetic_owner", "1.0.0",
                LoaderFamily.NEOFORGE,
                List.of(constraint("synthetic_target", "[1.0,)")),
                List.of(), List.of());
        final ModCandidate target = syntheticCandidate("synthetic_target", "${file.jarVersion}",
                LoaderFamily.NEOFORGE, List.of(), List.of(), List.of());

        final DependencyReport synthetic = new DependencyResolver()
                .resolve(List.of(owner, target), EnvironmentFacts.declared());

        assertThat(synthetic.results().getFirst().outcome())
                .isEqualTo(DependencyOutcome.UNPARSEABLE);
        assertThat(synthetic.results().getFirst().resolvedVersion())
                .isEqualTo("${file.jarVersion}");
    }

    @Test
    void missingSoftDependencyIsClassifiedButNotFatal() {
        final ModCandidate owner = syntheticCandidate("synthetic_owner", "1.0.0",
                LoaderFamily.FABRIC, List.of(),
                List.of(new Constraint("absent_mod", List.of("*"), false, Ordering.NONE,
                        Side.UNKNOWN)),
                List.of());

        final DependencyReport synthetic = new DependencyResolver()
                .resolve(List.of(owner), EnvironmentFacts.declared());

        assertThat(synthetic.results().getFirst().outcome())
                .isEqualTo(DependencyOutcome.MISSING_ID);
        assertThat(synthetic.results().getFirst().depKind()).isEqualTo(DepKind.RECOMMENDS);
    }

    @Test
    void resolutionIsDeterministic() {
        final DependencyReport second = new DependencyResolver()
                .resolve(candidates, EnvironmentFacts.declared());
        assertThat(second.results()).isEqualTo(report.results());
    }

    @Test
    void realDatasetMilestones() {
        if (!FULL_DATASET) {
            // 内置样本（两个 BOM 元数据）没有声明任何依赖，这就是该数据集的已知构成。
            assertThat(report.results()).isEmpty();
            return;
        }

        assertThat(find("iris", "sodium", DepKind.REQUIRED).outcome())
                .as("Fabric 通配 + 构建元数据：0.8.x 必须匹配 0.8.13+mc1.21.1")
                .isEqualTo(DependencyOutcome.SATISFIED);
        assertThat(find("e4mc", "neoforge", DepKind.REQUIRED).outcome())
                .as("NeoForge 的 * 表示不限制版本")
                .isEqualTo(DependencyOutcome.SATISFIED);
        assertThat(find("indium", "sodium", DepKind.REQUIRED).outcome())
                .as("indium 要求 sodium 0.5.11，数据集里是 0.8.13")
                .isEqualTo(DependencyOutcome.VERSION_MISMATCH);
        final DependencyResult breaks = find("iris", "embeddium", DepKind.BREAKS);
        assertThat(breaks.outcome()).isEqualTo(DependencyOutcome.BREAKS_VIOLATED);
        assertThat(breaks.resolvedVersion()).isEqualTo("1.0.15+mc1.21.1");
        assertThat(find("biomesoplenty", "terrablender", DepKind.REQUIRED).outcome())
                .as("目标版本是 ${file.jarVersion}，但同一 modId 的另一个变体有真实版本")
                .isEqualTo(DependencyOutcome.SATISFIED);

        final List<DependencyResult> sodiumMinecraft = report.results().stream()
                .filter(result -> "sodium".equals(result.modId()))
                .filter(result -> "minecraft".equals(result.depId()))
                .toList();
        assertThat(sodiumMinecraft).isNotEmpty();
        assertThat(sodiumMinecraft).allSatisfy(result ->
                assertThat(result.outcome()).isEqualTo(DependencyOutcome.SATISFIED));
        // Fabric 变体写的是数组 ["1.21","1.21.1"]，取值按“任一满足”处理，原串用 ; 连接。
        assertThat(sodiumMinecraft).anySatisfy(result ->
                assertThat(result.declaredRange()).isEqualTo("1.21;1.21.1"));
    }

    private static DependencyResult find(final String modId, final String depId,
            final DepKind kind) {
        return report.results().stream()
                .filter(result -> result.modId().equals(modId))
                .filter(result -> result.depId().equals(depId))
                .filter(result -> result.depKind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "未找到依赖行：" + modId + " -> " + depId + " (" + kind + ")"));
    }

    private static String key(final ModCandidate candidate, final DepKind kind,
            final Constraint constraint) {
        return candidate.modId() + "|" + candidate.version() + "|" + constraint.targetId()
                + "|" + kind + "|" + String.join(";", constraint.versionRanges());
    }

    private static Constraint constraint(final String targetId, final String range) {
        return new Constraint(targetId, List.of(range), true, Ordering.NONE, Side.UNKNOWN);
    }

    private static ModCandidate syntheticCandidate(final String modId, final String version,
            final LoaderFamily family, final List<Constraint> depends,
            final List<Constraint> recommends, final List<Constraint> breaks) {
        return new ModCandidate(
                modId + ".jar",
                "0".repeat(64),
                family,
                Status.OK,
                modId,
                version,
                modId,
                Side.BOTH,
                depends,
                recommends,
                breaks,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                List.of());
    }

    private static List<ModCandidate> loadCandidates() throws IOException {
        final InvasivenessAnalyzer analyzer = new InvasivenessAnalyzer();
        final FabricMetadataParser fabric = new FabricMetadataParser();
        final NeoForgeMetadataParser neoforge = new NeoForgeMetadataParser();
        final List<ModCandidate> all = new ArrayList<>();
        if (FULL_DATASET) {
            for (ScannedJar jar : new JarScanner().scan(DatasetRoot.fabricRoot())) {
                all.add(analyzer.analyze(jar, fabric.parse(jar)));
            }
            for (ScannedJar jar : new JarScanner().scan(DatasetRoot.neoforgeRoot())) {
                all.add(analyzer.analyze(jar, neoforge.parse(jar)));
            }
        } else {
            for (ScannedJar jar : new JarScanner().scan(DatasetRoot.root())) {
                final ModCandidate parsed = jar.entryNames().contains("fabric.mod.json")
                        ? fabric.parse(jar) : neoforge.parse(jar);
                all.add(analyzer.analyze(jar, parsed));
            }
        }
        return all;
    }
}
