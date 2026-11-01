package dev.modgraph.resolve;

import dev.modgraph.model.Constraint;
import dev.modgraph.model.LoaderFamily;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import dev.modgraph.version.FabricVersionRange;
import dev.modgraph.version.NeoForgeVersionRange;
import dev.modgraph.version.Version;
import dev.modgraph.version.VersionParseException;
import dev.modgraph.version.VersionRange;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M0.5 依赖可满足性判定（SPEC §3.5）。
 *
 * <p>只做一件事：对每条已登记的依赖声明回答“本模组集合是否满足它”，产出 §3.5.5 的五种结果之一。
 * 不排序、不求解、不选版本、不下载，也不改动 M0 的 {@code conflicts.csv}。</p>
 *
 * <p>解析口径（全部写在代码里，可审计）：</p>
 * <ul>
 *   <li>目标存在性只看两项：数据集里 {@code status=OK} 记录的 {@code modId}，以及
 *       {@link EnvironmentFacts} 声明的环境 ID。{@code provides} 声明的能力 ID 不参与解析——
 *       SPEC §3.5.5 只列了这两种来源，扩展它属于改规格。</li>
 *   <li>同一 {@code modId} 有多个加载器变体（例如 sodium 的 Fabric / NeoForge 版本）时，
 *       按“任一存在的版本满足区间即 SATISFIED”判定，{@code resolvedVersion} 优先取实际匹配的那个版本。</li>
 *   <li>{@code Constraint.versionRanges} 有多条原串时按“任一满足”处理（Fabric 的真实写法
 *       {@code ["1.21","1.21.1"]} 表示支持其中任一版本）。</li>
 *   <li>{@code breaks} 的目标不存在记为 SATISFIED：没有目标就不构成冲突。</li>
 * </ul>
 */
public final class DependencyResolver {

    private static final Logger LOG = LoggerFactory.getLogger(DependencyResolver.class);
    private static final VersionRange ALWAYS = version -> true;

