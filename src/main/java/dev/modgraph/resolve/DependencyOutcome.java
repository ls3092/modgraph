package dev.modgraph.resolve;

/**
 * 单条依赖的判定结果，取值与 SPEC §3.5.5 一一对应，每条依赖恰好一个。
 *
 * <p>{@code SATISFIED} 对 {@code breaks} 声明表示“不构成冲突”，对 {@code depends} / {@code recommends}
 * 表示“目标存在且版本落在区间内”。</p>
 *
 * <p>{@code AMBIGUOUS_ALIAS} 是 v0.9 新增的第六种：ID 确实存在，但同时有多个拥有者
 * （多个 modId 声明了同一个 {@code provides} 别名，或别名与真实 modId / 环境 ID 重名）。
 * 此时不挑任何一个候选——挑一个等于把冲突藏起来。</p>
 */
public enum DependencyOutcome {

    SATISFIED,
    MISSING_ID,
    VERSION_MISMATCH,
    BREAKS_VIOLATED,
    AMBIGUOUS_ALIAS,
    UNPARSEABLE
}
