# BOUNDARIES — external comparisons and what they do not show

Status: v1.1 (2026-11-08). This document is not part of the SPEC acceptance criteria. It freezes what two external comparisons measured, and the interpretation limits that must travel with those numbers. All counts below are taken from `PROGRESS.md` (steps 第二十步 and 第二十一步) and, for §7, from the 26.2 batch run in step 第二十六步; if `PROGRESS.md` changes, this document must be re-synced in both language versions.

Change log: v1.1 adds §7 (loader-family coverage, with the 1.21.1-baseline qualifier and the first real 26.2 trigger) and §8 (data handling), and adds one bullet to §2.5.

## 1. Why this file exists

Two external comparisons were run to bound known gaps, not to prove that the tool works:

- the `modcrawl` Mixin-coverage comparison: how many Mixin targets the naming-based inference misses;
- the Forbric `MOD_TEST_FAILURES.md` comparison: how many entries of an external failure list can be located in the 1.21.1 dataset, and what static signals they carry.

Both are static, offline comparisons. Neither is a runtime test. Neither produces an install order, a compatibility verdict, or a prediction of class loading.

## 2. modcrawl Mixin-coverage comparison

### 2.1 Setup

- Input: 195 jars, 195 candidate records, 191 OK, 4 FAILED (`191 + 4 = 195`).
- The four-way classification covers the 191 OK records only.
- Tool: upstream `modcrawl` v0.11.3 prebuilt Windows binary, not compiled locally (no `cargo` / `rustc` on the machine). The local fork's `src/cmd/mixins.rs` matches upstream v0.11.3 apart from CRLF/LF. The binary SHA-256 is recorded in `PROGRESS.md` (step 第二十步).
- Command shape: `modcrawl.exe mixins --json <jar>`. Only the top level of each jar is scanned; nested jars are not recursed into.

### 2.2 Four-way result

| Bucket | Records | Unique modIds |
|---|---:|---:|
| BOTH | 157 | 100 |
| NAMING_ONLY | 1 | 1 (`sodium-neoforge`) |
| MODCRAWL_ONLY | 6 | 5 |
| NEITHER | 27 | 21 |
| **Total** | **191** | — |

The unique-modId column overlaps across buckets (for example, sodium has one Fabric record in BOTH and one NeoForge record in NAMING_ONLY), so the unique counts must not be added.

### 2.3 The original 10-record gap

- 33 OK records have empty `mixinTargets`; 10 of them have non-empty `mixinConfigs` and form the gap; the other 23 have empty configs.
- The gap is 10 records / 8 unique modIds. `modcrawl` supplies targets for 5 records / 4 unique modIds; 5 records / 4 unique modIds remain.
- All 5 remaining records are `EMPTY_CONFIG`: the config exists but enumerates no mixin classes, and `modcrawl` also reads 0 top-level `@Mixin` entries. They are not evidence that the naming rule missed a target; there is no target to infer.
- The `NEITHER` bucket is exactly those 5 records plus 22 empty-config records that `modcrawl` also did not hit. The `MODCRAWL_ONLY` bucket is the 5 filled records plus 1 empty-config record (`modelfix`) that `modcrawl` still read.

### 2.4 The nested-jar caveat

`NAMING_ONLY` is one record, `sodium-neoforge`: naming inference finds 74 targets while top-level `modcrawl` finds 0. Extracting the nested jar `META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar` and running `modcrawl` on it yields 99 entries / 72 unique targets. The difference is caused by `modcrawl` not recursing into nested jars, not by sodium lacking mixins. Therefore “`modcrawl` found nothing at top level” is not evidence that a jar has no mixins.

### 2.5 Not verified

- Whether the two sides name targets the same way: the naming side uses dotless simple names; `modcrawl` returns internal names (possibly obfuscated). No intersection was computed.
- Runtime behavior; nested-jar coverage of `modcrawl`; the 4 FAILED records (they were also run and returned 0 records, but they are outside the 191 denominator).
- The 26.2 re-run of this comparison was **not performed**: the upstream v0.11.3 Windows binary recorded in `PROGRESS.md` step 第二十步 is no longer on disk (`D:\modgraph\tools\modcrawl` is a source checkout with no `.exe`), and re-fetching it from the recorded release URL returned `504 Gateway Time-out` (92-byte HTML error body). So there is no 26.2 four-way number, and none is inferred from the 1.21.1 one.

## 3. Forbric failure-list comparison

### 3.1 Source and snapshot rule

- Source: `https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader` → `MOD_TEST_FAILURES.md`, 30327 bytes, SHA-256 `96AE0BC7E3DFBAED6F76ABCDDC104FBF79F1DB973048E479490C9BEDEC0E3CFB`.
- The file is a multi-snapshot historical record, not one list. Only two named tables are parsed:
  - 2026-09-27 table: 35 rows / 32 unique Forbric ids;
  - 2026-10-01 table: 12 rows / 12 unique Forbric ids.
- The two tables are not merged. The script asserts `listsAreDisjoint = true`: no Forbric id appears in both.
- Other snapshots are not guessed in: 2026-10-11's 4 “both sides failed” entries are unnamed; 2026-10-03 says 10 of 12 were still failing but does not name them row by row.

