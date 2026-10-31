package dev.modgraph.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.model.SupportPolicy;
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

class DescriptorAnalysisTest {

    private static final boolean FULL_DATASET = DatasetRoot.hasFullDataset();
    private static final Path FABRIC_ROOT = DatasetRoot.fabricRoot();
    private static final Path NEOFORGE_ROOT = DatasetRoot.neoforgeRoot();

    private static List<ModCandidate> candidates;
    private static ConflictReport conflictReport;
    private static List<ModConflict> conflicts;

    @BeforeAll
    static void analyzeRealFixtures() throws IOException {
        DatasetRoot.logMode("DescriptorAnalysisTest");
        final InvasivenessAnalyzer analyzer = new InvasivenessAnalyzer();
        final List<ModCandidate> all = new ArrayList<>();
        if (FULL_DATASET) {
            all.addAll(parseFabric(analyzer));
            all.addAll(parseNeoForge(analyzer));
        } else {
            all.addAll(parseBundled(analyzer));
        }
        candidates = List.copyOf(all);
        conflictReport = new ConflictPredictor().predict(candidates);
        conflicts = conflictReport.conflicts();
    }

    @Test
    void allJarsProduceExactlyOneCandidate() {
        assertThat(candidates).isNotEmpty();
        final Set<String> sourceFiles = new HashSet<>();
        for (ModCandidate candidate : candidates) {
            assertThat(sourceFiles.add(candidate.sourceFile())).isTrue();
        }
        final long ok = candidates.stream().filter(c -> c.status() == Status.OK).count();
        final long failed = candidates.stream().filter(c -> c.status() == Status.FAILED).count();
        assertThat(ok + failed).isEqualTo(candidates.size());
        assertThat(failed).isEqualTo(candidates.stream()
                .filter(c -> c.failureCode() != null)
                .count());
    }

