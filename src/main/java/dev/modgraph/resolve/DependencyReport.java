package dev.modgraph.resolve;

import java.util.List;
import java.util.Objects;

/**
 * M0.5 的完整判定结果。
 *
 * <p>{@link #results()} 与输入依赖声明一一对应、不去重、不省略；计数方法只是对同一份结果的聚合，
 * 不会把 {@code UNPARSEABLE} 折叠进 {@code SATISFIED}。</p>
 */
public record DependencyReport(List<DependencyResult> results) {

    public DependencyReport {
        Objects.requireNonNull(results, "results");
        results = List.copyOf(results);
    }

    public long count(final DepKind kind) {
        return results.stream().filter(result -> result.depKind() == kind).count();
    }

    public long count(final DependencyOutcome outcome) {
        return results.stream().filter(result -> result.outcome() == outcome).count();
    }

    /** 按（种类, 结果）联合计数，供 M1.5 的 {@code unsatisfiedRequired} 使用。 */
    public long count(final DepKind kind, final DependencyOutcome outcome) {
        return results.stream()
                .filter(result -> result.depKind() == kind && result.outcome() == outcome)
                .count();
    }
}
