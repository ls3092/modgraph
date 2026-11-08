# MC 26.2 collectability — segment 1 verification (static, no jars downloaded)

Status: v1.0 (2026-11-08). Produced by 长程任务第 1 段. This file records what was
verified, with the exact request URLs, so the numbers can be re-run. It is not an
acceptance criterion of `SPEC.md` and it changes no M0–M1.5 number.

**Nothing here downloaded a jar.** Only JSON API responses and one local Markdown
snapshot were read.

## 1. Answer in one number

| Question | Answer | Definition |
|---|---:|---|
| Collectable MC 26.2 mods on Modrinth | **14,705** | projects with `project_type:mod` and a release tagged `26.2`; last measured 2026-11-08 16:3x local |
| — of which fabric-tagged | 13,875 | `categories:fabric` facet |
| — of which neoforge-tagged | 7,219 | `categories:neoforge` facet |
| — annotated-with-both | 2,669 | both facets (see §4: this facet does **not** behave as an intersection) |
| Collectable MC 26.2 mods on CurseForge | **unknown, not zero** | official API returns `403` without `x-api-key`; web search behind Cloudflare challenge |
| Overlap with the two Forbric tables | **24 rows / 21 unique slugs of 47 rows / 44 unique ids** | snapshot = first 2,000 Modrinth 26.2 mods only (lower bound, see §6) |

The pool is far larger than the 30 jars segment 2 needs, so segment 2 is not blocked
by supply. What *is* blocked is CurseForge: no key, no authoritative number.

## 2. Exact requests (re-runnable)

`python verify_collectability_262.py --enumerate 20 --forbric-ids forbric-comparison/sources-A.json`
reproduces every number in this file and writes `collectability-262.json`.

The underlying calls (Modrinth public API, no key; `User-Agent` identifies this project):

```
GET https://api.modrinth.com/v2/tag/game_version
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"],["project_type:mod"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"],["project_type:mod"],["categories:fabric"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"],["project_type:mod"],["categories:neoforge"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"],["project_type:mod"],["categories:fabric"],["categories:neoforge"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:26.2"],["project_type:mod"],["categories:fabric","categories:neoforge"]]
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:99.99"]]      # control: must be 0
GET https://api.modrinth.com/v2/search?limit=1&facets=[["versions:1.21.1"],["project_type:mod"]]  # control
GET https://api.modrinth.com/v2/search?limit=100&offset=<0,100,...>&facets=[["versions:26.2"],["project_type:mod"]]
```

`[["a"],["b"]]` is AND, `[["a","b"]]` is OR, per Modrinth's search-facet syntax.

## 3. Controls (why the counts are not passthrough)

| Control | Value | Meaning |
|---|---:|---|
| `versions:99.99` | 0 | a bogus version cannot leak results; the facet is applied |
| `versions:1.21.1` + `project_type:mod` | 32,922 | the filter discriminates; 26.2 is not "everything" |
| `versions:26.2` all project types | 35,975 → 36,005 over 40 min | includes resource packs, shaders, datapacks |
| `versions:26.2` release date | 2026-06-16 (`/v2/tag/game_version`) | 26.2 is a real release string, not invented |

## 4. Bucket assertion — what closes and what does not

Buckets are computed **inside one enumeration snapshot**, never by adding separate
`total_hits` values, because the Modrinth index changes between requests (the same
`versions:26.2` + `project_type:mod` query returned 14,676 / 14,677 / 14,704 / 14,705
within ~40 minutes).

Snapshot (first 2,000 of 14,705, `limit=100` × 20 pages):

| Bucket | Projects |
|---|---:|
| BOTH (fabric and neoforge) | 1,390 |
| FABRIC_ONLY | 519 |
| NEOFORGE_ONLY | 91 |
| NEITHER | 0 |
| **Total** | **2,000** |

Assertion `A3_buckets_sum_to_snapshot`: **holds** (1,390 + 519 + 91 + 0 = 2,000).

Assertion `A5_no_duplicate_project_ids`: **holds**.

