"""Constructed-data checks for the oracle bucket closure functions.

Run with:
    python oracle/selftest.py

This test does not scan the filesystem for jars and does not start a process.
"""

from __future__ import annotations

import sys
from typing import Callable

sys.dont_write_bytecode = True

try:
    from .buckets import (
        DEPENDENCY_BUCKETS,
        EIGHT_PATTERN_KEYS,
        EightPatternSummary,
        FOUR_BUCKETS,
        MAIN_BUCKETS,
        PAIR_KEYS,
        BucketClosureError,
        assert_all_closures,
        assert_orthogonal_partitions,
        eight_pattern_key,
        summarize_dependency_buckets,
        summarize_eight_patterns,
        summarize_four_buckets,
        summarize_main_buckets,
        summarize_pairwise,
    )
    from .schema import IMPLEMENTATIONS, REQUIRED_ITEM_FIELDS
except ImportError:
    from buckets import (
        DEPENDENCY_BUCKETS,
        EIGHT_PATTERN_KEYS,
        EightPatternSummary,
        FOUR_BUCKETS,
        MAIN_BUCKETS,
        PAIR_KEYS,
        BucketClosureError,
        assert_all_closures,
        assert_orthogonal_partitions,
        eight_pattern_key,
        summarize_dependency_buckets,
        summarize_eight_patterns,
        summarize_four_buckets,
        summarize_main_buckets,
        summarize_pairwise,
    )
    from schema import IMPLEMENTATIONS, REQUIRED_ITEM_FIELDS


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def require_equal(actual: object, expected: object, label: str) -> None:
    if actual != expected:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def require_close(actual: float, expected: float, label: str) -> None:
    if abs(actual - expected) > 1e-12:
        raise AssertionError(f"{label}: expected {expected!r}, got {actual!r}")


def require_raises(exception_type: type[BaseException], fn: Callable[[], object]) -> None:
    try:
        fn()
    except exception_type:
        return
    except Exception as exc:
        raise AssertionError(
            f"expected {exception_type.__name__}, got {type(exc).__name__}: {exc}"
        ) from exc
    raise AssertionError(f"expected {exception_type.__name__}, but no exception was raised")


MAIN_COUNTS = {
    "CORRECT": 4,
    "INCORRECT": 2,
    "UNSUPPORTED": 1,
    "NO_PARSEABLE_OUTPUT": 1,
    "TOOL_FAILURE": 1,
    "TIMEOUT": 1,
    "NOT_ATTEMPTED": 1,
    "BOUNDARY_BLOCKED": 1,
    "ORACLE_MISSING": 2,
}

PAIR_COUNTS = {
    "ORIGINAL_PRE": {"agree": 8, "disagree": 3, "common": 11},
    "ORIGINAL_POST": {"agree": 7, "disagree": 4, "common": 11},
    "PRE_POST": {"agree": 9, "disagree": 3, "common": 12},
}

FOUR_COUNTS = {
    "BOTH_CORRECT": 6,
    "PRE_ONLY_CORRECT": 3,
    "POST_ONLY_CORRECT": 2,
    "BOTH_INCORRECT": 1,
}

EIGHT_COUNTS = {
    key: 1
    for key in EIGHT_PATTERN_KEYS
}
EIGHT_COUNTS[eight_pattern_key(True, True, True)] = 5

DEPENDENCY_COUNTS = {
    "D_EXACT": 10,
    "D_WRONG_OUTCOME": 2,
    "D_MISSING": 1,
    "D_UNAVAILABLE": 1,
}


def check_main_total() -> None:
    summary = summarize_main_buckets(MAIN_COUNTS)
    require_equal(summary.n, 14, "N")
    require_equal(summary.counts, MAIN_COUNTS, "main bucket copy")


def check_n_eval() -> None:
    summary = summarize_main_buckets(MAIN_COUNTS)
    require_equal(summary.n_eval, 12, "N_eval")


def check_n_answered() -> None:
    summary = summarize_main_buckets(MAIN_COUNTS)
    require_equal(summary.n_answered, 6, "N_answered")


def check_accuracy_denominator() -> None:
    summary = summarize_main_buckets(MAIN_COUNTS)
    require(summary.accuracy is not None, "accuracy unexpectedly missing")
    require_close(summary.accuracy, 4 / 6, "accuracy")
    require(summary.accuracy != 4 / 12, "accuracy must not use N_eval")
    require(summary.accuracy != 4 / 14, "accuracy must not use N")


