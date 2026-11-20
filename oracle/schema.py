"""Static-analysis result schema and integration seams for oracle runs.

This module contains declarations only. It does not import, discover, launch, or
adapt Minecraft or any loader implementation.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping, NotRequired, Protocol, Sequence, TypedDict, runtime_checkable


SCHEMA_REVISION = 1
OPERATING_MODE = "STATIC_ANALYSIS"
IMPLEMENTATIONS = (
    "FORBRIC_ORIGINAL",
    "REPLACEMENT_PRE",
    "REPLACEMENT_POST",
)

CAPABILITY_STATES = ("SUPPORTED", "UNSUPPORTED", "UNKNOWN")
EXECUTION_OUTCOMES = (
    "COMPLETED",
    "CRASHED",
    "TIMEOUT",
    "NOT_ATTEMPTED",
    "BOUNDARY_BLOCKED",
)
OUTPUT_STATES = ("PARSEABLE", "MALFORMED", "ABSENT")
DISCOVERY_ANSWERS = ("FOUND", "REJECTED", "NO_ANSWER", "UNSUPPORTED")
SEMANTIC_RESULTS = ("MATCH", "MISMATCH", "UNASSESSED")
MEASUREMENT_STATES = (
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
DEPENDENCY_OUTCOMES = (
    "SATISFIED",
    "MISSING_ID",
    "VERSION_MISMATCH",
    "AMBIGUOUS_ALIAS",
    "BREAKS_VIOLATED",
    "UNPARSEABLE",
    "UNSUPPORTED",
)
DEPENDENCY_KINDS = ("depends", "recommends", "breaks", "other")


class ImplementationLock(TypedDict):
    implementationId: str
    sourceRevision: str
    buildSha256: str | None
    runnerId: str
    adapterId: str
    normalizerId: str


class RunRecord(TypedDict):
    runId: str
    cohortId: str
    implementation: ImplementationLock
    commandSha256: str | None
    environmentDigest: str
    startedAt: str
    endedAt: str
    processExitCode: int | None
    runOutcome: str
    stdoutSha256: str | None
    stderrSha256: str | None
    rawOutputSha256: str | None
    normalizedOutputSha256: str | None


class DependencyResult(TypedDict):
    dependencyKey: str
    dependencyKind: str
    targetIdRaw: str
    targetIdNormalized: str
    constraintRaw: str | None
    constraintNormalized: str | None
    resolutionStatus: str
    evidenceRef: str


class ItemResult(TypedDict):
    artifactKey: str
    sourceFileName: str
    manifestOrder: int
    capabilityState: str
    executionOutcome: str
    outputState: str
    discoveryAnswer: str
    modId: str | None
    version: str | None
    loaderFamily: str | None
    expectedDependencyKeys: list[str]
    actualDependencyKeys: list[str]
    dependencies: list[DependencyResult]
    evidenceRefs: list[str]
    reasonCode: str
    oracleItemId: str | None
    semanticResult: str
    measurementState: str
    rawOutputSha256: str | None
    normalizedOutputSha256: str | None
    elapsedMs: NotRequired[int]


class ImplementationArtifact(TypedDict):
    schemaRevision: int
    cohortId: str
    operatingMode: str
    implementation: ImplementationLock
    run: RunRecord
    items: list[ItemResult]


REQUIRED_ITEM_FIELDS = (
    "artifactKey",
    "sourceFileName",
    "manifestOrder",
    "capabilityState",
    "executionOutcome",
    "outputState",
    "discoveryAnswer",
    "modId",
    "version",
    "loaderFamily",
    "expectedDependencyKeys",
    "actualDependencyKeys",
    "dependencies",
    "evidenceRefs",
    "reasonCode",
    "oracleItemId",
    "semanticResult",
    "measurementState",
    "rawOutputSha256",
    "normalizedOutputSha256",
)


def _enum(values: Sequence[str]) -> dict[str, Any]:
    return {"type": "string", "enum": list(values)}


_STRING_ARRAY: dict[str, Any] = {"type": "array", "items": {"type": "string"}}
_HASH_OR_NULL: dict[str, Any] = {
    "type": ["string", "null"],
    "pattern": "^[0-9a-f]{64}$",
}

ARTIFACT_JSON_SCHEMA: dict[str, Any] = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "modgraph static implementation result",
    "type": "object",
    "additionalProperties": False,
    "required": [
        "schemaRevision",
        "cohortId",
        "operatingMode",
        "implementation",
        "run",
        "items",
    ],
    "properties": {
        "schemaRevision": {"const": SCHEMA_REVISION},
        "cohortId": {"type": "string", "minLength": 1},
        "operatingMode": {"const": OPERATING_MODE},
        "implementation": {"$ref": "#/$defs/implementation"},
        "run": {"$ref": "#/$defs/run"},
        "items": {
            "type": "array",
            "items": {"$ref": "#/$defs/item"},
        },
    },
    "$defs": {
        "sha256": {
            "type": "string",
            "pattern": "^[0-9a-f]{64}$",
        },
        "implementation": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "implementationId",
                "sourceRevision",
                "buildSha256",
                "runnerId",
                "adapterId",
                "normalizerId",
            ],
            "properties": {
                "implementationId": _enum(IMPLEMENTATIONS),
                "sourceRevision": {"type": "string", "minLength": 1},
                "buildSha256": _HASH_OR_NULL,
                "runnerId": {"type": "string", "minLength": 1},
                "adapterId": {"type": "string", "minLength": 1},
                "normalizerId": {"type": "string", "minLength": 1},
            },
        },
        "run": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "runId",
                "cohortId",
                "implementation",
                "commandSha256",
                "environmentDigest",
                "startedAt",
                "endedAt",
                "processExitCode",
                "runOutcome",
                "stdoutSha256",
                "stderrSha256",
                "rawOutputSha256",
                "normalizedOutputSha256",
            ],
            "properties": {
                "runId": {"type": "string", "minLength": 1},
                "cohortId": {"type": "string", "minLength": 1},
                "implementation": {"$ref": "#/$defs/implementation"},
                "commandSha256": _HASH_OR_NULL,
                "environmentDigest": {"$ref": "#/$defs/sha256"},
                "startedAt": {"type": "string", "minLength": 1},
                "endedAt": {"type": "string", "minLength": 1},
                "processExitCode": {"type": ["integer", "null"]},
                "runOutcome": _enum(EXECUTION_OUTCOMES),
                "stdoutSha256": _HASH_OR_NULL,
                "stderrSha256": _HASH_OR_NULL,
                "rawOutputSha256": _HASH_OR_NULL,
                "normalizedOutputSha256": _HASH_OR_NULL,
            },
        },
        "dependency": {
            "type": "object",
            "additionalProperties": False,
            "required": [
                "dependencyKey",
                "dependencyKind",
                "targetIdRaw",
                "targetIdNormalized",
                "constraintRaw",
                "constraintNormalized",
                "resolutionStatus",
                "evidenceRef",
            ],
            "properties": {
                "dependencyKey": {"type": "string", "minLength": 1},
                "dependencyKind": _enum(DEPENDENCY_KINDS),
                "targetIdRaw": {"type": "string"},
                "targetIdNormalized": {"type": "string"},
                "constraintRaw": {"type": ["string", "null"]},
                "constraintNormalized": {"type": ["string", "null"]},
                "resolutionStatus": _enum(DEPENDENCY_OUTCOMES),
                "evidenceRef": {"type": "string", "minLength": 1},
            },
        },
        "item": {
            "type": "object",
            "additionalProperties": False,
            "required": list(REQUIRED_ITEM_FIELDS),
            "properties": {
                "artifactKey": {"$ref": "#/$defs/sha256"},
                "sourceFileName": {"type": "string", "minLength": 1},
                "manifestOrder": {"type": "integer", "minimum": 1},
                "capabilityState": _enum(CAPABILITY_STATES),
                "executionOutcome": _enum(EXECUTION_OUTCOMES),
                "outputState": _enum(OUTPUT_STATES),
                "discoveryAnswer": _enum(DISCOVERY_ANSWERS),
                "modId": {"type": ["string", "null"]},
                "version": {"type": ["string", "null"]},
                "loaderFamily": {"type": ["string", "null"]},
                "expectedDependencyKeys": _STRING_ARRAY,
                "actualDependencyKeys": _STRING_ARRAY,
                "dependencies": {
                    "type": "array",
                    "items": {"$ref": "#/$defs/dependency"},
                },
                "evidenceRefs": _STRING_ARRAY,
                "reasonCode": {"type": "string", "minLength": 1},
                "oracleItemId": {"type": ["string", "null"]},
                "semanticResult": _enum(SEMANTIC_RESULTS),
                "measurementState": _enum(MEASUREMENT_STATES),
                "rawOutputSha256": _HASH_OR_NULL,
                "normalizedOutputSha256": _HASH_OR_NULL,
                "elapsedMs": {"type": "integer", "minimum": 0},
            },
        },
    },
}


@runtime_checkable
class Runner(Protocol):
    """Produces an untrusted raw artifact for one locked implementation."""

    implementation_id: str

    def run(
        self,
        *,
        cohort_path: Path,
        run_id: str,
        output_dir: Path,
    ) -> Mapping[str, Any]:
        """Execute the separately approved runner and return raw metadata only."""


@runtime_checkable
class Adapter(Protocol):
    """Projects raw runner output into this module's JSON object shape."""

    adapter_id: str

    def adapt(
        self,
        *,
        raw_output: Mapping[str, Any],
        cohort_id: str,
        implementation_id: str,
    ) -> Mapping[str, Any]:
        """Return an implementation artifact conforming to ARTIFACT_JSON_SCHEMA."""


@runtime_checkable
class Normalizer(Protocol):
    """Applies a frozen, implementation-independent normalization revision."""

    normalizer_id: str

    def normalize(
        self,
        *,
        item: Mapping[str, Any],
        dependency_rows: Sequence[Mapping[str, Any]],
    ) -> Mapping[str, Any]:
        """Return one normalized item without consulting another implementation."""


def schema() -> dict[str, Any]:
    """Return a fresh serializable copy of the result schema."""

    import copy

    return copy.deepcopy(ARTIFACT_JSON_SCHEMA)
