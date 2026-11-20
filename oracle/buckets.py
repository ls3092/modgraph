"""Pure closure functions for static implementation comparisons.

No function reads files, launches processes, or uses a Minecraft/loader API.
All counts are supplied by callers after the corresponding run and adapter have
completed.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping, Sequence


MAIN_BUCKETS = (
    "CORRECT",
    "INCORRECT",
    "UNSUPPORTED",
    "NO_PARSEABLE_OUTPUT",
    "TOOL_FAILURE",
    "TIMEOUT",
    "NOT_ATTEMPTED",
    "BOUNDARY_BLOCKED",
    "ORACLE_MISSING",
)

FOUR_BUCKETS = (
    "BOTH_CORRECT",
    "PRE_ONLY_CORRECT",
    "POST_ONLY_CORRECT",
    "BOTH_INCORRECT",
)

PAIR_KEYS = (
    "ORIGINAL_PRE",
    "ORIGINAL_POST",
    "PRE_POST",
)

DEPENDENCY_BUCKETS = (
    "D_EXACT",
    "D_WRONG_OUTCOME",
    "D_MISSING",
    "D_UNAVAILABLE",
)


def eight_pattern_key(
    original_correct: bool,
    pre_correct: bool,
    post_correct: bool,
) -> str:
    """Return the stable key for one three-implementation correctness pattern."""

    def bit(value: bool) -> str:
        return "C" if value else "I"

    return (
        f"ORIGINAL_{bit(original_correct)}"
        f"_PRE_{bit(pre_correct)}"
        f"_POST_{bit(post_correct)}"
    )


EIGHT_PATTERN_KEYS = tuple(
    eight_pattern_key(original, pre, post)
    for original in (False, True)
    for pre in (False, True)
    for post in (False, True)
)


class BucketClosureError(ValueError):
    """Raised when a declared count vector does not close."""


@dataclass(frozen=True)
class MainBucketSummary:
    counts: dict[str, int]
    n: int
    n_eval: int
    n_answered: int
    accuracy: float | None


@dataclass(frozen=True)
class PairwiseBucketSummary:
    counts_by_pair: dict[str, dict[str, int]]


@dataclass(frozen=True)
class FourBucketSummary:
    counts: dict[str, int]
    n_common_3: int


@dataclass(frozen=True)
class EightPatternSummary:
    counts: dict[str, int]
    n_common_3: int


@dataclass(frozen=True)
class DependencyBucketSummary:
    counts: dict[str, int]
    expected: int
    extra: int


@dataclass(frozen=True)
class OrthogonalitySummary:
    four_bucket_total: int
    eight_pattern_total: int
    n_common_3: int


@dataclass(frozen=True)
class ClosureReport:
    main: MainBucketSummary
    pairwise: PairwiseBucketSummary
    four_bucket: FourBucketSummary
    eight_pattern: EightPatternSummary
    orthogonality: OrthogonalitySummary
    dependencies: DependencyBucketSummary


def _non_negative_int(value: object, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise BucketClosureError(f"{label} must be an integer; got {value!r}")
    if value < 0:
        raise BucketClosureError(f"{label} must be >= 0; got {value}")
    return value


def _exact_counts(
    counts: Mapping[str, int],
    required_keys: Sequence[str],
    label: str,
) -> dict[str, int]:
    if not isinstance(counts, Mapping):
        raise BucketClosureError(f"{label} must be a mapping")
    actual = set(counts)
    required = set(required_keys)
    if actual != required:
        missing = sorted(required - actual)
        extra = sorted(actual - required)
        raise BucketClosureError(
            f"{label} keys do not match; missing={missing}, extra={extra}"
        )
    return {
        key: _non_negative_int(counts[key], f"{label}.{key}")
        for key in required_keys
    }


def summarize_main_buckets(counts: Mapping[str, int]) -> MainBucketSummary:
    """Validate the 9 main buckets and derive N, N_eval, N_answered and accuracy."""

    checked = _exact_counts(counts, MAIN_BUCKETS, "main buckets")
    n = sum(checked.values())
    n_eval = n - checked["ORACLE_MISSING"]
    n_answered = checked["CORRECT"] + checked["INCORRECT"]
    if n_eval < n_answered:
        raise BucketClosureError(
            f"N_eval ({n_eval}) must be >= N_answered ({n_answered})"
        )
    accuracy = None if n_answered == 0 else checked["CORRECT"] / n_answered
    return MainBucketSummary(
        counts=checked,
        n=n,
        n_eval=n_eval,
        n_answered=n_answered,
        accuracy=accuracy,
    )


def summarize_pairwise(
    counts_by_pair: Mapping[str, Mapping[str, int]],
) -> PairwiseBucketSummary:
    """Require all three implementation pairs and AGREE + DISAGREE == N_common."""

    if set(counts_by_pair) != set(PAIR_KEYS):
        missing = sorted(set(PAIR_KEYS) - set(counts_by_pair))
        extra = sorted(set(counts_by_pair) - set(PAIR_KEYS))
        raise BucketClosureError(
            f"pair keys do not match; missing={missing}, extra={extra}"
        )

    checked: dict[str, dict[str, int]] = {}
    for pair in PAIR_KEYS:
        row = counts_by_pair[pair]
        values = _exact_counts(row, ("agree", "disagree", "common"), f"pair {pair}")
        expected_common = values["agree"] + values["disagree"]
        if values["common"] != expected_common:
            raise BucketClosureError(
                f"pair {pair}: agree + disagree ({expected_common}) "
                f"!= common ({values['common']})"
            )
        checked[pair] = values
    return PairwiseBucketSummary(counts_by_pair=checked)


def summarize_four_buckets(
    counts: Mapping[str, int],
    n_common_3: int,
) -> FourBucketSummary:
    """Validate the four-cell PRE/POST correctness partition."""

    checked = _exact_counts(counts, FOUR_BUCKETS, "four buckets")
    common = _non_negative_int(n_common_3, "N_common_3")
    total = sum(checked.values())
    if total != common:
        raise BucketClosureError(
            f"4-bucket total ({total}) != N_common_3 ({common})"
        )
    return FourBucketSummary(counts=checked, n_common_3=common)


def summarize_eight_patterns(
    counts: Mapping[str, int],
    n_common_3: int,
) -> EightPatternSummary:
    """Validate the eight-cell three-implementation correctness partition."""

    checked = _exact_counts(counts, EIGHT_PATTERN_KEYS, "eight patterns")
    common = _non_negative_int(n_common_3, "N_common_3")
    total = sum(checked.values())
    if total != common:
        raise BucketClosureError(
            f"8-pattern total ({total}) != N_common_3 ({common})"
        )
    return EightPatternSummary(counts=checked, n_common_3=common)


def assert_orthogonal_partitions(
    four: FourBucketSummary,
    eight: EightPatternSummary,
) -> OrthogonalitySummary:
    """Check both partitions cover the same intersection without combining them."""

    four_total = sum(four.counts.values())
    eight_total = sum(eight.counts.values())
    if four.n_common_3 != eight.n_common_3:
        raise BucketClosureError(
            "4-bucket and 8-pattern denominators differ: "
            f"{four.n_common_3} != {eight.n_common_3}"
        )
    if four_total != eight_total:
        raise BucketClosureError(
            f"4-bucket and 8-pattern totals differ: {four_total} != {eight_total}"
        )
    return OrthogonalitySummary(
        four_bucket_total=four_total,
        eight_pattern_total=eight_total,
        n_common_3=four.n_common_3,
    )


def summarize_dependency_buckets(
    counts: Mapping[str, int],
    *,
    expected: int,
    extra: int = 0,
) -> DependencyBucketSummary:
    """Validate D_expected closure while keeping D_extra outside that denominator."""

    checked = _exact_counts(counts, DEPENDENCY_BUCKETS, "dependency buckets")
    expected_count = _non_negative_int(expected, "D_expected")
    extra_count = _non_negative_int(extra, "D_extra")
    total = sum(checked.values())
    if total != expected_count:
        raise BucketClosureError(
            f"dependency bucket total ({total}) != D_expected ({expected_count})"
        )
    return DependencyBucketSummary(
        counts=checked,
        expected=expected_count,
        extra=extra_count,
    )


def assert_all_closures(
    *,
    main_counts: Mapping[str, int],
    pairwise_counts: Mapping[str, Mapping[str, int]],
    four_bucket_counts: Mapping[str, int],
    eight_pattern_counts: Mapping[str, int],
    n_common_3: int,
    dependency_counts: Mapping[str, int],
    dependency_expected: int,
    dependency_extra: int = 0,
    input_curation_error: int = 0,
) -> ClosureReport:
    """Apply all C-path closure assertions and return the checked components."""

    curation_errors = _non_negative_int(
        input_curation_error, "INPUT_CURATION_ERROR"
    )
    if curation_errors:
        raise BucketClosureError(
            f"INPUT_CURATION_ERROR must be 0 for a valid report; got {curation_errors}"
        )

    main = summarize_main_buckets(main_counts)
    pairwise = summarize_pairwise(pairwise_counts)
    four = summarize_four_buckets(four_bucket_counts, n_common_3)
    eight = summarize_eight_patterns(eight_pattern_counts, n_common_3)
    orthogonality = assert_orthogonal_partitions(four, eight)
    dependencies = summarize_dependency_buckets(
        dependency_counts,
        expected=dependency_expected,
        extra=dependency_extra,
    )
    return ClosureReport(
        main=main,
        pairwise=pairwise,
        four_bucket=four,
        eight_pattern=eight,
        orthogonality=orthogonality,
        dependencies=dependencies,
    )