def check_zero_answer_accuracy() -> None:
    counts = dict.fromkeys(MAIN_BUCKETS, 0)
    counts["UNSUPPORTED"] = 1
    summary = summarize_main_buckets(counts)
    require_equal(summary.n_answered, 0, "zero-answer denominator")
    require_equal(summary.accuracy, None, "zero-answer accuracy")


def check_negative_rejected() -> None:
    counts = dict(MAIN_COUNTS)
    counts["CORRECT"] = -1
    require_raises(BucketClosureError, lambda: summarize_main_buckets(counts))


def check_missing_bucket_rejected() -> None:
    counts = dict(MAIN_COUNTS)
    del counts["CORRECT"]
    require_raises(BucketClosureError, lambda: summarize_main_buckets(counts))


def check_pair_count() -> None:
    summary = summarize_pairwise(PAIR_COUNTS)
    require_equal(tuple(summary.counts_by_pair), PAIR_KEYS, "pair order")
    require_equal(len(summary.counts_by_pair), 3, "pair count")


def check_pair_closure() -> None:
    summary = summarize_pairwise(PAIR_COUNTS)
    for pair, counts in summary.counts_by_pair.items():
        require_equal(counts["agree"] + counts["disagree"], counts["common"], pair)


def check_missing_pair_rejected() -> None:
    counts = dict(PAIR_COUNTS)
    del counts["PRE_POST"]
    require_raises(BucketClosureError, lambda: summarize_pairwise(counts))


def check_pair_mismatch_rejected() -> None:
    counts = {key: dict(value) for key, value in PAIR_COUNTS.items()}
    counts["ORIGINAL_PRE"]["disagree"] += 1
    require_raises(BucketClosureError, lambda: summarize_pairwise(counts))


def check_four_bucket_closure() -> None:
    summary = summarize_four_buckets(FOUR_COUNTS, 12)
    require_equal(sum(summary.counts.values()), 12, "4-bucket total")


def check_four_bucket_mismatch_rejected() -> None:
    require_raises(
        BucketClosureError,
        lambda: summarize_four_buckets(FOUR_COUNTS, 13),
    )


def check_eight_pattern_closure() -> None:
    summary = summarize_eight_patterns(EIGHT_COUNTS, 12)
    require_equal(sum(summary.counts.values()), 12, "8-pattern total")


def check_eight_pattern_extra_rejected() -> None:
    counts = dict(EIGHT_COUNTS)
    counts["ORIGINAL_X_PRE_X_POST_X"] = 1
    require_raises(
        BucketClosureError,
        lambda: summarize_eight_patterns(counts, 12),
    )


def check_orthogonality() -> None:
    four = summarize_four_buckets(FOUR_COUNTS, 12)
    eight = summarize_eight_patterns(EIGHT_COUNTS, 12)
    summary = assert_orthogonal_partitions(four, eight)
    require_equal(summary.four_bucket_total, 12, "4-bucket orthogonal total")
    require_equal(summary.eight_pattern_total, 12, "8-pattern orthogonal total")
    require_equal(summary.n_common_3, 12, "N_common_3")


def check_orthogonal_mismatch_rejected() -> None:
    four = summarize_four_buckets(FOUR_COUNTS, 12)
    eight = summarize_eight_patterns(EIGHT_COUNTS, 12)
    eight_with_other_denominator = EightPatternSummary(
        counts=eight.counts,
        n_common_3=13,
    )
    require_raises(
        BucketClosureError,
        lambda: assert_orthogonal_partitions(four, eight_with_other_denominator),
    )
    require_equal(four.n_common_3, eight.n_common_3, "valid 4/8 denominator")


def check_dependency_closure() -> None:
    summary = summarize_dependency_buckets(
        DEPENDENCY_COUNTS,
        expected=14,
        extra=3,
    )
    require_equal(sum(summary.counts.values()), 14, "D_expected total")
    require_equal(summary.extra, 3, "D_extra")


def check_dependency_mismatch_rejected() -> None:
    require_raises(
        BucketClosureError,
        lambda: summarize_dependency_buckets(
            DEPENDENCY_COUNTS,
            expected=15,
        ),
    )


def check_dependency_extra_not_folded() -> None:
    summary = summarize_dependency_buckets(
        DEPENDENCY_COUNTS,
        expected=14,
        extra=3,
    )
    require_equal(summary.expected, 14, "D_expected unchanged by D_extra")
    require(summary.extra != summary.expected, "D_extra must remain separate")