    public DependencyReport resolve(final List<ModCandidate> candidates,
            final EnvironmentFacts environment) {
        final Map<String, List<String>> versionsById = indexVersions(candidates);
        final List<DependencyResult> results = new ArrayList<>();

        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            for (Constraint constraint : candidate.depends()) {
                results.add(evaluate(candidate, DepKind.REQUIRED, constraint,
                        versionsById, environment));
            }
            for (Constraint constraint : candidate.recommends()) {
                results.add(evaluate(candidate, DepKind.RECOMMENDS, constraint,
                        versionsById, environment));
            }
            for (Constraint constraint : candidate.breaks()) {
                results.add(evaluate(candidate, DepKind.BREAKS, constraint,
                        versionsById, environment));
            }
        }
        return new DependencyReport(results);
    }

    private DependencyResult evaluate(final ModCandidate owner, final DepKind kind,
            final Constraint constraint, final Map<String, List<String>> versionsById,
            final EnvironmentFacts environment) {
        final String declaredRange = String.join(";", constraint.versionRanges());
        final List<String> found = environment.versionOf(constraint.targetId())
                .map(List::of)
                .orElseGet(() -> versionsById.getOrDefault(constraint.targetId(), List.of()));

        if (found.isEmpty()) {
            if (kind == DepKind.BREAKS) {
                // breaks 的目标不存在等于没有冲突；这不是“缺失依赖”，不能记成 MISSING_ID。
                return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(),
                        kind, declaredRange, null, DependencyOutcome.SATISFIED);
            }
            if (kind == DepKind.RECOMMENDS) {
                // SPEC §3.5.2：软依赖缺失是 WARN，不是失败；但结果仍如实记为 MISSING_ID。
                LOG.warn("软依赖缺失 {} × {}（不影响准入）", owner.modId(), constraint.targetId());
            }
            return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(),
                    kind, declaredRange, null, DependencyOutcome.MISSING_ID);
        }

        final List<VersionRange> ranges;
        try {
            ranges = parseRanges(owner, constraint);
        } catch (VersionParseException e) {
            return unparseable(owner, kind, constraint, declaredRange, found.getFirst(), e, "版本区间");
        }

        final List<Version> foundVersions = new ArrayList<>(found.size());
        String unparseableVersion = null;
        VersionParseException unparseableCause = null;
        for (String rawVersion : found) {
            try {
                foundVersions.add(Version.parse(rawVersion));
            } catch (VersionParseException e) {
                if (unparseableVersion == null) {
                    unparseableVersion = rawVersion;
                    unparseableCause = e;
                }
            }
        }

        Optional<Version> matched = Optional.empty();
        for (Version candidateVersion : foundVersions) {
            if (ranges.stream().anyMatch(range -> range.matches(candidateVersion))) {
                matched = Optional.of(candidateVersion);
                break;
            }
        }

        if (matched.isEmpty() && unparseableVersion != null) {
            // 至少一个候选版本存在但无法比较，不能断言“版本不符”。
            return unparseable(owner, kind, constraint, declaredRange,
                    unparseableVersion, unparseableCause, "目标版本");
        }

        final String resolvedVersion = matched.map(Version::source).orElse(found.getFirst());
        if (kind == DepKind.BREAKS) {
            return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(),
                    kind, declaredRange, resolvedVersion,
                    matched.isPresent() ? DependencyOutcome.BREAKS_VIOLATED
                            : DependencyOutcome.SATISFIED);
        }
        return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(),
                kind, declaredRange, resolvedVersion,
                matched.isPresent() ? DependencyOutcome.SATISFIED
                        : DependencyOutcome.VERSION_MISMATCH);
    }

    private List<VersionRange> parseRanges(final ModCandidate owner, final Constraint constraint) {
        if (constraint.versionRanges().isEmpty()) {
            return List.of(ALWAYS);
        }
        final List<VersionRange> ranges = new ArrayList<>(constraint.versionRanges().size());
        for (String rawRange : constraint.versionRanges()) {
            ranges.add(parseRange(owner, rawRange));
        }
        return ranges;
    }

    private VersionRange parseRange(final ModCandidate owner, final String rawRange) {
        final LoaderFamily family = owner.loaderFamily();
        return switch (family) {
            case NEOFORGE, FORGE -> neoForgeRange(owner, rawRange);
            case FABRIC, QUILT -> FabricVersionRange.parse(rawRange);
            case UNKNOWN -> throw new IllegalStateException(
                    "OK 记录缺少加载器家族，无法选择版本区间语法：" + owner.sourceFile());
        };
    }

    private VersionRange neoForgeRange(final ModCandidate owner, final String rawRange) {
        final NeoForgeVersionRange range = NeoForgeVersionRange.parse(rawRange);
        if (range.unbracketed()) {
            // SPEC §3.5.3：无括号形式是两套语法唯一明确分歧处，必须记录用了哪条约定。
            LOG.debug("无括号版本区间 {} 按 Maven 软要求约定解释为 >= {}（模组 {}）",
                    rawRange, rawRange, owner.modId());
        }
        return range;
    }

    private DependencyResult unparseable(final ModCandidate owner, final DepKind kind,
            final Constraint constraint, final String declaredRange, final String resolvedVersion,
            final VersionParseException exception, final String subject) {
        // SPEC §3.5.5：UNPARSEABLE 必须计数并以 WARN 打出原串，不能当成满足、也不能静默丢弃。
        LOG.warn("无法解析的版本区间（{}）：{} → {}，声明 {}，原串 {}，原因 {}",
                subject, owner.modId(), constraint.targetId(), declaredRange,
                exception.offending(), exception.getMessage());
        final String resolved = resolvedVersion == null || resolvedVersion.isBlank()
                ? null : resolvedVersion;
        return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(), kind,
                declaredRange, resolved, DependencyOutcome.UNPARSEABLE);
    }

    private Map<String, List<String>> indexVersions(final List<ModCandidate> candidates) {
        final Map<String, List<String>> versionsById = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK || candidate.modId() == null) {
                continue;
            }
            final List<String> versions =
                    versionsById.computeIfAbsent(candidate.modId(), id -> new ArrayList<>());
            if (!versions.contains(candidate.version())) {
                versions.add(candidate.version());
            }
        }
        return versionsById;
    }
}
