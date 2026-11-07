# BOUNDARIES — external comparisons and what they do not show

Status: v1.0 (2026-11-07). This document is not part of the SPEC acceptance criteria. It freezes what two external comparisons measured, and the interpretation limits that must travel with those numbers. All counts below are taken from `PROGRESS.md` (steps 第二十步 and 第二十一步); if `PROGRESS.md` changes, this document must be re-synced in both language versions.

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
