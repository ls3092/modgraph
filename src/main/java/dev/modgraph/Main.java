package dev.modgraph;

import dev.modgraph.analysis.ConflictLevel;
import dev.modgraph.analysis.ConflictPredictor;
import dev.modgraph.analysis.ConflictReport;
import dev.modgraph.analysis.InvasivenessAnalyzer;
import dev.modgraph.analysis.ModConflict;
import dev.modgraph.analysis.TargetFrequency;
import dev.modgraph.model.FailureCode;
import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.model.SupportPolicy;
import dev.modgraph.parse.FabricMetadataParser;
import dev.modgraph.parse.NeoForgeMetadataParser;
import dev.modgraph.report.ReportWriter;
import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyReport;
import dev.modgraph.resolve.DependencyResolver;
import dev.modgraph.resolve.EnvironmentFacts;
import dev.modgraph.scan.JarScanner;
import dev.modgraph.scan.ScannedJar;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final Path FABRIC_ROOT = Path.of("fixtures", "jars", "fabric");
    private static final Path NEOFORGE_ROOT = Path.of("fixtures", "jars", "neoforge");
    private static final Path CANDIDATES_PATH = Path.of("candidates.jsonl");
    private static final Path SUMMARY_PATH = Path.of("summary.csv");
    private static final Path CONFLICTS_PATH = Path.of("conflicts.csv");
    private static final Path DEPENDENCIES_PATH = Path.of("dependencies.csv");

    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        final EnvironmentFacts environment = EnvironmentFacts.declared();
        LOG.info("环境事实：{}", environment.describe());

        final InvasivenessAnalyzer analyzer = new InvasivenessAnalyzer();
        final List<ModCandidate> fabric = parseFabric(analyzer);
        final List<ModCandidate> neoforge = parseNeoForge(analyzer);
        final List<ModCandidate> all = new ArrayList<>(fabric.size() + neoforge.size());
        all.addAll(fabric);
        all.addAll(neoforge);

        final long ok = count(all, Status.OK);
        final long failed = count(all, Status.FAILED);
        if (ok + failed != all.size()) {
            LOG.error("内部不变量失败：OK {} + FAILED {} != 输入 {}",
                    ok, failed, all.size());
            throw new IllegalStateException("OK + FAILED 必须等于输入 jar 数");
        }

        final ReportWriter reportWriter = new ReportWriter();
        reportWriter.writeCandidates(CANDIDATES_PATH, all);
        reportWriter.writeSummary(SUMMARY_PATH, all);

        final ConflictReport conflictReport = new ConflictPredictor().predict(all);
        final List<ModConflict> conflicts = conflictReport.conflicts();
        reportWriter.writeConflicts(CONFLICTS_PATH, conflicts);

        LOG.info("FABRIC: OK={} FAILED={}",
                count(fabric, Status.OK), count(fabric, Status.FAILED));
        LOG.info("NEOFORGE: OK={} FAILED={}",
                count(neoforge, Status.OK), count(neoforge, Status.FAILED));
        LOG.info("输入 jar：{} | OK：{} | FAILED：{}", all.size(), ok, failed);
        LOG.info("失败分类：{}", failureSummary(all));

        final Map<Invasiveness, Long> invasiveness = countInvasiveness(all);
        LOG.info("侵入深度：API {} | MIXIN {} | CORE {} | RENDER {}",
                invasiveness.getOrDefault(Invasiveness.API, 0L),
                invasiveness.getOrDefault(Invasiveness.MIXIN, 0L),
                invasiveness.getOrDefault(Invasiveness.CORE, 0L),
                invasiveness.getOrDefault(Invasiveness.RENDER, 0L));

        final Map<SupportPolicy, Long> support = countSupportPolicy(all);
        LOG.info("支持策略：SUPPORTED {} | DEGRADED {} | EXCLUDED {}",
                support.getOrDefault(SupportPolicy.SUPPORTED, 0L),
                support.getOrDefault(SupportPolicy.DEGRADED, 0L),
                support.getOrDefault(SupportPolicy.EXCLUDED, 0L));

        final String topTargets = conflictReport.targetFrequencies().stream()
                .limit(10)
                .map(frequency -> frequency.target() + " " + frequency.modCount())
                .collect(Collectors.joining(" | "));
        LOG.info("高频目标类：{}", topTargets);

        final List<TargetFrequency> ubiquitousTargets = conflictReport.targetFrequencies().stream()
                .filter(frequency -> frequency.modCount() >= conflictReport.ubiquitousThreshold())
                .toList();
        final String ubiquitousLine = ubiquitousTargets.stream()
                .map(frequency -> frequency.target() + " " + frequency.modCount())
                .collect(Collectors.joining(" | "));
        LOG.info("ubiquitous 阈值：{}（模组总数 {} × 10%，向上取整）",
                conflictReport.ubiquitousThreshold(), conflictReport.modCount());
        LOG.info("ubiquitous 目标类（{} 个）：{}", ubiquitousTargets.size(), ubiquitousLine);

        final Map<ConflictLevel, Long> levels = countConflictLevels(conflicts);
        LOG.info("冲突预测：HIGH {} 组 | MEDIUM {} 组 | LOW {} 组（静态推断，非运行结论）",
                levels.getOrDefault(ConflictLevel.HIGH, 0L),
                levels.getOrDefault(ConflictLevel.MEDIUM, 0L),
                levels.getOrDefault(ConflictLevel.LOW, 0L));

        final DependencyReport dependencyReport = new DependencyResolver().resolve(all, environment);
        reportWriter.writeDependencies(DEPENDENCIES_PATH, dependencyReport.results());
        LOG.info("依赖总数：硬依赖 {} | 软依赖 {} | 冲突声明 {}",
                dependencyReport.count(DepKind.REQUIRED),
                dependencyReport.count(DepKind.RECOMMENDS),
                dependencyReport.count(DepKind.BREAKS));
        LOG.info("不可满足：MISSING_ID {} | VERSION_MISMATCH {} | BREAKS_VIOLATED {} | AMBIGUOUS_ALIAS {}",
                dependencyReport.count(DependencyOutcome.MISSING_ID),
                dependencyReport.count(DependencyOutcome.VERSION_MISMATCH),
                dependencyReport.count(DependencyOutcome.BREAKS_VIOLATED),
                dependencyReport.count(DependencyOutcome.AMBIGUOUS_ALIAS));
        LOG.info("无法解析的版本区间：{}（实现缺陷，不是模组问题）",
                dependencyReport.count(DependencyOutcome.UNPARSEABLE));

        LOG.info("本结果为准入检查，不构成运行稳定性保证");
    }

    private static List<ModCandidate> parseFabric(final InvasivenessAnalyzer analyzer)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(FABRIC_ROOT);
        final FabricMetadataParser parser = new FabricMetadataParser();
        final List<ModCandidate> candidates = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            final ModCandidate candidate = analyzer.analyze(jar, parser.parse(jar));
            candidates.add(candidate);
            LOG.debug("分析 {} → modId={}, invasiveness={}, targets={}",
                    candidate.sourceFile(), candidate.modId(), candidate.invasiveness(),
                    candidate.mixinTargets().size());
        }
        return candidates;
    }

    private static List<ModCandidate> parseNeoForge(final InvasivenessAnalyzer analyzer)
            throws IOException {
        final List<ScannedJar> scanned = new JarScanner().scan(NEOFORGE_ROOT);
        final NeoForgeMetadataParser parser = new NeoForgeMetadataParser();
        final List<ModCandidate> candidates = new ArrayList<>(scanned.size());
        for (ScannedJar jar : scanned) {
            final ModCandidate candidate = analyzer.analyze(jar, parser.parse(jar));
            candidates.add(candidate);
            LOG.debug("分析 {} → modId={}, invasiveness={}, targets={}",
                    candidate.sourceFile(), candidate.modId(), candidate.invasiveness(),
                    candidate.mixinTargets().size());
        }
        return candidates;
    }

    private static String failureSummary(final List<ModCandidate> candidates) {
        final Map<FailureCode, Long> counts = new EnumMap<>(FailureCode.class);
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.FAILED) {
                counts.merge(candidate.failureCode(), 1L, Long::sum);
            }
        }
        if (counts.isEmpty()) {
            return "无";
        }
        return Arrays.stream(FailureCode.values())
                .filter(code -> counts.getOrDefault(code, 0L) > 0)
                .map(code -> code + ": " + counts.getOrDefault(code, 0L))
                .collect(Collectors.joining(" | "));
    }

    private static long count(final List<ModCandidate> candidates, final Status status) {
        return candidates.stream()
                .filter(candidate -> candidate.status() == status)
                .count();
    }

    private static Map<Invasiveness, Long> countInvasiveness(final List<ModCandidate> candidates) {
        final Map<Invasiveness, Long> result = new EnumMap<>(Invasiveness.class);
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.OK && candidate.invasiveness() != null) {
                result.merge(candidate.invasiveness(), 1L, Long::sum);
            }
        }
        return result;
    }

    private static Map<SupportPolicy, Long> countSupportPolicy(final List<ModCandidate> candidates) {
        final Map<SupportPolicy, Long> result = new EnumMap<>(SupportPolicy.class);
        for (ModCandidate candidate : candidates) {
            if (candidate.status() == Status.OK && candidate.supportPolicy() != null) {
                result.merge(candidate.supportPolicy(), 1L, Long::sum);
            }
        }
        return result;
    }

    private static Map<ConflictLevel, Long> countConflictLevels(final List<ModConflict> conflicts) {
        final Map<ConflictLevel, Long> result = new EnumMap<>(ConflictLevel.class);
        for (ModConflict conflict : conflicts) {
            result.merge(conflict.level(), 1L, Long::sum);
        }
        return result;
    }
}