    @Test
    void analysisIsPresentForEveryOkCandidate() {
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.OK) {
                assertThat(candidate.invasiveness()).isNotNull();
                assertThat(candidate.supportPolicy()).isNotNull();
                assertThat(candidate.invasivenessSignals()).isNotNull();
                assertThat(candidate.mixinTargets()).isNotNull();
                assertThat(candidate.mixinTargetsInferred()).isTrue();
            } else {
                assertThat(candidate.invasiveness()).isNull();
                assertThat(candidate.supportPolicy()).isNull();
                assertThat(candidate.mixinTargetsInferred()).isFalse();
            }
        }
    }

    @Test
    void invasivenessDistributionCoversAllOkCandidates() {
        final Map<Invasiveness, Long> counts = new EnumMap<>(Invasiveness.class);
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.OK) {
                counts.merge(candidate.invasiveness(), 1L, Long::sum);
            }
        }
        final long ok = candidates.stream().filter(c -> c.status() == Status.OK).count();
        assertThat(counts.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(ok);
        if (FULL_DATASET) {
            assertThat(counts.getOrDefault(Invasiveness.RENDER, 0L)).isPositive();
            assertThat(counts.getOrDefault(Invasiveness.CORE, 0L)).isPositive();
            assertThat(counts.getOrDefault(Invasiveness.MIXIN, 0L)).isPositive();
            assertThat(counts.getOrDefault(Invasiveness.API, 0L)).isPositive();
        } else {
            // 内置样本只有两个 BOM 模组，均为纯 API 级；断言这一已知构成，
            // 而不是在缺数据时假装四档都出现过。
            assertThat(counts.keySet()).containsExactly(Invasiveness.API);
        }
    }

    @Test
    void renderAndCoreCandidatesAreExcludedAndHaveSignals() {
        final List<ModCandidate> deep = candidates.stream()
                .filter(c -> c.status() == Status.OK)
                .filter(c -> c.invasiveness() == Invasiveness.RENDER
                        || c.invasiveness() == Invasiveness.CORE)
                .toList();
        final long excluded = candidates.stream()
                .filter(c -> c.status() == Status.OK)
                .filter(c -> c.supportPolicy() == SupportPolicy.EXCLUDED)
                .count();
        if (FULL_DATASET) {
            assertThat(deep).isNotEmpty();
            for (ModCandidate candidate : deep) {
                assertThat(candidate.supportPolicy()).isEqualTo(SupportPolicy.EXCLUDED);
                assertThat(candidate.invasivenessSignals()).isNotEmpty();
            }
            assertThat(excluded).isGreaterThanOrEqualTo(deep.size());
        } else {
            assertThat(deep).isEmpty();
            assertThat(excluded).isZero();
        }
    }

    @Test
    void mixinTargetsAreSimpleInferredNames() {
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            for (String target : candidate.mixinTargets()) {
                assertThat(target).doesNotContain(".");
            }
        }

        if (FULL_DATASET) {
            assertThat(candidates.stream()
                    .filter(c -> c.status() == Status.OK)
                    .anyMatch(c -> !c.mixinTargets().isEmpty())).isTrue();

            final ModCandidate sodium = candidates.stream()
                    .filter(c -> "sodium-neoforge-0.8.13+mc1.21.1.jar".equals(c.sourceFile()))
                    .findFirst()
                    .orElseThrow();
            assertThat(sodium.mixinTargets()).contains("GameRenderer", "Minecraft");
            assertThat(sodium.mixinTargetsInferred()).isTrue();

            final ModCandidate carryon = candidates.stream()
                    .filter(c -> "carryon.mixins.json".equals(c.mixinConfigs().stream()
                            .filter(config -> config.endsWith("carryon.mixins.json"))
                            .findFirst()
                            .orElse(null)))
                    .findFirst()
                    .orElseThrow();
            assertThat(carryon.mixinTargets()).isNotEmpty();
        } else {
            // 内置样本没有任何 mixin 配置，目标集合必须全空；两个 BOM 模组仍然
            // 必须有 invasiveness（见 analysisIsPresentForEveryOkCandidate）。
            assertThat(candidates.stream()
                    .filter(c -> c.status() == Status.OK)
                    .allMatch(c -> c.mixinTargets().isEmpty())).isTrue();
        }
    }

    @Test
    void nestedMixinClassNamesAreStrippedSegmentBySegment() {
        if (!FULL_DATASET) {
            // 嵌套类样本（sodium）只在完整数据集里；内置样本没有 mixin 配置，
            // 目标集合为空本身就是这条规则无法被触发的证据，不允许静默跳过。
            assertThat(candidates.stream()
                    .filter(c -> c.status() == Status.OK)
                    .flatMap(c -> c.mixinTargets().stream())
                    .toList())
                    .isEmpty();
            return;
        }
        final List<ModCandidate> sodiumCandidates = candidates.stream()
                .filter(candidate -> candidate.status() == Status.OK)
                .filter(candidate -> "sodium".equals(candidate.modId()))
                .toList();
        assertThat(sodiumCandidates).isNotEmpty();
        for (ModCandidate sodium : sodiumCandidates) {
            assertThat(sodium.mixinTargets())
                    .contains("VertexMultiConsumer$Double", "VertexMultiConsumer$Multiple")
                    .doesNotContain("VertexMultiConsumerMixin$Double",
                            "VertexMultiConsumerMixin$Multiple");
            final List<String> residualMixinNames = sodium.mixinTargets().stream()
                    .filter(target -> target.contains("Mixin"))
                    .toList();
            assertThat(residualMixinNames).isEmpty();
        }
    }

    @Test
    void conflictsAreClassifiedConsistently() {
        final Set<String> okIds = new HashSet<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.OK) {
                okIds.add(candidate.modId());
            }
        }
        final long expectedPairs = (long) okIds.size() * (okIds.size() - 1L) / 2L;
        assertThat(conflicts).hasSize((int) expectedPairs);
        assertThat(conflictReport.modCount()).isEqualTo(okIds.size());
        assertThat(conflictReport.totalPairs()).isEqualTo((int) expectedPairs);
        assertThat(conflictReport.ubiquitousThreshold()).isEqualTo((okIds.size() + 9) / 10);

        final Set<String> ubiquitous = new HashSet<>();
        for (TargetFrequency frequency : conflictReport.targetFrequencies()) {
            if (frequency.modCount() >= conflictReport.ubiquitousThreshold()) {
                ubiquitous.add(frequency.target());
            }
        }

        final Map<ConflictLevel, Long> levels = new EnumMap<>(ConflictLevel.class);
        for (ModConflict conflict : conflicts) {
            levels.merge(conflict.level(), 1L, Long::sum);
            if (!conflict.sharedTargets().isEmpty()) {
                assertThat(conflict.level()).isIn(ConflictLevel.HIGH, ConflictLevel.MEDIUM);
                final boolean hasRareTarget = conflict.sharedTargets().stream()
                        .anyMatch(target -> !ubiquitous.contains(target));
                final boolean hasUbiquitousTarget = conflict.sharedTargets().stream()
                        .anyMatch(ubiquitous::contains);
                if (conflict.level() == ConflictLevel.HIGH) {
                    assertThat(hasRareTarget).isTrue();
                    assertThat(hasUbiquitousTarget).isFalse();
                } else {
                    assertThat(hasUbiquitousTarget).isTrue();
                }
            } else {
                assertThat(conflict.level())
                        .isIn(ConflictLevel.MEDIUM, ConflictLevel.LOW, ConflictLevel.NONE);
            }
        }
        if (FULL_DATASET) {
            assertThat(levels.getOrDefault(ConflictLevel.HIGH, 0L)).isPositive();
            assertThat(levels.getOrDefault(ConflictLevel.MEDIUM, 0L)).isPositive();
            assertThat(levels.getOrDefault(ConflictLevel.LOW, 0L)).isPositive();
            assertThat(levels.getOrDefault(ConflictLevel.NONE, 0L)).isPositive();
        } else {
            // 两个 BOM 模组没有共享目标，必然全部落在 NONE。
            assertThat(levels.getOrDefault(ConflictLevel.NONE, 0L)).isEqualTo(expectedPairs);
        }

        final long high = levels.getOrDefault(ConflictLevel.HIGH, 0L);
        assertThat(high * 100L).isLessThanOrEqualTo(expectedPairs * 5L);
    }

    @Test
    void mixinTargetCoverageAndPrefixStyleAreCorrect() {
        final List<ModCandidate> okCandidates = candidates.stream()
                .filter(candidate -> candidate.status() == Status.OK)
                .toList();
        final long gap = okCandidates.stream()
                .filter(candidate -> !candidate.mixinConfigs().isEmpty())
                .filter(candidate -> candidate.mixinTargets().isEmpty())
                .count();
        assertThat(gap * 100L).isLessThanOrEqualTo(okCandidates.size() * 10L);

        if (FULL_DATASET) {
            final ModCandidate biomesOPlenty = candidates.stream()
                    .filter(candidate -> "BiomesOPlenty-fabric-1.21.1-21.1.0.14.jar"
                            .equals(candidate.sourceFile()))
                    .findFirst()
                    .orElseThrow();
            assertThat(biomesOPlenty.mixinTargets())
                    .contains("BloodFluid", "LiquidNullFluid")
                    .doesNotContain("MixinBloodFluid", "MixinLiquidNullFluid");
            assertThat(biomesOPlenty.mixinTargetsInferred()).isTrue();
        } else {
            // 内置样本没有声明 mixin 配置，缺口必须为 0。
            assertThat(gap).isZero();
        }

        assertThat(okCandidates.stream()
                .flatMap(candidate -> candidate.mixinTargets().stream()))
                .doesNotContain("MixinGameRenderer");
    }

    @Test
    void highFrequencyTargetsAreRanked() {
        if (!FULL_DATASET) {
            assertThat(conflictReport.targetFrequencies()).isEmpty();
            assertThat(conflictReport.ubiquitousThreshold())
                    .isEqualTo((conflictReport.modCount() + 9) / 10);
            return;
        }
        assertThat(conflictReport.targetFrequencies()).isNotEmpty();
        assertThat(conflictReport.targetFrequencies())
                .allSatisfy(frequency -> {
                    assertThat(frequency.target()).doesNotContain(".");
                    assertThat(frequency.modCount()).isPositive();
                    assertThat(frequency.modCount())
                            .isLessThanOrEqualTo(conflictReport.modCount());
                });

        final List<TargetFrequency> top = conflictReport.targetFrequencies()
                .stream()
                .limit(10)
                .toList();
        assertThat(top).hasSize(Math.min(10, conflictReport.targetFrequencies().size()));
        for (int i = 1; i < top.size(); i++) {
            assertThat(top.get(i - 1).modCount())
                    .isGreaterThanOrEqualTo(top.get(i).modCount());
        }
        assertThat(top.get(0).modCount()).isPositive();
        assertThat(conflictReport.ubiquitousThreshold())
                .isEqualTo((conflictReport.modCount() + 9) / 10);
        assertThat(conflictReport.targetFrequencies())
                .anyMatch(frequency -> frequency.modCount()
                        >= conflictReport.ubiquitousThreshold());
        assertThat(top.get(0).modCount())
                .isLessThanOrEqualTo(conflictReport.modCount());
    }

    @Test
    void reportsContainAllCandidatesAndConflicts(@TempDir final Path tempDir) throws IOException {
        final ReportWriter writer = new ReportWriter();
        final Path candidatesPath = tempDir.resolve("candidates.jsonl");
        final Path summaryPath = tempDir.resolve("summary.csv");
        final Path conflictsPath = tempDir.resolve("conflicts.csv");
        writer.writeCandidates(candidatesPath, candidates);
        writer.writeSummary(summaryPath, candidates);
        writer.writeConflicts(conflictsPath, conflicts);

        final List<String> jsonLines = Files.readAllLines(candidatesPath);
        assertThat(jsonLines).hasSize(candidates.size());
        for (String line : jsonLines) {
            final JsonObject object = JsonParser.parseString(line).getAsJsonObject();
            final boolean inferred = object.get("mixinTargetsInferred").getAsBoolean();
            if ("OK".equals(object.get("status").getAsString())) {
                assertThat(inferred).isTrue();
            } else {
                assertThat(inferred).isFalse();
            }
        }

        final List<String> summaryLines = Files.readAllLines(summaryPath);
        assertThat(summaryLines).hasSize(candidates.size() + 1);
        assertThat(summaryLines.get(0)).isEqualTo(
                "sourceFile,sha256,loaderFamily,status,modId,version,modType,"
                        + "invasiveness,supportPolicy,failureCode");
        final long excluded = summaryLines.stream()
                .skip(1)
                .map(line -> line.split(",", -1)[8])
                .filter("EXCLUDED"::equals)
                .count();
        assertThat(excluded).isEqualTo(candidates.stream()
                .filter(c -> c.supportPolicy() == SupportPolicy.EXCLUDED)
                .count());

        final List<String> conflictLines = Files.readAllLines(conflictsPath);
        assertThat(conflictLines).hasSize(conflicts.size() + 1);
        assertThat(conflictLines.get(0)).isEqualTo("modA,modB,level,sharedTargets");
        final long highLines = conflictLines.stream()
                .skip(1)
                .map(line -> line.split(",", -1)[2])
                .filter("HIGH"::equals)
                .count();
        assertThat(highLines).isEqualTo(conflicts.stream()
                .filter(c -> c.level() == ConflictLevel.HIGH)
                .count());
    }

    private static List<ModCandidate> parseFabric(final InvasivenessAnalyzer analyzer)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser parser = new FabricMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(analyzer.analyze(jar, parser.parse(jar)));
        }
        return result;
    }

    private static List<ModCandidate> parseNeoForge(final InvasivenessAnalyzer analyzer)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(NEOFORGE_ROOT);
        final NeoForgeMetadataParser parser = new NeoForgeMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            result.add(analyzer.analyze(jar, parser.parse(jar)));
        }
        return result;
    }

    /** 内置样本是扁平目录、Fabric 与 NeoForge 混放，按 jar 内元数据选择解析器。 */
    private static List<ModCandidate> parseBundled(final InvasivenessAnalyzer analyzer)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser fabric = new FabricMetadataParser();
        final NeoForgeMetadataParser neoforge = new NeoForgeMetadataParser();
        final List<ModCandidate> result = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            final ModCandidate parsed = jar.entryNames().contains("fabric.mod.json")
                    ? fabric.parse(jar)
                    : neoforge.parse(jar);
            result.add(analyzer.analyze(jar, parsed));
        }
        return result;
    }
}
