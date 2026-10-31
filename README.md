<p align="center">
  <b>English</b> · <a href="./README.zh-CN.md">简体中文</a>
</p>

<div align="center">

<h1 align="center">modgraph</h1>

<p align="center">
  <b>A unified mod metadata and dependency arbitration layer for Fabric and NeoForge</b>
  <br/>
  <sub>Not a compatibility layer · Not an API reimplementation · Not translating one loader on top of the other</sub>
</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/license-MIT-yellow?style=for-the-badge" alt="license"/></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"/></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/build-gradle-orange?style=for-the-badge&logo=gradle&logoColor=white" alt="build"/></a>
  <a href="./SPEC.md"><img src="https://img.shields.io/badge/status-M0%20completed-brightgreen?style=for-the-badge" alt="M0 completed"/></a>
  <a href="https://www.minecraft.net/"><img src="https://img.shields.io/badge/Minecraft-1.21.1-52a447?style=for-the-badge" alt="Minecraft"/></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/real%20jars-195-blue?style=flat-square" alt="jars"/>
  <img src="https://img.shields.io/badge/metadata%20accuracy-100%25-brightgreen?style=flat-square" alt="accuracy"/>
  <img src="https://img.shields.io/badge/tests-42%20passing%20%C2%B7%200%20skipped-brightgreen?style=flat-square" alt="tests"/>
  <img src="https://img.shields.io/badge/HIGH%20conflicts-331%20%C2%B7%204.96%25-orange?style=flat-square" alt="conflicts"/>
</p>

