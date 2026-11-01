package dev.modgraph.version;

/**
 * 版本字符串或版本区间无法按 SPEC §3.5.3 解析。
 *
 * <p>这是数据形态问题，不是程序 bug：调用方必须把它归类为 {@code UNPARSEABLE}，
 * 计数并以 WARN 打出原串，绝不能当成“满足”处理。</p>
 */
public final class VersionParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String offending;

    public VersionParseException(final String offending, final String reason) {
        super(reason);
        this.offending = offending;
    }

    /** 触发失败的原始字符串。 */
    public String offending() {
        return offending;
    }
}
