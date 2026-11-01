package dev.modgraph.version;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge / Forge 侧版本区间（SPEC §3.5.3 下半表，Maven 区间语法）。
 *
 * <p>支持形式：{@code [21.1.219,)}、{@code (21.1.219,)}、{@code [1.21,1.22)}、{@code (,1.22]}、
 * {@code [1.21.1]}（精确）、无括号的 {@code 21.1.219}（Maven 软要求，按 {@code >=} 处理），
 * 以及 Maven 的多区间并集（如 {@code (,1.0],[1.2,)}）。</p>
 *
 * <p>无括号形式是两套语法唯一明确分歧的地方，因此解析后由调用方以 DEBUG 打出所采用的约定。</p>
 */
public final class NeoForgeVersionRange implements VersionRange {

    private final String source;
    private final List<Interval> intervals;
    private final boolean unbracketed;

    private NeoForgeVersionRange(final String source, final List<Interval> intervals,
            final boolean unbracketed) {
        this.source = source;
        this.intervals = List.copyOf(intervals);
        this.unbracketed = unbracketed;
    }

    /**
     * @throws VersionParseException 语法不完整、括号不配对、或版本号含占位符时抛出
     */
    public static NeoForgeVersionRange parse(final String raw) {
        final String text = raw == null ? "" : raw.trim();
        // 空串与 * 都表示不限制版本；NeoForge 元数据里确实有 versionRange = "*"。
        if (text.isEmpty() || "*".equals(text)) {
            return new NeoForgeVersionRange(raw, List.of(Interval.unbounded()), false);
        }

        final char first = text.charAt(0);
        if (first != '[' && first != '(') {
            if (text.indexOf(',') >= 0) {
                throw new VersionParseException(text, "无括号区间不能含逗号，疑似缺少方括号");
            }
            final Version minimum = boundVersion(text, text);
            return new NeoForgeVersionRange(raw,
                    List.of(new Interval(minimum, true, null, false)), true);
        }

        final List<Interval> intervals = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            final char opening = text.charAt(index);
            if (opening != '[' && opening != '(') {
                throw new VersionParseException(text, "区间必须以 [ 或 ( 开始，实际位置 " + index);
            }
            final int closingIndex = findClosing(text, index + 1);
            if (closingIndex < 0) {
                throw new VersionParseException(text, "区间缺少闭合括号");
            }
            final char closing = text.charAt(closingIndex);
            final String body = text.substring(index + 1, closingIndex);
            intervals.add(parseBody(body, opening == '[', closing == ']', text));
            index = closingIndex + 1;
            if (index < text.length() && text.charAt(index) == ',') {
                index++;
            }
        }
        return new NeoForgeVersionRange(raw, intervals, false);
    }

    public String source() {
        return source;
    }

    /** 是否是无括号形式（Maven 软要求约定为 {@code >=}）。 */
    public boolean unbracketed() {
        return unbracketed;
    }

    @Override
    public boolean matches(final Version version) {
        for (Interval interval : intervals) {
            if (interval.contains(version)) {
                return true;
            }
        }
        return false;
    }

    private static int findClosing(final String text, final int from) {
        for (int index = from; index < text.length(); index++) {
            final char character = text.charAt(index);
            if (character == ']' || character == ')') {
                return index;
            }
        }
        return -1;
    }

    private static Interval parseBody(final String body, final boolean lowerInclusive,
            final boolean upperInclusive, final String context) {
        final int comma = body.indexOf(',');
        if (comma < 0) {
            if (!lowerInclusive || !upperInclusive) {
                throw new VersionParseException(context,
                        "精确版本必须写成 [版本]，不能用 ( 或 )：" + body);
            }
            final Version exact = boundVersion(body, context);
            return new Interval(exact, true, exact, true);
        }
        if (body.indexOf(',', comma + 1) >= 0) {
            throw new VersionParseException(context, "区间体只能包含一个逗号：" + body);
        }
        final String lowerText = body.substring(0, comma).trim();
        final String upperText = body.substring(comma + 1).trim();
        final Version lower = lowerText.isEmpty() ? null : boundVersion(lowerText, context);
        final Version upper = upperText.isEmpty() ? null : boundVersion(upperText, context);
        return new Interval(lower, lowerInclusive, upper, upperInclusive);
    }

    private static Version boundVersion(final String text, final String context) {
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new VersionParseException(context, "区间端点为空");
        }
        final Version version = Version.parse(trimmed);
        if (version.hasWildcard()) {
            throw new VersionParseException(context, "Maven 区间的端点不能含通配段：" + trimmed);
        }
        return version;
    }

    private record Interval(Version lower, boolean lowerInclusive, Version upper,
            boolean upperInclusive) {

        static Interval unbounded() {
            return new Interval(null, false, null, false);
        }

        boolean contains(final Version version) {
            if (lower != null) {
                final int comparison = version.compareTo(lower);
                if (comparison < 0 || (comparison == 0 && !lowerInclusive)) {
                    return false;
                }
            }
            if (upper != null) {
                final int comparison = version.compareTo(upper);
                if (comparison > 0 || (comparison == 0 && !upperInclusive)) {
                    return false;
                }
            }
            return true;
        }
    }
}