### 3.2 35-row table

- Candidate located: 10 rows / 8 unique ids; no candidate: 25 rows / 24 unique ids.
- Primary buckets: `NO_CANDIDATE` 25 + `LOADER_MISMATCH` 1 (`lambdynamiclights`) + `EXCLUDED_RENDER_CORE` 4 (`iris`, `sodium` ×2, `sodium-extra`) + `DEPENDENCY_SIGNAL` 5 (`biomes-o-plenty`, `player-animation-library`, `fancymenu`, `fzzy-config` ×2) = 35. All other buckets are 0.
- Field counts on the 10 located rows are non-exclusive and must not be added: dependencies non-SATISFIED 10; `depends` non-empty 10; exact version match 0 (all cross-version); `mixinTargets` non-empty 7; conflicts HIGH/MEDIUM 6; `supportPolicy=EXCLUDED` 4; `nestedJars` non-empty 1; `status=FAILED` 0; loader match 9 / loader mismatch 1.

### 3.3 12-row table

- Candidate located: 0; no candidate: 12 rows / 12 unique ids. `NO_CANDIDATE` 12 = 12. All other buckets are 0.

### 3.4 The same two tables against the 26.2 batch (one quantitative sentence)

Against a 26.2 batch of 36 collected jars, the two tables (47 rows / 44 unique ids, not merged) bucket as **LOCATED 39 / `NO_262` 2 / `NOT_FOUND` 3 / `NOT_IN_BATCH` 3 / download-failure 0 = 47 rows**, i.e. **39 of 47 rows (36 unique identities) have a candidate in the 26.2 batch**, and inside those 39 rows the signals are non-SATISFIED dependency 36, non-empty `mixinTargets` 33, HIGH/MEDIUM conflict 27, loader mismatch 20, `supportPolicy=EXCLUDED` 4, `status=FAILED` 0, rows with no signal at all 0 (non-exclusive field counts; they must not be added). The five identities outside the batch are the two `NO_262` and the three `NOT_FOUND` ones listed in `PROGRESS.md` step 第二十八步; the 2026-10-11 re-test (“fixed down to 0”) is not used in any label here.

### 3.5 What the 26.2 batch can and cannot say

An earlier revision of this section said `mixinTargets` = 31 (a record-basis figure presented as unique-modId) and loader mismatch = 36/36 (a case-sensitive comparison of `FABRIC` against `Fabric`); both were wrong. The numbers below are re-derived with `recount_baseline_262.py` on a unique-modId basis, and the correction is recorded in `PROGRESS.md` step 第二十九步.

- **Sizes.** The two tables together are 47 rows / 44 unique ids; 39 rows (36 unique identities) have a candidate in the 26.2 batch, and all 36 downloaded jars ran through M0 (36 OK / 0 FAILED).
- **Field distribution, side by side**, counted per unique modId (non-exclusive, so they must not be added):

| field | collectable baseline (30 mods) | Forbric rows (35 unique of 39 rows) |
|---|---:|---:|
| dependencies non-`SATISFIED` | 26 | 32 |
| `mixinTargets` non-empty | 23 | 30 |
| HIGH/MEDIUM conflict | 17 | 24 |
| `supportPolicy=EXCLUDED` | 1 | 3 |
| `status=FAILED` | 0 | 0 |

- **These signals are common in ordinary collectable mods too** (26/30, 23/30, 17/30). This round cannot show that any of them discriminates runtime failure. “0 rows with no signal” is a row count, not a hit rate and not a success rate; no predictive claim follows from it.
- **Loader mismatch is excluded from the evidence.** Of the 39 located rows, 31 carry a Forbric loader label and 8 do not; among the 31 labelled rows, 17 match the classifier’s `loaderFamily` and **14 do not** (case-insensitive comparison). Forbric labels the loader of the failing build while this batch selects files by file-level `loaders`, so the mismatch is a labelling artefact rather than a failure signal, and it is dropped from the table above.
- **Mixin four-way on 26.2 (modcrawl, local):** BOTH 29 / `NAMING_ONLY` 2 / `MODCRAWL_ONLY` 1 / `NEITHER` 4 = 36 records; unique modIds 28 / 2 / 1 / 4 (not summed). The single naming gap (`entitycrosshair`) is filled by modcrawl with 2 targets, so 1 of 1 is filled and 0 remain.
- **Assertion coverage.** C4 only checked the `summary.csv` row count. C6 now also checks `conflicts.csv` rows == C(unique modIds, 2) on both batches (30 → 435, 35 → 595); that identity is why 595 is the correct number for 36 records rather than C(36,2) = 630.

## 4. How these numbers may and may not be read

