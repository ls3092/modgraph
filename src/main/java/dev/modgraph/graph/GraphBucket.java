package dev.modgraph.graph;

/**
 * 硬依赖声明的五个桶（SPEC §3.6.3）。每条硬依赖恰好落入一个桶。
 *
 * <p>只有 {@link #EDGE} 产生边；其余四个桶都不建边，并各自单独计数、单独打印，
 * 永不合并成一个数字。</p>
 */
public enum GraphBucket {
    EDGE("进图"),
    ENVIRONMENT("环境提供"),
    OTHER_UNIVERSE("跨加载器宇宙"),
    ALIAS_ONLY("仅别名"),
    ABSENT("目标不存在");

    private final String label;

    GraphBucket(final String label) {
        this.label = label;
    }

    /** 摘要行里使用的中文标签。 */
    public String label() {
        return label;
    }
}
