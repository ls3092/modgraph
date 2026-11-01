package dev.modgraph.report;

import dev.modgraph.graph.LoaderUniverse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * SPEC §3.7.3：单个宇宙内同一 {@code modId} 的重复组。
 *
 * <p>{@code versions} 是去重后的版本原串，按 ASCII 升序保存；比较用原串精确相等，不用 §3.5.4 的
 * 比较规则（后者为了区间匹配忽略 {@code +} 之后的构建元数据，但 {@code 3.0.0+1.21} 与
 * {@code 3.0.0+1.21.neoforge} 是两个不同的构建）。本对象只描述事实，不含推荐、不含"保留哪一份"。</p>
 *
 * @param universe    所属加载器宇宙
 * @param modId       重复的模组 ID
 * @param recordCount 该宇宙内该 {@code modId} 的 {@code status=OK} 记录数，必须 ≥ 2
 * @param versions    去重后的版本原串，ASCII 升序，不能为空
 */
public record DuplicateIdGroup(
        LoaderUniverse universe,
        String modId,
        int recordCount,
        List<String> versions) {

    public DuplicateIdGroup {
        Objects.requireNonNull(universe, "universe");
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(versions, "versions");
        if (modId.isBlank()) {
            throw new IllegalArgumentException("modId 不能为空");
        }
        if (recordCount < 2) {
            throw new IllegalArgumentException("重复组至少要有 2 条记录：" + recordCount);
        }
        final List<String> sorted = new ArrayList<>(new TreeSet<>(versions));
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("重复组的版本集合不能为空");
        }
        if (sorted.size() > recordCount) {
            throw new IllegalArgumentException(
                    "不同版本数 " + sorted.size() + " 不可能多于记录数 " + recordCount);
        }
        sorted.sort(Comparator.naturalOrder());
        versions = List.copyOf(sorted);
    }

    public VersionRelation relation() {
        return versions.size() == 1 ? VersionRelation.SAME_VERSION : VersionRelation.DIFFERENT_VERSION;
    }

    /** 组内两两组合的数量 {@code n * (n - 1) / 2}。 */
    public int pairCount() {
        return recordCount * (recordCount - 1) / 2;
    }

    /** {@code duplicates.csv} 的写法：版本用 {@code ;} 连接。 */
    public String csvVersions() {
        return String.join(";", versions);
    }
}
