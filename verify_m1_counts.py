#!/usr/bin/env python3
"""M1 node/edge/bucket verification (SPEC v1.0 section 3.6).

Reads candidates.jsonl (read-only), rebuilds the loader universes of SPEC 3.6.2,
classifies every hard-dependency declaration into exactly one of the five buckets
of SPEC 3.6.3, and prints node counts, distinct edge counts and bucket totals.

The script also reproduces the wrong FABRIC edge figure 85 that appeared in the
step-17 preview, so the cause is visible instead of guessed:
  68 distinct depends edges + 17 alias-to-owner edges = 85.
Alias-only declarations must never become edges (SPEC 3.6.3, bucket ALIAS_ONLY),
and the EDGE bucket counts declarations (69), not distinct node pairs (68).

Usage:
    python verify_m1_counts.py [path/to/candidates.jsonl]

Exit code 0 only when every invariant below holds:
  - every record is status OK or FAILED, and OK + FAILED == number of records
  - for every universe: EDGE + ENVIRONMENT + OTHER_UNIVERSE + ALIAS_ONLY + ABSENT
    == number of hard declarations in that universe
  - sum of the five buckets over all universes == total number of hard declarations

No third-party module is used, and nothing is written to disk.
"""

import collections
import hashlib
import json
import sys

UNIVERSES = ("FABRIC", "FORGE", "NEOFORGE")
BUCKETS = ("EDGE", "ENVIRONMENT", "OTHER_UNIVERSE", "ALIAS_ONLY", "ABSENT")

# SPEC 3.5.2: the six environment IDs plus the three aliases of EnvironmentFacts.
ENVIRONMENT_IDS = {
    "minecraft",
    "fabricloader",
    "quilt_loader",
    "neoforge",
    "forge",
    "java",
    "minecraft_version",
    "fabric-loader",
    "quilt-loader",
}


def universe_of(loader_family):
    """SPEC 3.6.2: QUILT folds into FABRIC; FORGE and NEOFORGE stay apart; UNKNOWN is excluded."""
    if loader_family in ("FABRIC", "QUILT"):
        return "FABRIC"
    if loader_family in ("FORGE", "NEOFORGE"):
        return loader_family
    return None


