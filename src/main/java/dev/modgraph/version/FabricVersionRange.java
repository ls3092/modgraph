package dev.modgraph.version;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric / Quilt 侧版本区间（SPEC §3.5.3 上半表）。
 *
 * <p>支持形式：{@code *}、{@code >=} / {@code >} / {@code <=} / {@code <} / {@code ==} / {@code =}、
 * {@code ~1.2}（{@code >=1.2, <1.3}）、{@code 1.21.x} 通配段、空格分隔的 AND 组合。
 * 另有两个真实数据必需、由本实现显式补上的规则：</p>
 *
 * <ul>
 *   <li>裸版本串（如 {@code 1.21.1}）按**精确匹配**处理，与 Fabric 加载器一致；
 *       注意这与 NeoForge 的无括号形式语义相反（后者是“最低版本”）。</li>
 *   <li>空串视为不限制版本（等价于 {@code *}）。</li>
 * </ul>
 */
public final class FabricVersionRange implements VersionRange {

    private static final VersionRange ALWAYS = version -> true;

    private final String source;
    private final List<VersionRange> predicates;

    private FabricVersionRange(final String source, final List<VersionRange> predicates) {
        this.source = source;
        this.predicates = List.copyOf(predicates);
    }

    /**
     * @throws VersionParseException 任一谓词无法解析时抛出，调用方必须归类为 {@code UNPARSEABLE}
     */
    public static FabricVersionRange parse(final String raw) {
        final String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return new FabricVersionRange(raw, List.of(ALWAYS));
        }
        final List<VersionRange> predicates = new ArrayList<>();
        for (final String token : text.split("\\s+")) {
            if (!token.isEmpty()) {
                predicates.add(parseToken(token, text));
            }
        }
        if (predicates.isEmpty()) {
            return new FabricVersionRange(raw, List.of(ALWAYS));
        }
        return new FabricVersionRange(raw, predicates);
    }

    public String source() {
        return source;
    }

    @Override
    public boolean matches(final Version version) {
        for (VersionRange predicate : predicates) {
            if (!predicate.matches(version)) {
                return false;
            }
        }
        return true;
    }

    private static VersionRange parseToken(final String token, final String context) {
        if ("*".equals(token)) {
            return ALWAYS;
        }
        if (token.startsWith(">=")) {
            return lowerBound(token, 2, context, true);
        }
        if (token.startsWith("<=")) {
            return upperBound(token, 2, context, true);
        }
        if (token.startsWith("==")) {
            return exact(token, 2, context);
        }
        if (token.startsWith(">")) {
            return lowerBound(token, 1, context, false);
        }
        if (token.startsWith("<")) {
            return upperBound(token, 1, context, false);
        }
        if (token.startsWith("=")) {
            return exact(token, 1, context);
        }
        if (token.startsWith("~")) {
            return tilde(token, context);
        }
        if (token.isEmpty() || !isAsciiDigit(token.charAt(0))) {
            throw new VersionParseException(context,
                    "不认识的 Fabric 版本谓词：" + token);
        }
        final Version version = Version.parse(token);
        if (version.hasWildcard()) {
            return candidate -> version.matchesWildcard(candidate);
        }
        return candidate -> candidate.compareTo(version) == 0;
    }

    private static VersionRange lowerBound(final String token, final int prefixLength,
            final String context, final boolean inclusive) {
        final Version bound = boundVersion(token, prefixLength, context);
        return candidate -> {
            final int result = candidate.compareTo(bound);
            return inclusive ? result >= 0 : result > 0;
        };
    }

    private static VersionRange upperBound(final String token, final int prefixLength,
            final String context, final boolean inclusive) {
        final Version bound = boundVersion(token, prefixLength, context);
        return candidate -> {
            final int result = candidate.compareTo(bound);
            return inclusive ? result <= 0 : result < 0;
        };
    }

    private static VersionRange exact(final String token, final int prefixLength,
            final String context) {
        final Version bound = boundVersion(token, prefixLength, context);
        return candidate -> candidate.compareTo(bound) == 0;
    }

    private static VersionRange tilde(final String token, final String context) {
        final Version minimum = boundVersion(token, 1, context);
        final Version upper;
        try {
            upper = minimum.bumpForTilde();
        } catch (VersionParseException e) {
            throw new VersionParseException(context, "波浪号版本无法确定上界：" + e.getMessage());
        }
        return candidate -> candidate.compareTo(minimum) >= 0 && candidate.compareTo(upper) < 0;
    }

    private static Version boundVersion(final String token, final int prefixLength,
            final String context) {
        final String rest = token.substring(prefixLength).trim();
        if (rest.isEmpty()) {
            throw new VersionParseException(context, "版本谓词缺少版本号：" + token);
        }
        final Version version = Version.parse(rest);
        if (version.hasWildcard()) {
            throw new VersionParseException(context, "比较运算的版本号不能含通配段：" + token);
        }
        return version;
    }

    private static boolean isAsciiDigit(final char character) {
        return character >= '0' && character <= '9';
    }
}
