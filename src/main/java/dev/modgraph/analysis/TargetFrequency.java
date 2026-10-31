package dev.modgraph.analysis;

import java.util.Objects;

/**
 * 一个 mixin 目标简单类被多少个唯一模组命中。
 *
 * @param target 目标类的简单名，例如 {@code GameRenderer}
 * @param modCount 命中该目标的唯一 modId 数量（同一模组的多个加载器变体只算一次）
 */
public record TargetFrequency(String target, int modCount) {

    public TargetFrequency {
        Objects.requireNonNull(target, "target");
        if (modCount <= 0) {
            throw new IllegalArgumentException("modCount 必须为正数：" + modCount);
        }
    }
}