**Unexplained, reported as-is:** inclusion–exclusion does not close across separate
queries. `fabric + neoforge − both` = 13,875 + 7,219 − 2,669 = 18,425, while the
union query (`fabric OR neoforge`) returns 14,656 — a gap of 3,769, far larger than
index drift. The multi-`categories` AND facet therefore does **not** behave as an
intersection at scale. Consequence: **no total is published from loader
`total_hits`**; only the snapshot-local partition above is used.

## 5. Sources, licenses and terms

| Source | Access | Terms observed |
|---|---|---|
| Modrinth API (`api.modrinth.com/v2`) | public, no key, works | returns per-project `license` (SPDX id or `LicenseRef-*`); no acceptance click was required for these read-only calls |
| CurseForge API (`api.curseforge.com`) | `403 Forbidden: API Key missing or invalid` | requires `x-api-key` from the CurseForge for Studios console; `totalCount` exists only with a key |
| `www.curseforge.com` search | `403`, `Just a moment...` Cloudflare managed challenge | not machine-readable without a browser; no number extracted |
| `api.cfwidget.com` | `200` per project, `404` for search | keyless but per-project only; no browse/total; its own docs say license values are stale since 2020 |
| local `forbric-comparison/MOD_TEST_FAILURES.md` | local snapshot | 30,327 bytes, SHA-256 `96AE0BC7E3DFBAED6F76ABCDDC104FBF79F1DB973048E479490C9BEDEC0E3CFB` (measured, matches recorded) |

License distribution over Lane B's top-80 sample is in `sources-B.json`; the API
exposes the SPDX id but **not** whether a given `LicenseRef-*` permits
redistribution of the jar, so no license conclusion is drawn beyond quoting the id.

## 6. Overlap with the two Forbric tables (lower bound)

Forbric tables total 47 rows / 44 unique ids (35 rows / 32 ids on 2026-09-27,
12 rows / 12 ids on 2026-10-01; the two tables are not merged and share no id).
Of those, **24 rows / 21 unique slugs** appear in the first-2,000 snapshot.

