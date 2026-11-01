package dev.modgraph.resolve;

import java.util.Objects;

/**
 * 一条依赖声明的判定结果，对应 {@code dependencies.csv} 的一行。
 *
 * @param modId           声明方模组 ID
 * @param version         声明方版本（原样，不做规范化）
 * @param depId           被依赖 / 被推荐 / 被声明冲突的 ID
 * @param depKind         声明种类
 * @param declaredRange   声明的版本区间原串；多条区间用 {@code ;} 连接
 * @param resolvedVersion 实际找到的版本；目标不存在时为 {@code null}
 * @param outcome         判定结果
 */
public record DependencyResult(
        String modId,
        String version,
        String depId,
        DepKind depKind,
        String declaredRange,
        String resolvedVersion,
        DependencyOutcome outcome) {

    public DependencyResult {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(depId, "depId");
        Objects.requireNonNull(depKind, "depKind");
        Objects.requireNonNull(declaredRange, "declaredRange");
        Objects.requireNonNull(outcome, "outcome");
    }
}
