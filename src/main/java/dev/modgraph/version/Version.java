package dev.modgraph.version;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 版本号，按 SPEC §3.5.4 做**确定性字符串比较**。
 *
 * <p>规则原文：</p>
 * <ul>
 *   <li>按 {@code .} 和 {@code -} 切分成段，逐段比较</li>
 *   <li>数字段按数值比较，非数字段按字典序比较</li>
 *   <li>缺失的尾部段按 {@code 0} 计（{@code 1.21} == {@code 1.21.0}）</li>
 *   <li>预发布段（{@code -beta}、{@code -rc1}）低于同版本号的正式版</li>
 *   <li>通配段匹配该位置任意值</li>
 *   <li>纯字符串比较，不引入任何 semver 库或外部依赖</li>
 * </ul>
 *
 * <p>“预发布低于正式版”的实现方式：数字段恒大于非数字段，而缺失段按 {@code 0} 补齐，
 * 因此 {@code 1.21.1-beta} 的第 4 段 {@code beta} 小于补齐出来的 {@code 0}。</p>
 */
public final class Version implements Comparable<Version> {

    private static final String PADDING = "0";
    private static final BigInteger ONE = BigInteger.ONE;

    private final String source;
    private final List<String> segments;

    private Version(final String source, final List<String> segments) {
        this.source = source;
        this.segments = List.copyOf(segments);
    }

    /**
     * 解析版本字符串。
     *
     * <p>构建期占位符（{@code ${file.jarVersion}}、{@code #compatible_neoforge#}）不是版本号，
     * 一律判为不可解析：把它们当普通字符串参与比较会得出看似正常、实则无意义的结果。</p>
     */
    public static Version parse(final String text) {
        final String source = text == null ? "" : text.trim();
        if (source.isEmpty()) {
            throw new VersionParseException(text, "版本字符串为空");
        }
        if (source.contains("${") || source.contains("#")) {
            throw new VersionParseException(source,
                    "版本字符串含构建期占位符，不是可比较的版本号");
        }

        // SPEC §3.5.4 只规定按 . 和 - 切分；+ 之后是构建元数据（如 0.8.13+mc1.21.1），
        // 不参与版本优先级。若不剥离，13+mc1 会被当成非数字段，比较结果与事实相反。
        String trimmed = source;
        final int buildMetadata = trimmed.indexOf('+');
        if (buildMetadata >= 0) {
            trimmed = trimmed.substring(0, buildMetadata);
        }
        if (trimmed.isEmpty()) {
            throw new VersionParseException(source, "版本字符串只有构建元数据");
        }

        final List<String> parts = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        for (int index = 0; index < trimmed.length(); index++) {
            final char character = trimmed.charAt(index);
            if (character == '.' || character == '-') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        parts.add(current.toString());

        while (!parts.isEmpty() && parts.getLast().isEmpty()) {
            parts.removeLast();
        }
        if (parts.isEmpty()) {
            throw new VersionParseException(source, "版本字符串只有分隔符");
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new VersionParseException(source, "版本字符串含连续分隔符");
            }
        }
        return new Version(source, parts);
    }

    public String source() {
        return source;
    }

    public boolean hasWildcard() {
        return segments.stream().anyMatch(Version::isWildcard);
    }

    /**
     * 通配匹配：本对象是模式（可能含 {@code *} / {@code x} / {@code X}），
     * 判断候选版本是否落在模式内。
     */
    public boolean matchesWildcard(final Version candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!hasWildcard()) {
            throw new IllegalStateException("matchesWildcard 只用于含通配段的版本：" + source);
        }
        // 模式以通配段结尾时（1.21.x），其后的段不再限制；否则多余段按 0 补齐比较，
        // 这样 1.21 与 1.21.1 仍然不等。
        final boolean openEnded = isWildcard(segments.getLast());
        final int size = openEnded ? segments.size()
                : Math.max(segments.size(), candidate.segments.size());
        for (int index = 0; index < size; index++) {
            final String pattern = segmentAt(index);
            if (isWildcard(pattern)) {
                continue;
            }
            final String other = candidate.segmentAt(index);
            if (compareSegment(pattern, other) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 波浪号上界：{@code ~1.2} → {@code <1.3}，{@code ~1.21.1} → {@code <1.22}。
     *
     * <p>取第二段（minor）加一，并截断到两段；只有一段时对第一段加一。</p>
     */
    public Version bumpForTilde() {
        final List<String> parts = new ArrayList<>(segments);
        final int index = parts.size() >= 2 ? 1 : 0;
        final String target = parts.get(index);
        if (!isNumeric(target)) {
            throw new VersionParseException(source, "波浪号版本的中段不是数字：" + target);
        }
        parts.set(index, new BigInteger(target).add(ONE).toString());
        return new Version(source + "~upper", parts.subList(0, index + 1));
    }

    @Override
    public int compareTo(final Version other) {
        Objects.requireNonNull(other, "other");
        final int size = Math.max(segments.size(), other.segments.size());
        for (int index = 0; index < size; index++) {
            final int result = compareSegment(segmentAt(index), other.segmentAt(index));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return source;
    }

    private String segmentAt(final int index) {
        return index < segments.size() ? segments.get(index) : PADDING;
    }

    private static int compareSegment(final String left, final String right) {
        final boolean leftNumeric = isNumeric(left);
        final boolean rightNumeric = isNumeric(right);
        if (leftNumeric && rightNumeric) {
            return compareNumeric(left, right);
        }
        if (leftNumeric) {
            // 数字段大于非数字段：正式版高于同位置的预发布标签。
            return 1;
        }
        if (rightNumeric) {
            return -1;
        }
        return left.compareTo(right);
    }

    private static int compareNumeric(final String left, final String right) {
        final String leftDigits = stripLeadingZeros(left);
        final String rightDigits = stripLeadingZeros(right);
        if (leftDigits.length() != rightDigits.length()) {
            return Integer.compare(leftDigits.length(), rightDigits.length());
        }
        return leftDigits.compareTo(rightDigits);
    }

    private static String stripLeadingZeros(final String digits) {
        int index = 0;
        while (index < digits.length() - 1 && digits.charAt(index) == '0') {
            index++;
        }
        return digits.substring(index);
    }

    private static boolean isNumeric(final String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        for (int index = 0; index < segment.length(); index++) {
            final char character = segment.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isWildcard(final String segment) {
        return "*".equals(segment) || "x".equals(segment) || "X".equals(segment);
    }
}