| snapshot table | name | slug | Forbric version | license (Modrinth) | loaders | in 1.21.1 dataset |
|---|---|---|---|---|---|---|
| 2026-09-27 | alexs-mobs-continued | alexs-mobs-continued | 2.2.2+26.2-neoforge | GPL-3.0-only | fabric,forge,neoforge | no |
| 2026-09-27 | better-combat | better-combat | 3.2.2+26.2-neoforge | LicenseRef-All-Rights-Reserved | fabric,forge,neoforge | no |
| 2026-09-27 | biomes-o-plenty | biomes-o-plenty | 26.2.0.0.28 | LicenseRef-All-Rights-Reserved | fabric,forge,neoforge | YES |
| 2026-09-27 | iris | iris | 1.11.4+26.2-neoforge | LGPL-3.0-only | fabric,neoforge,quilt | YES |
| 2026-09-27 | lambdynamiclights | lambdynamiclights | 4.12.4+26.2 | LicenseRef-Lambda-License | fabric,neoforge,quilt | no |
| 2026-09-27 | particle-core | particle-core | 0.3.3+26.2+neoforge | MIT | fabric,forge,neoforge,quilt | no |
| 2026-09-27 | player-animation-library | player-animation-library | 1.2.6 | MIT | fabric,neoforge | no |
| 2026-09-27 | sodium (row 1) | sodium | mc26.2-0.9.2-neoforge | LicenseRef-Polyform-Shield-1.0.0 | fabric,neoforge,quilt | YES |
| 2026-09-27 | sodium (row 2) | sodium | mc26.2-0.9.2-neoforge | LicenseRef-Polyform-Shield-1.0.0 | fabric,neoforge,quilt | YES |
| 2026-09-27 | sodium-extra | sodium-extra | mc26.2-0.9.4+neoforge | LGPL-3.0-only | fabric,neoforge,quilt | YES |
| 2026-09-27 | sodium-extra-information | sodium-extra-information | 2.9.0 | GPL-3.0-or-later | fabric,neoforge,quilt | no |
| 2026-09-27 | ukulib | ukulib | 2.1.1+26.2-fabric | MPL-2.0 | fabric,neoforge,quilt | no |
| 2026-09-27 | bclib (row 1) | bclib | 26.201.2 | MIT | fabric | no |
| 2026-09-27 | bclib (row 2) | bclib | 26.201.2 | MIT | fabric | no |
| 2026-09-27 | bclib-neoforge | bclib-neoforge | 26.2.3 | MIT | fabric,neoforge,quilt | no |
| 2026-09-27 | drippy-loading-screen | drippy-loading-screen | 3.1.5-26.2-neoforge | LicenseRef-DSMSLv3 | fabric,forge,neoforge | no |
| 2026-09-27 | fancymenu | fancymenu | 3.9.12-26.2-neoforge | LicenseRef-DSMSLv3.1 | fabric,forge,neoforge | YES |
| 2026-09-27 | easy-magic | easy-magic | 26.2.0 | MPL-2.0 | fabric,forge,neoforge | no |
| 2026-09-27 | oneconfig | oneconfig | v1.2.7 | LicenseRef-LGPL-3.0-with-Additional-Terms-Applicable-to-OneConfig | fabric,forge | no |
| 2026-09-27 | fzzy-config (row 1) | fzzy-config | 0.7.7+26.2+neoforge | LicenseRef-TDL-M | fabric,forge,neoforge,quilt | YES |
| 2026-09-27 | fzzy-config (row 2) | fzzy-config | 0.7.7+26.2+neoforge | LicenseRef-TDL-M | fabric,forge,neoforge,quilt | YES |
| 2026-09-27 | time-weather-changer | time-weather-changer | 1.2.0 | BSD-2-Clause | fabric,neoforge | no |
| 2026-10-01 | Crash Assistant | crash-assistant | (not stated in snapshot) | LicenseRef-KostromDan-MML-1.1.3 | fabric,forge,neoforge,quilt | no |
| 2026-10-01 | Leaves Be Gone | leaves-be-gone | (not stated in snapshot) | MPL-2.0 | fabric,forge,neoforge | no |

`in 1.21.1 dataset` = the normalized slug matches a `modId` in `candidates.jsonl`
(lowercase, every non-alphanumeric character stripped). Overall, 81 of the 2,000
snapshot projects match the 112 normalized dataset ids.

The remaining 23 unique Forbric ids are **not proven absent**: the snapshot is only
the first 2,000 of 14,705 by search relevance. Proving absence needs full
enumeration (147 pages) or a per-slug `GET /v2/project/<slug>` probe — neither was
run in this segment.

## 7. What this does not show

- **Jar-level counts.** All numbers are project-level. A project may ship several
  jars for 26.2; the file list per project was not enumerated (`/v2/project/<id>/version`
  would be 14,705 calls). So "≥ 14,705 collectable jars" is the defensible statement,
  not "exactly 14,705".
- **CurseForge anything.** No count, no license, no URL — blocked at auth/Cloudflare.
- **Redistribution rights.** Modrinth reports an SPDX id; it does not state whether
  that license permits redistributing the jar. No such conclusion is drawn here.
- **Runtime behaviour.** Same boundary as `SPEC.md` §0 and `BOUNDARIES.md` §4: this is
  a supply check, not a compatibility or loading prediction.
- **Stable totals.** The index moves continuously; every number carries the query
  timestamp and will drift by tens per hour.

## 8. Lane artifacts

| Lane | File | Lane scope | Status |
|---|---|---|---|
| A | `forbric-comparison/sources-A.json` | local `MOD_TEST_FAILURES.md` only | complete (hash measured by parent; child had no shell) |
| B | `forbric-comparison/sources-B.json` | Modrinth only | complete; enumerated top 80 |
| C | `forbric-comparison/sources-C.json` | CurseForge only | complete; authoritative total impossible without a key |
| main | `forbric-comparison/collectability-262.json` | merge: counts, snapshot buckets, both overlaps | complete |
