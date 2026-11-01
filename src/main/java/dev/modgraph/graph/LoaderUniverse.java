package dev.modgraph.graph;

import dev.modgraph.model.LoaderFamily;
import java.util.Optional;

/**
 * 加载器宇宙（SPEC §3.6.2）。
 *
 * <p>每个宇宙一张图，边永不跨宇宙。{@code QUILT} 并入 {@code FABRIC}，因为 §2.1 只在同时存在
 * {@code fabric.mod.json} 时才判 {@code QUILT}；{@code FORGE} 与 {@code NEOFORGE} 各自独立
 * （合并它们等于做兼容性断言，§0 禁止）；{@code UNKNOWN} 永不成为节点。</p>
 */
public enum LoaderUniverse {
    FABRIC,
    FORGE,
    NEOFORGE;

    /** 记录所属的宇宙；{@code UNKNOWN} 不属于任何宇宙。 */
    public static Optional<LoaderUniverse> of(final LoaderFamily family) {
        return switch (family) {
            case FABRIC, QUILT -> Optional.of(FABRIC);
            case FORGE -> Optional.of(FORGE);
            case NEOFORGE -> Optional.of(NEOFORGE);
            case UNKNOWN -> Optional.empty();
        };
    }
}
