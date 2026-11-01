package dev.modgraph.resolve;

/**
 * 单条依赖的判定结果，取值与 SPEC §3.5.5 一一对应，每条依赖恰好一个。
 *
 * <p>{@code SATISFIED} 对 {@code breaks} 声明表示“不构成冲突”，对 {@code depends} / {@code recommends}
 * 表示“目标存在且版本落在区间内”。</p>
 */
public enum DependencyOutcome {

    SATISFIED,
    MISSING_ID,
    VERSION_MISMATCH,
    BREAKS_VIOLATED,
    UNPARSEABLE
}
