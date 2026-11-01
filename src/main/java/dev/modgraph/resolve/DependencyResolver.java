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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * M0.5 依赖可满足性判定（SPEC v0.9 §3.5）。
 *
 * <p>只做一件事：对每条已登记的依赖声明回答“本模组集合是否满足它”，产出 §3.5.5 的结果之一。
 * 不排序、不求解、不选版本、不下载，也不改动 M0 的 {@code conflicts.csv}。</p>
 *
 * <p>解析口径（全部写在代码里，可审计）：</p>
 * <ul>
 *   <li>目标按优先级解析，命中即停：真实 {@code modId} → 环境 ID → 唯一 {@code provides} 别名拥有者。
 *       真实 {@code modId} 优先于别名，别名只是“这个 ID 不是任何模组”时的替补。</li>
 *   <li>别名被两个及以上 {@code modId} 声明时不挑任何一个，记 {@code AMBIGUOUS_ALIAS} 并打 WARN 列出全部候选；
 *       环境 ID 被某个模组声明为别名同样记歧义。</li>
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

    /** 拥有者来源，只用于 WARN 文本，便于人工复核。 */
    private static final String SOURCE_MOD_ID = "modId";
    private static final String SOURCE_PROVIDES = "provides";
    private static final String SOURCE_ENVIRONMENT = "environment";

    public DependencyReport resolve(final List<ModCandidate> candidates,
            final EnvironmentFacts environment) {
        final Map<String, List<String>> versionsById = indexVersions(candidates);
        final Map<String, Set<String>> providersByAlias = indexProviders(candidates);
        final List<DependencyResult> results = new ArrayList<>();

        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            for (Constraint constraint : candidate.depends()) {
                results.add(evaluate(candidate, DepKind.REQUIRED, constraint,
                        versionsById, providersByAlias, environment));
            }
            for (Constraint constraint : candidate.recommends()) {
                results.add(evaluate(candidate, DepKind.RECOMMENDS, constraint,
                        versionsById, providersByAlias, environment));
            }
            for (Constraint constraint : candidate.breaks()) {
                results.add(evaluate(candidate, DepKind.BREAKS, constraint,
                        versionsById, providersByAlias, environment));
            }
        }
        return new DependencyReport(results);
    }

    private DependencyResult evaluate(final ModCandidate owner, final DepKind kind,
            final Constraint constraint, final Map<String, List<String>> versionsById,
            final Map<String, Set<String>> providersByAlias, final EnvironmentFacts environment) {
        final String declaredRange = String.join(";", constraint.versionRanges());
        final List<Target> targets = targetsOf(constraint.targetId(), versionsById,
                providersByAlias, environment);

        if (targets.isEmpty()) {
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

        if (targets.size() > 1) {
            // SPEC §3.5.5：多个拥有者时不挑任何一个，候选全部打出，结果单列为 AMBIGUOUS_ALIAS。
            LOG.warn("别名歧义：{} 依赖 {}，候选 {}（共 {} 个拥有者，不替它选）",
                    owner.modId(), constraint.targetId(), describe(targets), targets.size());
            return new DependencyResult(owner.modId(), owner.version(), constraint.targetId(),
                    kind, declaredRange, null, DependencyOutcome.AMBIGUOUS_ALIAS);
        }

        final List<String> found = targets.getFirst().versions();
        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "拥有者没有版本，索引构造有误：" + constraint.targetId());
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

    /**
     * 按 SPEC §3.5.5 的优先级解析一个 ID，命中即停：
     * <ol>
     *   <li>真实 {@code modId} —— 直接返回，别名与环境的同名声明都不参与，因此永远不会判歧义；</li>
     *   <li>环境 ID —— 与环境事实同名且被某个模组声明为别名的，两者都进候选，交给上层判歧义；</li>
     *   <li>别名 —— 一个拥有者时上层正常解析，多个拥有者时上层判 {@code AMBIGUOUS_ALIAS}；</li>
     *   <li>都没有 —— 上层判 {@code MISSING_ID}。</li>
     * </ol>
     */
    private List<Target> targetsOf(final String id, final Map<String, List<String>> versionsById,
            final Map<String, Set<String>> providersByAlias, final EnvironmentFacts environment) {
        // 规则 1：真实 modId 是权威身份，优先于一切别名。
        final List<String> direct = versionsById.get(id);
        if (direct != null) {
            return List.of(new Target(id, SOURCE_MOD_ID, direct));
        }

        final List<Target> aliasTargets = new ArrayList<>(2);
        for (String provider : providersByAlias.getOrDefault(id, Set.of())) {
            aliasTargets.add(new Target(provider, SOURCE_PROVIDES,
                    versionsById.getOrDefault(provider, List.of())));
        }

        // 规则 2：环境 ID。环境与别名重名时保留两者，由上层判 AMBIGUOUS_ALIAS。
        final Optional<String> environmentVersion = environment.versionOf(id);
        if (environmentVersion.isPresent()) {
            final List<Target> targets = new ArrayList<>(aliasTargets.size() + 1);
            targets.add(new Target(id, SOURCE_ENVIRONMENT, List.of(environmentVersion.get())));
            targets.addAll(aliasTargets);
            return targets;
        }
        return aliasTargets;
    }

    private static String describe(final List<Target> targets) {
        final StringBuilder builder = new StringBuilder();
        for (Target target : targets) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(target.ownerId())
                    .append(' ')
                    .append(String.join("/", target.versions()))
                    .append('（')
                    .append(target.source())
                    .append('）');
        }
        return builder.toString();
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

    /** 别名 → 声明它的 modId 集合；只看 status=OK 的记录。 */
    private Map<String, Set<String>> indexProviders(final List<ModCandidate> candidates) {
        final Map<String, Set<String>> providersByAlias = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK || candidate.modId() == null) {
                continue;
            }
            for (String alias : candidate.provides()) {
                providersByAlias.computeIfAbsent(alias, key -> new LinkedHashSet<>())
                        .add(candidate.modId());
            }
        }
        return providersByAlias;
    }

    /** 一个 ID 的一个拥有者：谁、怎么被找到的、有哪些版本。 */
    private record Target(String ownerId, String source, List<String> versions) {
    }
}