def check_all_closures() -> None:
    report = assert_all_closures(
        main_counts=MAIN_COUNTS,
        pairwise_counts=PAIR_COUNTS,
        four_bucket_counts=FOUR_COUNTS,
        eight_pattern_counts=EIGHT_COUNTS,
        n_common_3=12,
        dependency_counts=DEPENDENCY_COUNTS,
        dependency_expected=14,
        dependency_extra=3,
    )
    require_equal(report.main.n, 14, "all-closures N")
    require_equal(report.main.n_eval, 12, "all-closures N_eval")
    require_equal(report.main.n_answered, 6, "all-closures N_answered")
    require_equal(report.orthogonality.four_bucket_total, 12, "all-closures 4")
    require_equal(report.orthogonality.eight_pattern_total, 12, "all-closures 8")
    require_equal(report.dependencies.expected, 14, "all-closures D_expected")


def check_curation_error_rejected() -> None:
    require_raises(
        BucketClosureError,
        lambda: assert_all_closures(
            main_counts=MAIN_COUNTS,
            pairwise_counts=PAIR_COUNTS,
            four_bucket_counts=FOUR_COUNTS,
            eight_pattern_counts=EIGHT_COUNTS,
            n_common_3=12,
            dependency_counts=DEPENDENCY_COUNTS,
            dependency_expected=14,
            dependency_extra=3,
            input_curation_error=1,
        ),
    )


def check_schema_implementations() -> None:
    require_equal(
        tuple(IMPLEMENTATIONS),
        ("FORBRIC_ORIGINAL", "REPLACEMENT_PRE", "REPLACEMENT_POST"),
        "implementation ids",
    )


def check_schema_required_fields() -> None:
    required = {
        "artifactKey",
        "capabilityState",
        "executionOutcome",
        "outputState",
        "discoveryAnswer",
        "modId",
        "version",
        "loaderFamily",
        "expectedDependencyKeys",
        "actualDependencyKeys",
        "evidenceRefs",
        "reasonCode",
        "oracleItemId",
        "rawOutputSha256",
        "normalizedOutputSha256",
    }
    require(required.issubset(set(REQUIRED_ITEM_FIELDS)), "required item fields incomplete")


CHECKS: tuple[tuple[str, Callable[[], None]], ...] = (
    ("main_bucket_total_9", check_main_total),
    ("n_eval_formula", check_n_eval),
    ("n_answered_formula", check_n_answered),
    ("accuracy_denominator", check_accuracy_denominator),
    ("accuracy_zero_denominator", check_zero_answer_accuracy),
    ("negative_count_rejected", check_negative_rejected),
    ("missing_bucket_rejected", check_missing_bucket_rejected),
    ("pair_count_exactly_3", check_pair_count),
    ("pair_closure", check_pair_closure),
    ("missing_pair_rejected", check_missing_pair_rejected),
    ("pair_mismatch_rejected", check_pair_mismatch_rejected),
    ("four_bucket_closure", check_four_bucket_closure),
    ("four_bucket_mismatch_rejected", check_four_bucket_mismatch_rejected),
    ("eight_pattern_closure", check_eight_pattern_closure),
    ("eight_pattern_extra_rejected", check_eight_pattern_extra_rejected),
    ("orthogonal_partitions", check_orthogonality),
    ("orthogonal_mismatch_rejected", check_orthogonal_mismatch_rejected),
    ("dependency_closure", check_dependency_closure),
    ("dependency_mismatch_rejected", check_dependency_mismatch_rejected),
    ("dependency_extra_separate", check_dependency_extra_not_folded),
    ("all_closures", check_all_closures),
    ("input_curation_error_rejected", check_curation_error_rejected),
    ("schema_implementations", check_schema_implementations),
    ("schema_required_fields", check_schema_required_fields),
)


def main() -> int:
    total = len(CHECKS)
    passed = 0
    for name, check in CHECKS:
        try:
            check()
        except Exception as exc:
            print("SELFTEST_STATUS=FAILED")
            print(f"SELFTEST_ASSERTIONS_TOTAL={total}")
            print(f"SELFTEST_ASSERTIONS_PASSED={passed}")
            print("SELFTEST_ASSERTIONS_FAILED=1")
            print(f"SELFTEST_ASSERTIONS_NOT_RUN={total - passed - 1}")
            print(f"SELFTEST_FAILED_CASE={name}")
            print(f"SELFTEST_RAW_FAILURE={exc!r}")
            return 1
        passed += 1

    print("SELFTEST_STATUS=PASSED")
    print(f"SELFTEST_ASSERTIONS_TOTAL={total}")
    print(f"SELFTEST_ASSERTIONS_PASSED={passed}")
    print("SELFTEST_ASSERTIONS_FAILED=0")
    print("SELFTEST_ASSERTIONS_NOT_RUN=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
