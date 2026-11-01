package dev.modgraph.report;

import dev.modgraph.model.ModCandidate;
import dev.modgraph.resolve.DependencyReport;
import java.util.List;
import java.util.Objects;

/**
 * SPEC §3 的报告对象中由 M1.5 补齐的两个运行级聚合字段。
 *
 * <p>两者都不是逐记录字段：每次运行各一个值。M1.5 不改 M1 的图，也不改四个已有产物。</p>
 */
public final class ReportSummary {

    private final UnsatisfiedRequired unsatisfiedRequired;
    private final DuplicateIdReport duplicateIds;

    private ReportSummary(final UnsatisfiedRequired unsatisfiedRequired,
                          final DuplicateIdReport duplicateIds) {
        this.unsatisfiedRequired = Objects.requireNonNull(unsatisfiedRequired, "unsatisfiedRequired");
        this.duplicateIds = Objects.requireNonNull(duplicateIds, "duplicateIds");
    }

    public static ReportSummary of(final List<ModCandidate> candidates,
                                   final DependencyReport dependencies) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(dependencies, "dependencies");
        return new ReportSummary(
                UnsatisfiedRequired.of(dependencies),
                DuplicateIdReport.of(candidates));
    }

    public UnsatisfiedRequired unsatisfiedRequired() {
        return unsatisfiedRequired;
    }

    public DuplicateIdReport duplicateIds() {
        return duplicateIds;
    }
}