1. **Version mismatch is not prediction failure.** The Forbric list describes MC 26.2; the modgraph dataset is 1.21.1. `NO_CANDIDATE` means “no record with this identity exists in the 1.21.1 dataset” — a version/identity mismatch — not “modgraph predicted this mod would fail”, and not “modgraph would also miss it on 26.2”. No 26.2 dataset was analyzed.
2. **`EXCLUDED` is a support-policy disclaimer.** It is not a finding that the mod is broken, and it does not explain a runtime crash.
3. **A non-`SATISFIED` dependency row is a 1.21.1 declarative judgement.** It can be a true gap, a version-range artifact, or a known false positive (see the hyphenated-version caveat in `PROGRESS.md`); it is not proof of the runtime cause of a Forbric failure.
4. **The tool does only declarative admission checks.** It does not predict Mixin transforms, class loading, mod loading order, or runtime behavior, and it does not guarantee stability. See `SPEC.md` §0.
5. **The `modcrawl` comparison is not a correctness proof either.** It shows which targets the naming rule did not produce under this tool/version, with the nested-jar and naming caveats above.
6. **Zero means “zero under this definition”.** `NO_CANDIDATE` 12 in the 12-row table means 0 of 12 rows could be located in the 1.21.1 dataset; it does not mean 0 failures, 0 mixins, or “verified effective”.

## 5. Required wording

- Do not write “已验证有效”, “预测成功”, “覆盖率提升”, “证明”, “根因”.
- Write “静态对照”, “在此口径下”, “未验证”, “不能证明”, “与基线一致/不一致”.
- When quoting a count, quote its denominator and its definition (records vs unique ids; rows vs pairs; declarations vs deduplicated edges).

## 6. Reproduction

- `python verify_mixin_coverage.py --modcrawl <path-to-modcrawl.exe>` — read-only w.r.t. the main artifacts; writes `mixin-coverage/`.
- `python verify_forbric_failures.py --expected-sha256 96AE0BC7E3DFBAED6F76ABCDDC104FBF79F1DB973048E479490C9BEDEC0E3CFB` — read-only w.r.t. the main artifacts; writes `forbric-comparison/`.
- Both scripts exit non-zero on assertion mismatch. Full hashes of the tool binary, the source Markdown, and the generated comparison files are in `PROGRESS.md` steps 第二十步 and 第二十一步.

## 7. Loader-family coverage: 1.21.1 baseline vs the first 26.2 trigger

- **In the 1.21.1 baseline**, `QUILT` = 0, `FORGE` = 0 and `OTHER_UNIVERSE` = 0 records. Those branches of §3.6.2 were therefore covered only by constructed tests, never by real data. Any sentence claiming “FORGE/QUILT are zero” must carry that qualifier: it describes the 1.21.1 dataset, not the tool.
- **In the 26.2 batch (30 jars)**, `loaderFamily` is FABRIC 19 / NEOFORGE 10 / FORGE 1 — the FORGE branch is hit by real data for the first time.
- The record is `carryon` 2.11.0, file `carryon-forge-26.2-2.11.0.jar`. It was placed on the NeoForge side by its filename, but the jar contains no `META-INF/neoforge.mods.toml`; it only has `META-INF/mods.toml`, so the §2.2 fallback rule assigns `FORGE`. Excerpt of `META-INF/mods.toml` (cosmetic fields, `ordering` and `side` lines omitted; the complete file is quoted in `PROGRESS.md` step 第二十七步):

```toml
modLoader="javafml"
loaderVersion="[65,)"
license="GNU LGPLv3"
issueTrackerURL="https://github.com/Tschipp/CarryOn/issues"

[[mods]]
    modId="carryon"
    version="2.11.0"
    displayName="Carry On"

authors="Tschipp, PurpliciousCow"

[[dependencies.carryon]]
    modId="forge"
    mandatory=true
    versionRange="[65,)"

[[dependencies.carryon]]
    modId="minecraft"
    mandatory=true
    versionRange="[26.2, 27)"
```

- What this does **not** show: that the FORGE branch is correct in general. One record, one version, one loader family. `QUILT` remains untriggered by real data in both datasets.
- **Selection rule (added in v1.1):** a batch must pick the jar by **file-level `loaders`**, not by project name and not by the project's primary file. Step 第二十六步 picked the primary file of each project's `26.2` version; for `carryon` that primary file is the **Forge build** (`carryon-forge-26.2-2.11.0.jar`). So the FORGE branch fired because of a wrong-variant selection on our side, **not** because the classifier misjudged: that jar really does contain only `META-INF/mods.toml`, and §2.2's fallback rule then correctly yields `FORGE`. A NeoForge build of the same mod would classify as NEOFORGE. Step 第二十八步 applies the file-level rule (`dataset-262/forbric/normalization.md` records the normalization, `fetch_262_forbric.py` the selection).

## 8. Data handling and redistribution

- Mod jars are **never committed and never redistributed**. They live only under `dataset-262/`, which is listed in `.gitignore`; no jar byte appears in any tracked file.
- Comparison manifests keep exactly three kinds of derived facts per jar: the download **URL**, the **hashes** (SHA-256 measured locally; SHA-512 as published by the source), and the **license identifier exactly as published** (SPDX id or `LicenseRef-*`). A license identifier is a quotation, not a permission analysis: this repository makes no claim that a jar may be redistributed under it.
- Recorded in step 第二十六步: 30 of 30 local jars matched the source-published SHA-512, and every local SHA-256 matched its manifest entry. If a hash ever mismatches, the batch must be discarded rather than partially used.
