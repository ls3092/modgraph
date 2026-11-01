package dev.modgraph.report;

import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyReport;

/**
 * SPEC §3.7.2：本次运行能够证明不满足的硬依赖声明。
 *
 * <p>只按 {@code dependencies.csv} 行的 {@code (depKind, outcome)} 计数，不重新求值任何东西。
 * {@code count()} 只含 {@code required} 行；其余类别单独保存、单独打印，永不折进这个数字。</p>
 *
 * <p>构造时校验“已解释的行 == 全部行”：{@code SATISFIED + count() + excludedCount()} 必须等于
 * 依赖行总数，否则说明出现了本类没有分类的行，直接抛异常让运行失败，不静默丢弃。</p>
 *
 * @param requiredMissingId       {@code required} + {@code MISSING_ID}，计入
 * @param requiredVersionMismatch {@code required} + {@code VERSION_MISMATCH}，计入
 * @param ambiguousAlias          {@code required} + {@code AMBIGUOUS_ALIAS}，工具无法判定，不计入
 * @param breaksViolated          {@code breaks} + {@code BREAKS_VIOLATED}，冲突命中，不计入
 * @param softMissingId           {@code recommends} + {@code MISSING_ID}，不计入
 * @param softVersionMismatch     {@code recommends} + {@code VERSION_MISMATCH}，不计入
 * @param unparseable             任何种类的 {@code UNPARSEABLE}，实现缺陷，不计入
 */
public record UnsatisfiedRequired(
        int requiredMissingId,
        int requiredVersionMismatch,
        int ambiguousAlias,
        int breaksViolated,
        int softMissingId,
        int softVersionMismatch,
        int unparseable) {

    public UnsatisfiedRequired {
        requireNonNegative(requiredMissingId, "requiredMissingId");
        requireNonNegative(requiredVersionMismatch, "requiredVersionMismatch");
        requireNonNegative(ambiguousAlias, "ambiguousAlias");
        requireNonNegative(breaksViolated, "breaksViolated");
        requireNonNegative(softMissingId, "softMissingId");
        requireNonNegative(softVersionMismatch, "softVersionMismatch");
        requireNonNegative(unparseable, "unparseable");
    }

    public static UnsatisfiedRequired of(final DependencyReport report) {
        final UnsatisfiedRequired result = new UnsatisfiedRequired(
                count(report, DepKind.REQUIRED, DependencyOutcome.MISSING_ID),
                count(report, DepKind.REQUIRED, DependencyOutcome.VERSION_MISMATCH),
                count(report, DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS),
                count(report, DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED),
                count(report, DepKind.RECOMMENDS, DependencyOutcome.MISSING_ID),
                count(report, DepKind.RECOMMENDS, DependencyOutcome.VERSION_MISMATCH),
                count(report, DependencyOutcome.UNPARSEABLE));
        final long satisfied = report.count(DependencyOutcome.SATISFIED);
        final long explained = satisfied + result.count() + result.excludedCount();
        if (explained != report.results().size()) {
            throw new IllegalStateException(
                    "unsatisfiedRequired 没有解释全部依赖行：SATISFIED " + satisfied
                            + " + 计入 " + result.count() + " + 未计入 " + result.excludedCount()
                            + " = " + explained + " != 总行数 " + report.results().size());
        }
        return result;
    }

    /** 计入字段的条目数：只有硬依赖的 MISSING_ID 与 VERSION_MISMATCH。 */
    public int count() {
        return requiredMissingId + requiredVersionMismatch;
    }

    /** 未计入但必须单独打印的条目数。 */
    public int excludedCount() {
        return ambiguousAlias + breaksViolated + softMissingId + softVersionMismatch + unparseable;
    }

    private static int count(final DependencyReport report,
                             final DepKind kind,
                             final DependencyOutcome outcome) {
        return Math.toIntExact(report.count(kind, outcome));
    }

    private static int count(final DependencyReport report, final DependencyOutcome outcome) {
        return Math.toIntExact(report.count(outcome));
    }

    private static void requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " 不能为负：" + value);
        }
    }
}
