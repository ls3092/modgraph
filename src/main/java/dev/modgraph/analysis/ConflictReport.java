package dev.modgraph.analysis;

import java.util.List;

/**
 * 一次静态冲突预测的完整结果，包含冲突分级和目标稀有度统计。
 *
 * @param conflicts 全部唯一模组两两比较结果，包含 NONE
 * @param targetFrequencies 全部目标的命中模组数，按命中数降序、目标名升序排列
 * @param modCount 参与比较的唯一 modId 数量
 * @param totalPairs 两两比较的总对数
 * @param ubiquitousThreshold 判定 ubiquitous 的最小命中数（模组总数 × 25%，向上取整）
 */
public record ConflictReport(
        List<ModConflict> conflicts,
        List<TargetFrequency> targetFrequencies,
        int modCount,
        int totalPairs,
        int ubiquitousThreshold) {

    public ConflictReport {
        conflicts = List.copyOf(conflicts);
        targetFrequencies = List.copyOf(targetFrequencies);
        if (modCount < 0) {
            throw new IllegalArgumentException("modCount 不能为负数：" + modCount);
        }
        if (totalPairs < 0) {
            throw new IllegalArgumentException("totalPairs 不能为负数：" + totalPairs);
        }
        if (ubiquitousThreshold < 0) {
            throw new IllegalArgumentException(
                    "ubiquitousThreshold 不能为负数：" + ubiquitousThreshold);
        }
    }
}