def load(path):
    records = []
    with open(path, encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError as error:
                raise SystemExit(
                    "candidates.jsonl line %d is not valid JSON: %s" % (line_number, error)
                )
    return records


def main(argv):
    path = argv[1] if len(argv) > 1 else "candidates.jsonl"

    with open(path, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    records = load(path)

    ok = [record for record in records if record["status"] == "OK"]
    failed = [record for record in records if record["status"] == "FAILED"]
    print("input file    : %s" % path)
    print("sha256        : %s" % digest)
    print("records       : %d (OK %d, FAILED %d)" % (len(records), len(ok), len(failed)))
    if len(ok) + len(failed) != len(records):
        print("FAIL: OK + FAILED != records")
        return 1
    print()

    families = collections.Counter(record["loaderFamily"] for record in ok)
    print("OK records by loaderFamily:")
    for family in sorted(families):
        print("  %-8s %d" % (family, families[family]))
    print()

    nodes = {universe: set() for universe in UNIVERSES}
    aliases = {universe: set() for universe in UNIVERSES}
    owners = {universe: collections.defaultdict(set) for universe in UNIVERSES}
    hard = collections.Counter()
    excluded = 0

    for record in ok:
        universe = universe_of(record["loaderFamily"])
        if universe is None:
            excluded += 1
            continue
        nodes[universe].add(record["modId"])
        aliases[universe].update(record["provides"])
        for provided in record["provides"]:
            owners[universe][provided].add(record["modId"])
        hard[universe] += len(record["depends"])

    def is_node_of_other_universe(target, universe):
        return any(target in nodes[other] for other in UNIVERSES if other != universe)

    totals = collections.Counter()
    edge_counts = {}

    for universe in UNIVERSES:
        buckets = collections.Counter()
        edge_pairs = set()
        pair_counts = collections.Counter()
        alias_pairs = set()

        for record in ok:
            if universe_of(record["loaderFamily"]) != universe:
                continue
            for constraint in record["depends"]:
                target = constraint["targetId"]
                if target in nodes[universe]:
                    buckets["EDGE"] += 1
                    edge_pairs.add((record["modId"], target))
                    pair_counts[(record["modId"], target)] += 1
                elif target in ENVIRONMENT_IDS:
                    buckets["ENVIRONMENT"] += 1
                elif is_node_of_other_universe(target, universe):
                    buckets["OTHER_UNIVERSE"] += 1
                elif target in aliases[universe]:
                    buckets["ALIAS_ONLY"] += 1
                    for owner in owners[universe][target]:
                        if owner != record["modId"]:
                            alias_pairs.add((record["modId"], owner))
                else:
                    buckets["ABSENT"] += 1

        totals.update(buckets)
        edge_counts[universe] = len(edge_pairs)
        bucket_sum = sum(buckets[name] for name in BUCKETS)

        print("universe %s" % universe)
        print("  nodes                 : %d" % len(nodes[universe]))
        print("  hard declarations     : %d" % hard[universe])
        for name in BUCKETS:
            print("  %-21s : %d" % (name, buckets[name]))
        print("  bucket sum            : %d" % bucket_sum)
        print("  distinct edges        : %d" % len(edge_pairs))
        print("  EDGE decls - edges    : %d" % (buckets["EDGE"] - len(edge_pairs)))
        repeated = sorted(pair for pair, count in pair_counts.items() if count > 1)
        for source, target in repeated:
            print("  repeated declaration  : %s -> %s" % (source, target))
        print("  figure check          : %d distinct edges + %d alias-as-edge = %d (the wrong figure)"
              % (len(edge_pairs), len(alias_pairs), len(edge_pairs) + len(alias_pairs)))
        if bucket_sum != hard[universe]:
            print("  FAIL: bucket sum %d != hard declarations %d" % (bucket_sum, hard[universe]))
            return 1
        print()

    total_hard = sum(hard.values())
    total_buckets = sum(totals.values())
    print("all universes")
    print("  hard declarations     : %d" % total_hard)
    for name in BUCKETS:
        print("  %-21s : %d" % (name, totals[name]))
    print("  bucket sum            : %d" % total_buckets)
    print("  distinct edges        : %d" % sum(edge_counts.values()))
    print("  outside all universes : %d (loaderFamily UNKNOWN)" % excluded)
    if total_buckets != total_hard:
        print("FAIL: bucket sum %d != hard declarations %d" % (total_buckets, total_hard))
        return 1
    print()

    unique_ids = {record["modId"] for record in ok}
    fabric_ids = {record["modId"] for record in ok
                  if universe_of(record["loaderFamily"]) == "FABRIC"}
    neoforge_ids = {record["modId"] for record in ok
                    if universe_of(record["loaderFamily"]) == "NEOFORGE"}
    same_family = collections.Counter(
        (universe_of(record["loaderFamily"]), record["modId"])
        for record in ok
        if universe_of(record["loaderFamily"]) is not None
    )
    duplicates = sorted(mod_id for (_, mod_id), count in same_family.items() if count > 1)

    print("loader-mixing facts (SPEC 3.6.2)")
    print("  unique modIds                       : %d" % len(unique_ids))
    print("  modIds with a FABRIC variant        : %d" % len(fabric_ids))
    print("  modIds with a NEOFORGE variant      : %d" % len(neoforge_ids))
    print("  modIds with both variants           : %d" % len(fabric_ids & neoforge_ids))
    print("  modIds with two records in a family : %d %s" % (len(duplicates), duplicates))
    print()
    print("OK: every invariant holds")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
