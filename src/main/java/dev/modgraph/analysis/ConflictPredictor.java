package dev.modgraph.analysis;

import dev.modgraph.model.Invasiveness;
import dev.modgraph.model.ModCandidate;
import dev.modgraph.model.Status;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 mixin 目标简单类名交集的静态冲突预测。
 *
 * <p>同一 modId 的多个 loader 变体在这里合并为一个逻辑模组；重复 modId 的正式处理留给依赖图步骤。</p>
 */
public final class ConflictPredictor {

    private static final Logger LOG = LoggerFactory.getLogger(ConflictPredictor.class);

    public ConflictReport predict(final List<ModCandidate> candidates) {
        final List<ModNode> mods = mergeByModId(candidates);
        final int ubiquitousThreshold = (mods.size() + 9) / 10;
        final Map<String, Integer> targetModCounts = countTargetMods(mods);
        final Set<String> ubiquitous = new HashSet<>();
        for (Map.Entry<String, Integer> entry : targetModCounts.entrySet()) {
            if (entry.getValue() >= ubiquitousThreshold) {
                ubiquitous.add(entry.getKey());
            }
        }
        final List<TargetFrequency> targetFrequencies = targetModCounts.entrySet().stream()
                .map(entry -> new TargetFrequency(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(TargetFrequency::modCount).reversed()
                        .thenComparing(TargetFrequency::target))
                .toList();

        final List<ModConflict> conflicts = new ArrayList<>();
        for (int i = 0; i < mods.size(); i++) {
            for (int j = i + 1; j < mods.size(); j++) {
                final ModNode a = mods.get(i);
                final ModNode b = mods.get(j);
                final List<String> shared = sharedTargets(a.targets(), b.targets());
                final ConflictLevel level =
                        classify(a.invasiveness(), b.invasiveness(), shared, ubiquitous);
                if (level == ConflictLevel.HIGH) {
                    LOG.warn("潜在冲突 {} × {}，共同目标：{}",
                            a.id(), b.id(), String.join(";", shared));
                }
                conflicts.add(new ModConflict(a.id(), b.id(), level, shared));
            }
        }
        final int totalPairs = mods.size() * (mods.size() - 1) / 2;
        return new ConflictReport(
                conflicts, targetFrequencies, mods.size(), totalPairs, ubiquitousThreshold);
    }

    private static Map<String, Integer> countTargetMods(final List<ModNode> mods) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (ModNode mod : mods) {
            for (String target : mod.targets()) {
                counts.merge(target, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static List<ModNode> mergeByModId(final List<ModCandidate> candidates) {
        final Map<String, ModNodeBuilder> builders = new LinkedHashMap<>();
        for (ModCandidate candidate : candidates) {
            if (candidate.status() != Status.OK) {
                continue;
            }
            final String id = candidate.modId() == null || candidate.modId().isBlank()
                    ? candidate.sourceFile()
                    : candidate.modId();
            builders.computeIfAbsent(id, ignored -> new ModNodeBuilder(id))
                    .merge(candidate);
        }
        final List<ModNode> result = new ArrayList<>();
        for (ModNodeBuilder builder : builders.values()) {
            result.add(builder.build());
        }
        return result;
    }

    private static List<String> sharedTargets(final List<String> a, final List<String> b) {
        final Set<String> shared = new TreeSet<>(a);
        shared.retainAll(b);
        return List.copyOf(shared);
    }

    private static ConflictLevel classify(
            final Invasiveness a,
            final Invasiveness b,
            final List<String> shared,
            final Set<String> ubiquitous) {
        if (!shared.isEmpty()) {
            // SPEC v0.6 §2.4 的最终分级表（用户 m01468 裁决，all-rare）：
            // HIGH   共享目标全部不是 ubiquitous（撞的位置确实不寻常）
            // MEDIUM 共享目标全部是 ubiquitous（噪声），或双方 RENDER/CORE 但无交集
            // LOW    仅一方 RENDER/CORE，另一方 MIXIN
            // NONE   其余
            // any-rare 会把“共享 3 个目标、其中 2 个是 GameRenderer/Minecraft、
            // 只有 1 个稀有”的配对也判 HIGH；实际撞车大概率在高频类，稀有类只是顺带命中。
            // 因此这里只在所有共享目标都非 ubiquitous 时判 HIGH。
            final boolean hasUbiquitousTarget = shared.stream().anyMatch(ubiquitous::contains);
            return hasUbiquitousTarget ? ConflictLevel.MEDIUM : ConflictLevel.HIGH;
        }
        final boolean aDeep = isDeep(a);
        final boolean bDeep = isDeep(b);
        if (aDeep && bDeep) {
            return ConflictLevel.MEDIUM;
        }
        if (aDeep && b == Invasiveness.MIXIN) {
            return ConflictLevel.LOW;
        }
        if (bDeep && a == Invasiveness.MIXIN) {
            return ConflictLevel.LOW;
        }
        return ConflictLevel.NONE;
    }

    private static boolean isDeep(final Invasiveness invasiveness) {
        return invasiveness == Invasiveness.RENDER || invasiveness == Invasiveness.CORE;
    }

    private static int rank(final Invasiveness invasiveness) {
        if (invasiveness == null) {
            return 0;
        }
        return switch (invasiveness) {
            case RENDER -> 4;
            case CORE -> 3;
            case MIXIN -> 2;
            case API -> 1;
        };
    }

    private record ModNode(String id, List<String> targets, Invasiveness invasiveness) {
        private ModNode {
            targets = List.copyOf(targets);
        }
    }

    private static final class ModNodeBuilder {
        private final String id;
        private final LinkedHashSet<String> targets = new LinkedHashSet<>();
        private Invasiveness invasiveness;

        private ModNodeBuilder(final String id) {
            this.id = id;
        }

        private void merge(final ModCandidate candidate) {
            targets.addAll(candidate.mixinTargets());
            if (rank(candidate.invasiveness()) > rank(invasiveness)) {
                invasiveness = candidate.invasiveness();
            }
        }

        private ModNode build() {
            return new ModNode(id, List.copyOf(targets), invasiveness);
        }
    }
}