> ⚠️ **The next stage of this project requires deep JVM knowledge, and the author does not know Java.**
> See [Seeking collaborator](#seeking-collaborator).

</div>

---

## Table of contents

- [What this is](#what-this-is)
- [What it is not](#what-it-is-not)
- [Why another one](#why-another-one)
- [Current status](#current-status)
- [Quick start](#quick-start)
- [Outputs](#outputs)
- [Architecture](#architecture)
- [Engineering discipline](#engineering-discipline)
- [Dataset compliance](#dataset-compliance)
- [Seeking collaborator](#seeking-collaborator)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## What this is

Minecraft: Java Edition has two mutually incompatible modding ecosystems: **Fabric** and **NeoForge / Forge**.
Each has its own metadata format (`fabric.mod.json` / `neoforge.mods.toml`), dependency semantics, version-range
syntax and load-time behaviour.

`modgraph` aims to unify both ecosystems **at the metadata layer**, then perform dependency resolution and
conflict arbitration on top of that model — so that the question "can these mods be installed together, and if
not, why not" finally has an answer that is **deterministic, reproducible and verifiable**.

The core type of the unified model is `ModCandidate` (see [`SPEC.md`](./SPEC.md) §1).
It flattens the metadata differences between Fabric and NeoForge into a neutral structure while
**preserving every original field** — no lossy conversion.

On top of that, M0 additionally produces an **invasiveness rating**: without parsing any bytecode, it uses the
descriptors and metadata inside the zip to identify which mods operate at the render-pipeline level, the
bytecode level, or are pure API consumers — and warns about conflict risk *before* launch.

### Scope

`modgraph` produces an **"admission check for whether a mod set can start"**, not **"a guarantee that it will run stably"**.

- Can predict: missing dependencies, disjoint version ranges, dependency cycles, explicit incompatibility declarations
- Cannot predict: two mixins targeting the same method, two mods replacing the same render stage

Behavioural conflicts only surface at runtime. This project makes no promise of "automatic compatibility" or
"automatic repair".

---

## What it is not

This matters more than "what it is", because every existing project draws the line somewhere different:

| Project | Approach | Positioning |
| --- | --- | --- |
| **Sinytra Connector** | Hosts on NeoForge, remaps Fabric mods at load time | Host + translation layer |
| **Kilt** | Hosts on Fabric, embeds the NeoForge API and re-creates Forge patches with 500+ mixins | Host + translation layer |
| **Patchwork** | Rewrites the Forge API as a set of Fabric mods | API reimplementation, discontinued |
| **Forbric** | Custom loader claiming not to be a translation layer | Source of inspiration, see below |
| **modgraph** | Unified metadata and dependency arbitration | Metadata-layer unification |

`modgraph` **is not currently a mod loader**. It does not start Minecraft, does not load classes, does not
rewrite bytecode.

On the question "is this a translation layer", our position is candid:
**unification stops at metadata and dependencies; below that (events, registries, energy/fluid/item interop,
patch conflicts against the same MC code) you cannot fundamentally avoid bridging.**
The difference is not "whether there is translation", but **whether the boundary of the bridge is drawn honestly,
and whether conflicts are adjudicated by a unified arbiter rather than silently resolved by picking one side**.

---

## Why another one

Inspired by [Forbric](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) v0.3.0, but the technical
judgement differs.

Forbric's code is almost entirely AI-generated without adequate human review — this has been pointed out in
public community analyses. The resulting engineering-quality hazards include:

- Using `assumeTrue` so tests silently skip under certain conditions, distorting pass rates
- Demoting APIs that should be mandatory to optional, to inflate "number of mods that run"
- Catching crashes and demoting them to warnings, then continuing
- No test coverage on the mixin output side
- Gates and canary tests not wired into CI

This project's answer is not "write it slightly better", but **turning each of the above into an enforceable
engineering rule** — see [Engineering discipline](#engineering-discipline).

---

## Current status

> ⚠️ **M0: offline metadata parser. It cannot load any mod and cannot start Minecraft.**

| Stage | Scope | Status |
| --- | :---: | :---: |
| **M0** | Parse mod jar metadata offline, emit unified `ModCandidate` records and dependency declarations | ✅ Completed (2026-10-31, 195 real jars) |
| M0.5 | Version-range evaluation and semver rule alignment | Not started |
| M1 | Cross-loader dependency resolution and conflict arbiter | Not started |
| M2 | Unified bootstrap and transform orchestration | ⚠️ Requires deep JVM knowledge; author does not know Java |
| M3 | Stably load 20 + 20 real mods on a single version | Not started |

**No version before M3 should ever be used in an actual game.**

---

## Quick start

### Requirements

- JDK 21
- Python 3.9+ (only for collecting the test dataset)
- Gradle 8.x (**installed locally** — this repo ships no wrapper)

### 1. Collect a real mod dataset *(optional)*

This project **does not use synthetic data**. Test input is real mod jars downloaded from the public
[Modrinth](https://modrinth.com) API.

Collection is three steps; all scripts live in the repository root:

```bash
python build_download_list.py                        # generate the download list (download_list.txt)
python fix_downloads.py                              # repair failed / truncated downloads
python organize_jars.py --search "fixtures/jars"     # auto-sort by metadata found inside each jar
python verify_downloads.py                           # verify each jar opens and contains metadata
```

You can run the tests without any dataset: when `fixtures/jars` is missing, tests fall back to the 11 built-in
samples under `src/test/resources/fixtures/`.

### 2. Build

```bash
gradle build
```

### 3. Run the parser

```bash
gradle run --args="fixtures/jars"
```

---

## Outputs

| File | Description |
| --- | --- |
| `candidates.jsonl` | One JSON object per line, one per input jar, in input order |
| `summary.csv` | Header: `sourceFile,sha256,loaderFamily,status,modId,version,modType,invasiveness,supportPolicy,failureCode` |
| `conflicts.csv` | Header: `modA,modB,level,sharedTargets` — pre-launch conflict prediction, all pairs included (including `NONE`) |
| stdout | INFO-level summary |

Below is a **real run** against the current dataset (195 jars). The numbers are not constants
(log messages are emitted in Chinese; the figures are what matter):

```
[INFO] FABRIC: OK=100 FAILED=0
[INFO] NEOFORGE: OK=91 FAILED=4
[INFO] 输入 jar：195 | OK：191 | FAILED：4
[INFO] 失败分类：NO_METADATA: 4
[INFO] 侵入深度：API 23 | MIXIN 152 | CORE 7 | RENDER 9
[INFO] 支持策略：SUPPORTED 175 | DEGRADED 0 | EXCLUDED 16
[INFO] 高频目标类：Minecraft 28 | GameRenderer 25 | Entity 19 | LevelRenderer 18 | MinecraftClient 18 | ClientPacketListener 17 | Gui 16 | LivingEntity 16 | Screen 15 | ClientLevel 13
[INFO] ubiquitous 阈值：12（模组总数 116 × 10%，向上取整）
[INFO] 冲突预测：HIGH 331 组 | MEDIUM 1170 组 | LOW 725 组（静态推断，非运行结论）
[INFO] 本结果为准入检查，不构成运行稳定性保证
```

> M0 performs no version-range evaluation and no dependency solving, so there are no figures for
> "hard dependency count", "unsatisfiable" or "cycles" — those belong to M0.5 / M1.

### Core invariant

```
OK count + FAILED count == number of input files
```

**Identity.** If this assertion fails, the build fails.
There is never a case of "N inputs, fewer than N outputs" without explanation — failures must be counted,
classified and presented.

---

## Architecture

The pipeline is only four steps, deliberately kept simple:

```
JarScanner           walk the directory, compute SHA-256, produce exactly one record per input
   │
   ├── FabricMetadataParser     reads fabric.mod.json / quilt.mod.json
   │
   └── NeoForgeMetadataParser   reads neoforge.mods.toml / mods.toml
   │
   ▼
InvasivenessAnalyzer invasiveness rating + mixin target inference (descriptor level only, no bytecode)
   │
   ▼
ConflictPredictor    merge loader variants by modId, compare targets pairwise, rank HIGH/MEDIUM/LOW/NONE
   │
   ▼
ReportWriter         candidates.jsonl / summary.csv / conflicts.csv / INFO summary
```

The unified data contract is `ModCandidate` — see [`SPEC.md`](./SPEC.md) §1.

**Depends on**: Gson (JSON), night-config (TOML), SLF4J + Log4j2 (logging), JUnit 5 + AssertJ (tests).

**Does not depend on**: Minecraft, Fabric Loader, Forge, NeoForge, Mixin, ASM.
This stage is pure offline text parsing; introducing any of the above is a violation.

### Invasiveness rating

Fabric mods expect "vanilla MC + mixin injection"; NeoForge mods expect "MC already patched by Forge" — the two
baseline assumptions differ. The metadata layer cannot predict runtime conflicts, but it *can* identify deeply
invasive mods before launch.

| Tier | Meaning |
| --- | --- |
| `RENDER` | Touches the render pipeline — highest conflict risk |
| `CORE` | Manipulates bytecode directly |
| `MIXIN` | Injects behaviour at runtime |
| `API` | Consumes API contracts only |

Scanning is **descriptor-level (L1) only**: it reads zip entry names and metadata text, never class content.
Constant-pool-level scanning (L2) belongs to a later stage.

The `RENDER` and `CORE` tiers, plus cheat clients and OptiFine, are all marked `EXCLUDED` with
**no compatibility promise whatsoever** — this is a product decision, not a technical failure: M0 can only
predict declarative conflicts, and behavioural conflicts from deeply invasive mods are entirely outside this
stage's observational reach.

They are **never removed from the statistics** — all of them stay in `summary.csv`. Excluded mods are the most
important output.

---

## Engineering discipline

Every item below is **enforced at build time**, not a style suggestion.
Each one maps directly to an upstream problem this project exists to correct.

1. **No empty catch.** Catching an exception must do three things: fill in `failureCode` and `failureMessage`,
   log at `ERROR`, and mark the record `FAILED`.
2. **Never demote a failure to a warning.** One jar failing without affecting the others is fine, but the
   failure must be counted, classified and presented.
3. **No `assumeTrue` / `assumeFalse`.** When a condition is not met the test must FAIL, not SKIP. The skipped
   count is always 0; missing the full dataset falls back to built-in samples, and only a missing built-in
   sample set is a failure.
4. **Do not mock away real file reads.** All seven failure codes
   (`NO_METADATA`, `MALFORMED_JSON`, `MALFORMED_TOML`, `MISSING_REQUIRED_FIELD`, `IO_ERROR`, `NOT_A_ZIP`,
   `INTERNAL_ERROR`) are each triggered by genuinely malformed jars.
5. **No `System.out.println` / `printStackTrace`.** Everything goes through SLF4J parameterised logging,
   enforced by a build-time static check.
6. **Never skip an input file.**
7. **Never implement features the spec has not defined, never leave "extensibility" hooks.**

Log-level rules are in [`SPEC.md`](./SPEC.md) §5.

---

## Dataset compliance

The test dataset is downloaded via the public Modrinth API and used **only for offline testing and
compatibility research**:

- This repository **contains no real mod jars**; collection output is never committed
  (the only jars in-tree are 11 self-made test samples under `src/test/resources/fixtures/`)
- Each download is checked for being openable and containing metadata; corrupted files are discarded
- Copyright and licensing of each mod remain with its own author; this project claims no rights over any mod content
- Minecraft itself is Mojang's proprietary property; this project does not download, modify or distribute any
  Minecraft file

---

## Seeking collaborator

> ⚠️ **The next stage of this project requires deep JVM knowledge, and the author does not know Java.**

This is not "please write code for me" — it is a search for a **collaborator who can make technical
judgements**. The spec, the dataset and the verification system are already in place.

**What has been achieved**

- M0 complete: 195 real jars parsed in full, record count always equals input count (`OK 191 + FAILED 4 = 195`)
- Manual metadata sampling: `modId` / `version` / `depends` — **10/10 consistent**, 0 mismatches across 47
  dependency declarations
- All ten acceptance criteria of [`SPEC.md`](./SPEC.md) v0.7 met
- 42 tests, 0 failures, 0 errors, **0 skipped**
- Pre-launch conflict warning: 331 HIGH pairs (4.96%)

**What I bring**

- A complete spec: [`SPEC.md`](./SPEC.md) v0.7 — fields, decision signals, tiering rules, output formats and
  acceptance criteria are all written down, not agreed verbally
- A real dataset collection pipeline: generate a list from the public Modrinth API, download, auto-sort by
  in-jar metadata, verify one by one — fully scripted
- A set of enforced engineering rules: no empty catch, no `assumeTrue`, no demoting failures to warnings,
  skipped count must be 0
- The full decision record: [`PROGRESS.md`](./PROGRESS.md), 92 work entries + 54 documented pitfalls, every
  number change and every mistaken judgement on the record

**What I need**

M2 (unified bootstrap and transform orchestration) requires deep JVM knowledge: class loading, bytecode
structure, how Mixin / ASM actually work, and tracing a crash stack back to an injection point. **I cannot
supply this part**, and I have no intention of pasting in a pile of AI-generated code that merely looks like it
runs.

What is missing is someone who can answer questions like "how do you actually decide whether two mixin
injections conflict" or "which constant-pool entry in a class file proves the target class" — not someone who
works through tickets.

If you want to get involved, please read [`CONTRIBUTING.md`](./CONTRIBUTING.md) and
[`SPEC.md`](./SPEC.md) first, then open an issue describing which part you want to own and why.

---

## Contributing

Contributions are welcome, but please read [Engineering discipline](#engineering-discipline) and the
[contributing guide](./CONTRIBUTING.md) first — this project has zero tolerance for "it runs, ship it".

Before opening a PR, confirm:

- [ ] `gradle build` passes
- [ ] Skipped test count is **0**
- [ ] The new behaviour is already defined in `SPEC.md`
- [ ] No Minecraft / loader-related dependency was introduced
- [ ] No `assumeTrue` / `assumeFalse`
- [ ] No empty catch block, no `System.out.println`
- [ ] `OK + FAILED == input count` still holds

---

## License

This project is licensed under the [MIT License](./LICENSE).

Note the licence differences among upstream and adjacent projects: Fabric Loader is Apache-2.0,
Forge / NeoForge is LGPL-2.1, Sinytra Connector is LGPL-3.0, Kilt is LGPL-2.1. The M0 stage of this project
depends on none of them.

---

## Acknowledgements

- [**Forbric**](https://github.com/Ray-T-r/Minecraft-Forbric-mod-loader) — proved the demand for a "unified
  loader" is real, and through its own mistakes defined this project's engineering discipline. Apache-2.0.
- [**Sinytra Connector**](https://github.com/Sinytra/Connector) and
  [**Kilt**](https://github.com/KiltMC/Kilt) — provided the complete experience and known boundaries of the
  translation-layer approach.
- [**Modrinth**](https://modrinth.com) — the open API that made collecting a real dataset possible.
- All mod authors whose mods are included in this dataset.
