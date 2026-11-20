"""Launch-gate adapter for the FORBRIC_ORIGINAL experiment.

The material verifier must provide an exact build and launch command. This
module never guesses an entry point and does not substitute a nearby Minecraft
version. When material is blocked, it emits the five requested item records as
BOUNDARY_BLOCKED with raw blocker references.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping

try:
    from .buckets import summarize_main_buckets
except ImportError:
    from buckets import summarize_main_buckets


SCHEMA_REVISION = 1
TAKE_COUNT = 5


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_sha256(value: object) -> str:
    return sha256_bytes(
        json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
    )


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_bytes())
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected a JSON object")
    return value


def write_json(path: Path, value: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def build_blocked_artifact(
    cohort: Mapping[str, Any],
    baseline: Mapping[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    artifacts = cohort.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) < TAKE_COUNT:
        raise ValueError(f"cohort must contain at least {TAKE_COUNT} artifacts")
    selected = artifacts[:TAKE_COUNT]
    blockers = baseline.get("blockers")
    if baseline.get("status") != "BOUNDARY_BLOCKED" or not isinstance(blockers, list):
        raise ValueError("run_original currently requires a verified BOUNDARY_BLOCKED material report")

    blocker_codes = [
        blocker.get("code", "UNKNOWN_BLOCKER")
        for blocker in blockers
        if isinstance(blocker, dict)
    ]
    reason = ",".join(str(code) for code in blocker_codes)
    source = baseline.get("source", {})
    environment = baseline.get("environment", {})
    now = datetime.now(timezone.utc).isoformat()
    implementation = {
        "implementationId": "FORBRIC_ORIGINAL",
        "sourceRevision": source.get("requestedCommit", "UNKNOWN"),
        "buildSha256": None,
        "runnerId": "material-gated-original",
        "adapterId": "oracle/run_original.py@1",
        "normalizerId": "oracle/schema.py@1",
    }
    run = {
        "runId": "blocked-first5-" + canonical_sha256(
            {
                "cohortId": cohort.get("cohortId"),
                "blockerCodes": blocker_codes,
            }
        )[:16],
        "cohortId": cohort.get("cohortId"),
        "implementation": implementation,
        "commandSha256": None,
        "environmentDigest": canonical_sha256(environment),
        "startedAt": now,
        "endedAt": now,
        "processExitCode": None,
        "runOutcome": "BOUNDARY_BLOCKED",
        "stdoutSha256": None,
        "stderrSha256": None,
        "rawOutputSha256": None,
        "normalizedOutputSha256": None,
    }

    items: list[dict[str, Any]] = []
    for order, artifact in enumerate(selected, start=1):
        item = {
            "artifactKey": artifact["sha256"],
            "sourceFileName": artifact["fileName"],
            "manifestOrder": order,
            "capabilityState": "UNKNOWN",
            "executionOutcome": "BOUNDARY_BLOCKED",
            "outputState": "ABSENT",
            "discoveryAnswer": "NO_ANSWER",
            "modId": None,
            "version": None,
            "loaderFamily": artifact.get("loader"),
            "expectedDependencyKeys": [],
            "actualDependencyKeys": [],
            "dependencies": [],
            "evidenceRefs": [
                "forbric-comparison/original-baseline.json#/blockers",
                "forbric-comparison/original-baseline.json#/build/probes",
                "forbric-comparison/original-baseline.json#/minecraftBinary/probes",
            ],
            "reasonCode": reason,
            "oracleItemId": None,
            "semanticResult": "UNASSESSED",
            "measurementState": "BOUNDARY_BLOCKED",
            "rawOutputSha256": None,
            "normalizedOutputSha256": None,
        }
        item["normalizedOutputSha256"] = canonical_sha256(item)
        items.append(item)

    counts = {
        "CORRECT": 0,
        "INCORRECT": 0,
        "UNSUPPORTED": 0,
        "NO_PARSEABLE_OUTPUT": 0,
        "TOOL_FAILURE": 0,
        "TIMEOUT": 0,
        "NOT_ATTEMPTED": 0,
        "BOUNDARY_BLOCKED": len(items),
        "ORACLE_MISSING": 0,
    }
    summary = summarize_main_buckets(counts)
    artifact_output = {
        "schemaRevision": SCHEMA_REVISION,
        "cohortId": cohort.get("cohortId"),
        "operatingMode": "LOADER_EXPERIMENT",
        "implementation": implementation,
        "run": run,
        "items": items,
    }
    summary_output = {
        "schemaRevision": SCHEMA_REVISION,
        "producedBy": "oracle/run_original.py",
        "cohortId": cohort.get("cohortId"),
        "selection": {
            "rule": "first five records in frozen cohort order",
            "count": TAKE_COUNT,
            "sha256": [item["artifactKey"] for item in items],
        },
        "launchAttempt": {
            "attempted": True,
            "processStarted": False,
            "reason": "exact Forbric build and Minecraft 26.2 binary unavailable",
            "buildEntry": baseline.get("build", {}).get("entry"),
            "commandTemplate": baseline.get("build", {}).get("commandTemplate"),
            "buildProducts": baseline.get("build", {}).get("products"),
            "buildProbes": baseline.get("build", {}).get("probes"),
            "minecraftBinaryProbes": baseline.get("minecraftBinary", {}).get("probes"),
            "exactBlockers": blockers,
        },
        "buckets": counts,
        "derived": {
            "N": summary.n,
            "N_eval": summary.n_eval,
            "N_answered": summary.n_answered,
            "accuracy": summary.accuracy,
            "inputCurationError": 0,
        },
        "assertions": {
            "nineBucketSumEqualsN": summary.n == sum(counts.values()),
            "nEvalFormula": summary.n_eval == summary.n - counts["ORACLE_MISSING"],
            "nAnsweredFormula": summary.n_answered
            == counts["CORRECT"] + counts["INCORRECT"],
            "accuracyUsesAnsweredOnly": summary.n_answered == 0
            and summary.accuracy is None,
            "allSelectedItemsBoundaryBlocked": all(
                item["measurementState"] == "BOUNDARY_BLOCKED" for item in items
            ),
        },
        "items": [
            {
                "manifestOrder": item["manifestOrder"],
                "sha256": item["artifactKey"],
                "fileName": item["sourceFileName"],
                "technicalState": item["executionOutcome"],
                "capabilityState": item["capabilityState"],
                "outputState": item["outputState"],
                "measurementState": item["measurementState"],
                "rawOutputSha256": item["rawOutputSha256"],
            }
            for item in items
        ],
    }
    return artifact_output, summary_output


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--cohort",
        type=Path,
        default=Path(__file__).resolve().with_name("cohort.json"),
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "forbric-comparison/original-baseline.json",
    )
    parser.add_argument(
        "--artifact-output",
        type=Path,
        default=Path(__file__).resolve().with_name("forbric-original-first5.json"),
    )
    parser.add_argument(
        "--summary-output",
        type=Path,
        default=Path(__file__).resolve().with_name(
            "forbric-original-first5-buckets.json"
        ),
    )
    args = parser.parse_args(argv)

    try:
        cohort = load_json(args.cohort.resolve())
        baseline = load_json(args.baseline.resolve())
        artifact, summary = build_blocked_artifact(cohort, baseline)
        if not all(summary["assertions"].values()):
            raise ValueError(f"summary assertions failed: {summary['assertions']}")
        write_json(args.artifact_output.resolve(), artifact)
        write_json(args.summary_output.resolve(), summary)
    except Exception as exc:
        print(f"ORIGINAL_RUN_STATUS=FAILED\nRAW_FAILURE={exc!r}")
        return 1

    print("ORIGINAL_RUN_STATUS=BOUNDARY_BLOCKED")
    print(
        f"N={summary['derived']['N']} "
        f"N_eval={summary['derived']['N_eval']} "
        f"N_answered={summary['derived']['N_answered']} "
        f"accuracy={summary['derived']['accuracy']}"
    )
    print(
        "BUCKETS="
        + ",".join(f"{key}:{value}" for key, value in summary["buckets"].items())
    )
    print(
        f"ASSERTIONS_PASSED={sum(summary['assertions'].values())}/"
        f"{len(summary['assertions'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
