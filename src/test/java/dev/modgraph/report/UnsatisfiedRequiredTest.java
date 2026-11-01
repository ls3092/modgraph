package dev.modgraph.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.modgraph.resolve.DepKind;
import dev.modgraph.resolve.DependencyOutcome;
import dev.modgraph.resolve.DependencyReport;
import dev.modgraph.resolve.DependencyResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.7.2：{@code unsatisfiedRequired} 的构成。
 *
 * <p>每一类都单独构造一次，证明"算"与"不算"的分界不是靠真实数据的巧合。</p>
 */
class UnsatisfiedRequiredTest {

    @Test
    void onlyRequiredMissingAndVersionMismatchAreCounted() {
        final List<DependencyResult> rows = new ArrayList<>();
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.MISSING_ID));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.MISSING_ID));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.VERSION_MISMATCH));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS));
        rows.add(row(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED));
        rows.add(row(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED));
        rows.add(row(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED));
        rows.add(row(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED));
        for (int i = 0; i < 5; i++) {
            rows.add(row(DepKind.RECOMMENDS, DependencyOutcome.MISSING_ID));
        }
        rows.add(row(DepKind.RECOMMENDS, DependencyOutcome.VERSION_MISMATCH));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.UNPARSEABLE));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.SATISFIED));
        rows.add(row(DepKind.REQUIRED, DependencyOutcome.SATISFIED));

        final UnsatisfiedRequired result = UnsatisfiedRequired.of(new DependencyReport(rows));

        assertThat(result.requiredMissingId()).isEqualTo(2);
        assertThat(result.requiredVersionMismatch()).isEqualTo(1);
        assertThat(result.count()).isEqualTo(3);
        assertThat(result.ambiguousAlias()).isEqualTo(3);
        assertThat(result.breaksViolated()).isEqualTo(4);
        assertThat(result.softMissingId()).isEqualTo(5);
        assertThat(result.softVersionMismatch()).isEqualTo(1);
        assertThat(result.unparseable()).isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(14);
    }

    @Test
    void softVersionMismatchIsPrintedButNeverCounted() {
        final UnsatisfiedRequired result = UnsatisfiedRequired.of(new DependencyReport(List.of(
                row(DepKind.RECOMMENDS, DependencyOutcome.VERSION_MISMATCH),
                row(DepKind.RECOMMENDS, DependencyOutcome.VERSION_MISMATCH))));

        assertThat(result.count()).isZero();
        assertThat(result.softVersionMismatch()).isEqualTo(2);
    }

    @Test
    void aliasIsAToolLimitationNotAnUnsatisfiedDependency() {
        final UnsatisfiedRequired result = UnsatisfiedRequired.of(new DependencyReport(List.of(
                row(DepKind.REQUIRED, DependencyOutcome.AMBIGUOUS_ALIAS))));

        assertThat(result.count()).isZero();
        assertThat(result.ambiguousAlias()).isEqualTo(1);
    }

    @Test
    void breaksViolationAndUnparseableAreNotDependencyFailures() {
        final UnsatisfiedRequired result = UnsatisfiedRequired.of(new DependencyReport(List.of(
                row(DepKind.BREAKS, DependencyOutcome.BREAKS_VIOLATED),
                row(DepKind.REQUIRED, DependencyOutcome.UNPARSEABLE),
                row(DepKind.RECOMMENDS, DependencyOutcome.UNPARSEABLE))));

        assertThat(result.count()).isZero();
        assertThat(result.breaksViolated()).isEqualTo(1);
        assertThat(result.unparseable()).isEqualTo(2);
    }

    @Test
    void aRowOutsideTheClassificationFailsTheRunInsteadOfBeingDropped() {
        final DependencyReport report = new DependencyReport(List.of(
                row(DepKind.RECOMMENDS, DependencyOutcome.AMBIGUOUS_ALIAS)));

        assertThatThrownBy(() -> UnsatisfiedRequired.of(report))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有解释全部依赖行");
    }

    @Test
    void negativeCountsAreRejected() {
        assertThatThrownBy(() -> new UnsatisfiedRequired(-1, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DependencyResult row(final DepKind kind, final DependencyOutcome outcome) {
        return new DependencyResult(
                "sample",
                "1.0",
                "target",
                kind,
                "*",
                outcome == DependencyOutcome.MISSING_ID ? null : "1.0",
                outcome);
    }
}
