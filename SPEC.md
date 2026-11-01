# SPEC.md — M0–M1: Offline Mod Metadata Normalization, Invasiveness Rating, Dependency Satisfiability and Cycle Detection

<p align="center">
  <b>English</b> · <a href="./SPEC.zh-CN.md">简体中文</a>
</p>

Version: v1.0
Status: draft pending human confirmation
Upstream inspiration: Forbric v0.3.0 (Ray-T-r/Minecraft-Forbric-mod-loader, Apache-2.0)

---

## 0. This project is not currently a mod loader

M0 **does not start Minecraft, does not load classes, does not process mixins, does not rewrite bytecode**.
It does exactly one thing: given a mod jar file, read its metadata and structural characteristics and produce
one structured `ModCandidate` record.

M1 (§3.6) builds a dependency graph on top of those records and detects cycles. It does not start Minecraft
either, does not load classes, does not read class files, and **does not output an install order** (§3.6.5).

### Scope (must be understood precisely)

M0's output is an **"admission check for whether a mod set can start"**, not **"a guarantee that it will run stably"**.

- M0 **can** predict declarative conflicts: missing dependencies, disjoint version ranges,
  explicit incompatibility declarations
  (cycle detection is **not** part of M0 or M0.5 — it is specified in M1, §3.6)
- M0 **cannot** predict behavioural conflicts: two mixins targeting the same method, two mods replacing the same
  render stage, NPEs caused by registry lifecycle ordering

Behavioural conflicts only surface at runtime. **Marketing M0 as "a guarantee of stable operation" is misleading.**

Explicitly out of scope (the AI must not implement these early, nor "add them in passing"):

- Any ClassLoader, JVM agent, or bytecode manipulation (ASM / Mixin / Tiny Remapper)
- Any Minecraft launch, download or patching logic
- Any cross-loader interop (event bridges, item/fluid/energy)
- Any GUI, config file or installer
- Any "automatic compatibility" or "automatic repair" capability
- Any install order, load order or topological order as output — forbidden by §3.6.5

---

## 1. Unified data contract: ModCandidate

Each jar produces **exactly one** record. A parse failure must also produce a record with `status = "FAILED"` —
**never skipped, never omitted, never null**.

| Field | Type | Required | Notes |
|---|---|---|---|
| `sourceFile` | string | yes | jar file name (no path) |
| `sha256` | string | yes | SHA-256 of the whole jar, lowercase hex |
| `loaderFamily` | enum | yes | `FABRIC` / `NEOFORGE` / `FORGE` / `QUILT` / `UNKNOWN` |
| `status` | enum | yes | `OK` / `FAILED` |
| `modId` | string | no | mod ID. `null` when `FAILED` |
| `version` | string | no | raw version string, preserved verbatim, not normalised |
| `name` | string | no | display name |
| `side` | enum | no | `CLIENT` / `SERVER` / `BOTH` / `UNKNOWN` |
| `depends` | array\<Constraint\> | no | hard dependencies |
| `recommends` | array\<Constraint\> | no | soft dependencies (warn if missing, but continue) |
| `breaks` | array\<Constraint\> | no | conflict declarations |
| `provides` | array\<string\> | no | capabilities/IDs this mod provides |
| `entrypoints` | array\<string\> | no | entrypoint key list (Fabric); empty array for Forge-family |
| `mixinConfigs` | array\<string\> | no | paths of mixin config files inside the jar (paths only, contents not parsed) |
| `mixinTargets` | array\<string\> | no | target classes this mod declares it injects into, e.g. `net.minecraft.client.render.GameRenderer`, see §2.4 |
| `mixinTargetsInferred` | boolean | no | whether `mixinTargets` was inferred from naming conventions (always `true` in M0), see §2.4 |
| `nestedJars` | array\<string\> | no | embedded jar paths (Fabric `jars`; Forge `jarJar`) |
| `modType` | enum | no | mod type, rules in §2.3 |
| `invasiveness` | enum | no | invasiveness rating, rules in §2.3 |
| `invasivenessSignals` | array\<string\> | no | concrete signals that triggered the rating, see §2.3 |
| `supportPolicy` | enum | no | `SUPPORTED` / `DEGRADED` / `EXCLUDED`, see §2.3 |
| `failureCode` | enum | no | required when `status=FAILED`, see §4 |
| `failureMessage` | string | no | human-readable reason |
| `unknownFields` | array\<string\> | no | top-level field names present in the metadata but not defined by this spec |

### Constraint structure

| Field | Type | Notes |
|---|---|---|
| `targetId` | string | mod ID or capability ID of the dependency target |
| `versionRanges` | array\<string\> | **raw** version-range strings, preserved verbatim |
| `required` | boolean | `true` for hard dependencies |
| `ordering` | enum | `NONE` / `BEFORE` / `AFTER` |
| `side` | enum | `CLIENT` / `SERVER` / `BOTH` / `UNKNOWN` |

> Version-range **strings** are not evaluated in M0 — they are only preserved verbatim and format-registered.
> Range evaluation belongs to M0.5; the AI must not invent comparison logic at this stage.

---

## 2. Metadata source mapping

### 2.1 Fabric / Quilt: `fabric.mod.json` (jar root)

| Unified field | Source |
|---|---|
| `modId` | `id` |
| `version` | `version` |
| `name` | `name` |
| `side` | `environment` (`*`→`BOTH`, `client`→`CLIENT`, `server`→`SERVER`) |
| `depends` | `depends`, `required=true` |
| `recommends` | `recommends`, `required=false` |
| `breaks` | `breaks`, `required=true` |
| `provides` | `provides` |
| `entrypoints` | all keys of `entrypoints` |
| `mixinConfigs` | `mixins` (array) |
| `nestedJars` | `file` of each entry in `jars` |

`loaderFamily`: `fabric.mod.json` present and `schemaVersion >= 1` → `FABRIC`.
If the jar also contains `quilt.mod.json` → `QUILT`.

### 2.2 NeoForge / Forge: `META-INF/neoforge.mods.toml` or `META-INF/mods.toml`

| Unified field | Source |
|---|---|
| `modId` | `modId` of the **first** `[[mods]]` entry |
| `version` | that entry's `version` (may be `${file.jarVersion}`; preserve verbatim, not a failure) |
| `name` | `displayName` |
| `side` | `displayTest` (`NORMAL` is treated as `BOTH`) |
| `depends` | `type="required"` in `[[dependencies.<modId>]]` |
| `recommends` | `type="optional"` |
| `breaks` | `type="incompatible"` |
| `provides` | `provides` of `[[mods]]` (NeoForge) |
| `nestedJars` | `[[jarJar]]` (NeoForge) |
| `mixinConfigs` | the `config` field of `[[mixins]]` (path only, contents not parsed) |

`loaderFamily`: `META-INF/neoforge.mods.toml` present → `NEOFORGE`;
only `META-INF/mods.toml` present → `FORGE`.

> **Legal values of `displayTest`**: `NORMAL`, `IGNORE_ALL_VERSION`, `MATCH_VERSION`, `IGNORE_SERVER_VERSION`.
> Values outside this set record `UNKNOWN` and emit WARN, but **must not be judged `FAILED`** — false positives
> would harm perfectly normal mods.
> The `ordering` / `side` of a dependency object are not in this category; unknown values there must be judged
> `MALFORMED_TOML` (see §4).

> **Multiple `[[mods]]` entries**: take only the first, and record a WARN.

---

### 2.3 Invasiveness rating (this project's first differentiating capability)

**Background**: Fabric and NeoForge mods are built on different baseline assumptions — Fabric mods expect
"vanilla MC + mixin injection", NeoForge mods expect "MC already patched by Forge". The metadata layer cannot
predict whether the two will conflict at runtime, but it **can identify deeply invasive mods and warn before
launch**.

**Scan scope is limited to L1 (descriptor level)**: only **file names and metadata text** inside the zip are
read; class file contents are never parsed and classes are never loaded.
Any constant-pool scanning (L2) belongs to a later stage and is forbidden in M0.

#### Decision signals

Evaluated in the following order, **stopping at the first hit** (take the highest tier):

| Tier | Signal (any one suffices) | Meaning |
|---|---|---|
| `RENDER` | mixin config or entrypoints reference classes under `net.minecraft.client.render`; mod name/ID hits the known render-mod list (below) | Touches the render pipeline — highest conflict risk |
| `CORE` | `META-INF/coremods.json` present; transformer entries present in `mods.toml`; ASM-related class paths present in the jar | Manipulates bytecode directly |
| `MIXIN` | any `mixins.*.json` config present, or `mixinConfigs` non-empty | Injects behaviour at runtime |
| `API` | none of the above | Consumes API contracts only |

**Known render / shader mod list** (hard-coded constant, may be extended later):
`sodium`, `iris`, `oculus`, `optifine`, `rubidium`, `embeddium`, `sodiumextra`,
`reeses-sodium-options`, `dynamiclights`, `lambdynamiclights`

#### `modType` decision

| Value | Condition |
|---|---|
| `LIBRARY` | `provides` non-empty, or ID hits the known library list (`fabric-api`, `fabric`, `architectury`, `neoforge`, `forge`, `kotlinforforge`, `fabric-language-kotlin`) |
| `SHADER_LOADER` | ID hits `iris` / `oculus` / `optifine` |
| `OPTIMIZER` | ID hits `sodium` / `rubidium` / `embeddium` |
| `HACK_CLIENT` | ID hits the known cheat-client list (`meteor-client`, `wurst`, `impact`, `future`, `liquidbounce`, `athena`, `rusherhack`) |
| `NORMAL` | everything else |

#### `supportPolicy` decision

| Value | Condition |
|---|---|
| `EXCLUDED` | `modType = HACK_CLIENT`, ID is `optifine`, **or `invasiveness` is `RENDER` / `CORE`** |
| `DEGRADED` | **no source in M0**, always 0; the value is retained for later stages |
| `SUPPORTED` | everything else |

**These three are product decisions, not technical failures.** Mods marked `EXCLUDED` carry no compatibility
promise whatsoever.

> **Why `RENDER` / `CORE` are `EXCLUDED` rather than `DEGRADED`**:
> M0 can only predict **declarative** conflicts, not **behavioural** ones.
> Mods that deeply invade the render pipeline or manipulate bytecode directly have behavioural conflicts
> entirely beyond this stage's observational reach, so they must be treated as "no promise made" rather than
> "supported with degradation".
> This is consistent with the positioning of conflict prediction in §2.4: warn only, never guarantee.

#### Logging requirements

- On hitting `RENDER` / `CORE`, emit **WARN**, format: `侵入深度 {} 于 {}，信号：{}`
- On hitting `EXCLUDED`, emit **WARN**, format: `排除模组 {}（{}），不做兼容性承诺`
- Neither may be demoted to DEBUG or silenced for any reason

---

### 2.4 Mixin target index and static conflict prediction (this project's second differentiating capability)

#### Why it is needed

Dependency conflicts (§3) only express "who needs whom". What actually breaks mod combinations is
**behavioural conflict**: two mods injecting code into the same method of the same class.

The traditional approach is to accumulate a blacklist manually after runtime crashes (this is exactly how
Sinytra Connector's incompatible list came to be). This project does **pre-launch prediction**: without starting
the game, using static information only, point out "these two mods may collide".

#### What M0 can actually obtain (honest boundary)

Fabric's mixin config file is **plain-text JSON**, shaped like:

```json
{
  "package": "me.jellysquid.mods.sodium.mixin",
  "mixins": ["GameRendererMixin", "LevelRendererMixin"],
  "client": ["RenderSystemMixin"]
}
```

It gives the **mixin implementation class name**, but **not the target class name** —
the target class is written in the `@Mixin(GameRenderer.class)` annotation on the mixin implementation class,
and reading annotations requires parsing class files (L2, forbidden at this stage).

M0 therefore uses **naming-convention inference**:

| Mixin implementation class name (package stripped) | Inferred target class |
|---|---|
| `GameRendererMixin` | `GameRenderer` |
| `LevelRendererMixin` | `LevelRenderer` |
| `MinecraftMixin` | `Minecraft` |
| `MixinGameRenderer` | `GameRenderer` |
| `MixinBloodFluid` | `BloodFluid` |

**All three** inference rules must be implemented (real data contains all of these styles simultaneously):

1. **Suffix form**: strip a trailing `Mixin` / `MixinPlugin` / `Accessor` / `Invoker`
2. **Prefix form**: strip a leading `Mixin`
3. **Nested classes**: for names containing `$`, split on `$`, **apply the two rules above to each segment
   independently**, then rejoin

| Mixin implementation class name | Inferred target class |
|---|---|
| `VertexMultiConsumerMixin$DoubleMixin` | `VertexMultiConsumer$Double` |
| `VertexMultiConsumerMixin$MultipleMixin` | `VertexMultiConsumer$Multiple` |

> Suffix-only stripping is not enough. In real data, **54** of 191 OK records declared `mixinConfigs` yet
> yielded no targets at all — because a large number of real configs use prefix-style naming (BiomesOPlenty's
> `MixinBloodFluid`, `MixinLiquidNullFluid`).
> Worse, `MixinGameRenderer` would remain as a separate target and be **counted separately** from
> `GameRenderer`, directly deflating target hit counts and distorting the rarity statistics.
>
> **Handling prefix/suffix but not nested classes loses an entire target.** In real data sodium's
> `VertexMultiConsumerMixin$DoubleMixin` was inferred as `VertexMultiConsumerMixin$Double` (leaving `Mixin` in),
> and those two entries are the only mixins in sodium pointing at `VertexMultiConsumer` —
> the real target class never entered the index at all, so conflict prediction misses it entirely.
> After the fix the target name becomes `VertexMultiConsumer$Double`; the HIGH count did not change (the wrong
> name was already in the set), so **the evidence must be the actual strings in the target set, not the numbers**.

**Limitations must be stated explicitly**:

- Inference yields only a **simple class name** (e.g. `GameRenderer`), without the full package path
- There are false negatives (when the target class name is unrelated to the mixin class name) and false
  positives (same-named classes in different packages)
- Therefore `mixinTargetsInferred` is **always `true`** in M0, and the output must explicitly state these are
  inferred values

> An exact target class requires reading the type descriptor in the class file annotation (L2).
> That capability belongs to a later stage, at which point the "must not introduce ASM" prohibition must be
> re-evaluated and confirmed by a human before being lifted.
> **Implementing L2 in M0 is strictly forbidden for any reason.**

#### Conflict prediction rules

For all `status=OK` mods, first **aggregate by unique `modId`** (Fabric / NeoForge variants of the same mod are
merged into one, so a mod is never compared against itself), then compare `mixinTargets` pairwise.

##### Step 1: compute target rarity (a necessary correction forced by real data)

Classes like `GameRenderer`, `Player`, `Minecraft` and `LivingEntity` are **injected into by a large number of
mods** — sharing them means nothing. First real run: among 116 mods and 6670 pairs, the naive rule
"same target class name ⇒ HIGH" produced **649 HIGH pairs (about 10%)**, the vast majority caused by a handful
of high-frequency target classes. **That signal-to-noise ratio is unusable.**

So the number of mods hitting each target class must be counted first:

| Concept | Definition |
|---|---|
| `targetModCount` | number of mods that hit this target class |
| `UBIQUITOUS_THRESHOLD` | **10%**, i.e. a target class with `targetModCount ≥ ceil(total mods × 0.10)` is **ubiquitous (everyone patches it)** |

> **25% was wrong; it has been lowered to 10%.** With 25% the threshold was 29, while in real data the top
> target `Minecraft` only reached 20 and `GameRenderer` only 14 — **no target qualified at all**,
> so the rarity rule spun its wheels and HIGH stayed at 649.
>
> The root cause is that the target distribution is **extremely long-tailed**: 116 mods spread across hundreds
> of distinct target classes, so a head class being hit by 12% of mods already means "everyone patches it".
> 25% simply does not suit this distribution.
>
> At 10% (116 × 0.10 = 11.6, rounded to 12), the real head targets
> `Minecraft 20`, `GameRenderer 14`, `Entity 13`, `Gui 13`, `LivingEntity 12`
> are correctly marked ubiquitous, and the rarity rule finally takes effect.

##### Step 2: grading

| Level | Condition | Meaning |
|---|---|---|
| `HIGH` | shared targets are **all** non-ubiquitous (and at least 1 is shared) | Colliding somewhere unusual — genuinely worth looking at |
| `MEDIUM` | **at least 1** shared target is ubiquitous | Colliding on a class everyone patches — likely noise |
| `LOW` | one side is `RENDER`/`CORE` and the other is `MIXIN` | Low risk |
| `NONE` | everything else | — |

> **Why all-rare rather than any-rare** (a semantic correction forced by real data):
>
> Taken literally, any-rare ("at least 1 non-ubiquitous shared target ⇒ HIGH") yields **810 HIGH pairs (12.14%)**,
> which is *higher* than the 649 baseline without the rarity rule — the rule would be pointless.
>
> It is also semantically indefensible: if two mods share 3 targets, 2 of which are classes everyone patches
> like `GameRenderer` and `Minecraft` and only 1 of which is rare, then **where they actually collide is most
> likely those high-frequency classes**; the rare class is hit incidentally. Grading that HIGH is noise.
>
> all-rare is what expresses "they collide somewhere genuinely unusual". Real comparison:
>
> | | HIGH | share | content of the top 10 |
> |---|---:|---:|---|
> | no rarity rule (baseline) | 649 | 9.7% | all `GameRenderer` / `Player` |
> | any-rare | 810 | 12.1% | still dominated by high-frequency classes |
> | **all-rare (adopted)** | **331** | **4.96%** | concrete targets such as `WorldRenderer`, `ToastComponent`, `ParticleManager` |

##### Rarity statistics must be output

Beyond `conflicts.csv`, the summary must additionally print the **10 most-hit target classes**, shaped like:

```
[INFO]  高频目标类：GameRenderer 42 | Player 38 | Minecraft 31 | LivingEntity 27 …
```

Without this line there is no way to judge whether HIGH is trustworthy — **the high-frequency target classes
determine the noise ceiling of this report**.

**This is not a conclusion, it is a warning.** The output must state clearly: static prediction has both false
negatives and false positives; HIGH does not mean it will definitely crash, and NONE does not mean it is
definitely safe.

#### Output

Conflict prediction results are written to a separate `conflicts.csv`:

```
modA,modB,level,sharedTargets
sodium,iris,HIGH,"GameRenderer;LevelRenderer"
```

and one line is emitted in the stdout summary:

```
[INFO]  冲突预测：HIGH 3 组 | MEDIUM 7 组 | LOW 12 组（静态推断，非运行结论）
```

#### Logging requirements

- Every `HIGH` pair detected emits **WARN**, format: `潜在冲突 {} × {}，共同目标：{}`
- The summary must contain the phrase "静态推断，非运行结论" (statically inferred, not a runtime conclusion);
  it must not be omitted
- Neither may be demoted to DEBUG or silenced for any reason

---

## 3. Dependency graph (M0 second phase: register only, do not solve)

M0's second phase takes all `ModCandidate`s as input and outputs:

- `nodes`: all `modId`s with `status=OK`
- `edges`: directed edges of the three kinds `depends` / `recommends` / `breaks`
- `report.unsatisfiedRequired`: hard dependencies whose target ID does not exist among all nodes
- `report.cycles`: cycles (formed by hard dependencies) — **not produced by M0 or M0.5; specified in §3.6 (M1)**
- `report.duplicateIds`: the same `modId` appearing with multiple different `version`s

**M0 does not solve version ranges and does not perform topological sorting.**

> This subsection is an in-memory contract: none of these fields is written to a file. M1 (§3.6) covers `nodes`,
> `edges` and `report.cycles` only, and its cycle graph uses hard dependencies alone — `recommends` and `breaks` are
> registered here but never become edges there. `report.unsatisfiedRequired` and `report.duplicateIds` are **not**
> part of M1 and stay unimplemented; M1 must not implement them, nor "add them in passing".
> M1 does not solve version ranges either, sorts only to decide acyclicity, and never outputs an order (§3.6.5).

---

## 3.5 Version range evaluation and satisfiability (M0.5)

M0.5 evaluates the version ranges that M0 only registered. It answers exactly one question per dependency:
**"does a mod in this set satisfy what this declaration asks for?"**

### 3.5.1 Scope boundary (must not be crossed)

M0.5 **does**:

- parse and evaluate version range strings from both loader families
- resolve each declared dependency against the mods actually present
- report explicit `breaks` declarations that are matched

M0.5 **does not**:

- perform topological sorting or produce an install/load order
- build or "solve" a dependency graph (no version backtracking, no candidate selection)
- download anything, or suggest a version to install
- merge its results into `conflicts.csv` (M0's numbers must stay reproducible; M0.5 writes a separate file)

### 3.5.2 Environment-provided IDs (must be handled, or nearly every mod reports MISSING)

Loader and platform IDs are **not mods in the dataset**. They are supplied by the runtime environment and must be
resolved against declared environment facts, **never** reported as `MISSING_ID`.

| ID | Meaning | Resolution |
|---|---|---|
| `minecraft` / `minecraft_version` | game version | environment fact declared for the run |
| `fabricloader` / `fabric-loader` | Fabric Loader version | environment fact |
| `quilt_loader` / `quilt-loader` | Quilt Loader version | environment fact |
| `neoforge` / `forge` | loader version | environment fact |
| `java` | JDK version | environment fact |

Rules:

- these IDs are resolved against the declared environment versions; absence from the dataset is normal
- any other ID that is absent from the dataset, from the environment facts **and** from every `provides` list
  (see §3.5.5) **is** `MISSING_ID`
- environment facts must be printed once at startup (INFO) so the numbers are auditable
- `recommends` on an absent ID is a WARN, never a failure (unchanged from M0)

---

### 3.5.3 Version range syntax (two incompatible families — must be parsed separately)

**Fabric / Quilt** (`fabric.mod.json` constraint strings):

| Form | Meaning |
|---|---|
| `*` | any version |
| `>=1.2.3` / `>1.2.3` / `<=1.2.3` / `<1.2.3` / `==1.2.3` | comparison |
| `~1.2` | tilde: `>=1.2, <1.3` |
| `1.21.x` | the `x` (or `X` / `*`) segment wildcard-matches |
| `>=1.2 <1.5` | space-separated constraints, **all** must hold (AND) |

**NeoForge / Forge** (`mods.toml` `versionRange`, Maven range syntax):

| Form | Meaning |
|---|---|
| `[21.1.219,)` | `>= 21.1.219` |
| `(21.1.219,)` | `> 21.1.219` |
| `[1.21,1.22)` | `>= 1.21` and `< 1.22` |
| `(,1.22]` | `<= 1.22` |
| `[1.21.1]` | exactly `1.21.1` |
| `21.1.219` (unbracketed) | minimum-inclusive `>= 21.1.219` (Maven soft-requirement convention) |

An unbracketed NeoForge value must log which convention rule was applied (DEBUG), because this is the one form
where the two families disagree.

### 3.5.4 Version comparison rules

- split on `.` and `-` into segments; compare segment by segment
- numeric segments compare numerically; non-numeric segments compare lexicographically
- a missing trailing segment counts as `0` (`1.21` == `1.21.0` for comparison purposes)
- a pre-release segment (`-beta`, `-rc1`) sorts **lower** than the same version without it
- a wildcard segment matches any value in that position
- comparison is **string-based and deterministic**: no "semver library", no external dependency

### 3.5.5 Existence and outcome per dependency (exactly one each)

An ID is resolved against, **in this order, stopping at the first hit**:

| # | Condition | Resolution |
|---|---|---|
| 1 | the ID is the `modId` of a `status=OK` record | use that mod's actual version; `provides` aliases are **not** consulted |
| 2 | the ID is an environment-provided ID (§3.5.2) | use the declared environment fact |
| 3 | the ID is declared in `provides` by exactly one `modId` | use the owning mod's actual version |
| 4 | the ID is declared in `provides` by two or more `modId`s | `AMBIGUOUS_ALIAS`; no owner is chosen |
| 5 | none of the above | `MISSING_ID` |

**Rule 1 has the highest priority: a real `modId` is authoritative and is never made ambiguous by another mod
declaring the same string in `provides`.** An alias is only a fallback for IDs that do not exist as mods;
`fabric-api` the mod always wins over `fabric-api` used as an alias.

One case stays a conflict: an alias that **collides with an environment-provided ID**. If a mod declares
`provides: ["minecraft"]` (or `java`, `fabricloader`, `neoforge`, …), a dependency on that environment ID is
`AMBIGUOUS_ALIAS`, and both the environment fact and the declaring mod are printed at WARN. The environment is not
given priority over a mod that claims the same ID, because the collision itself is a loading error.

A `provides` entry is an alias of the mod that declares it, and Fabric Loader treats it as a mod that exists
(`fabric.mod.json` documentation). The owner set is deduplicated by `modId`: a mod that lists its own `modId` in
`provides` is still a single owner.

| Outcome | Condition |
|---|---|
| `SATISFIED` | the ID resolves through rule 1, 2 or 3 and the resolved version is in range. For a `breaks` declaration: the target is absent (rule 5) or resolves and the version is **not** in range (i.e. no conflict) |
| `MISSING_ID` | the ID falls through to rule 5 |
| `VERSION_MISMATCH` | the ID resolves through rule 1, 2 or 3, but the resolved version does not satisfy the range |
| `BREAKS_VIOLATED` | a `breaks` declaration that resolves (rules 1–3) with a version in range |
| `AMBIGUOUS_ALIAS` | the ID hits rule 4, or is an environment ID that a mod also declares in `provides`; no owner is chosen and `resolvedVersion` is `null`. Every candidate is printed at WARN with its version and how it was found (`modId` / `provides` / environment) |
| `UNPARSEABLE` | the range string cannot be parsed |

**Why `AMBIGUOUS_ALIAS` is a sixth outcome instead of reusing one of the five**: `MISSING_ID` would be factually
wrong (the ID does exist), `SATISFIED` would silently pick one candidate — exactly what this stage must not do —
and `VERSION_MISMATCH` / `UNPARSEABLE` describe version strings, not identity. Since an ambiguous ID blocks an
admission decision in the same way as the other three, its count is printed on the same `不可满足` summary line.

**`UNPARSEABLE` must never be silently treated as satisfied and never be silently dropped.** It must be counted,
logged at WARN with the offending string, and reported in the summary. An unparseable range is a defect of this
tool, not of the mod.

---

### 3.5.6 Output

New file **`dependencies.csv`** (one row per declared dependency, header included):

```
modId,version,depId,depKind,declaredRange,resolvedVersion,outcome
```

- `depKind`: `required` / `recommends` / `breaks`
- `resolvedVersion`: the version actually found, or `null` when absent or ambiguous
- every declared dependency appears **exactly once** — never deduplicated, never omitted

stdout (INFO) must print:

```
依赖总数：硬依赖 N | 软依赖 N | 冲突声明 N
不可满足：MISSING_ID N | VERSION_MISMATCH N | BREAKS_VIOLATED N | AMBIGUOUS_ALIAS N
无法解析的版本区间：N（实现缺陷，不是模组问题）
```

### 3.5.7 M0.5 acceptance criteria

1. every declared dependency of every `status=OK` record appears exactly once in `dependencies.csv`
2. `UNPARSEABLE` count is printed explicitly and is **not** folded into `SATISFIED`
3. no environment-provided ID (§3.5.2) is ever reported as `MISSING_ID`
4. manual spot-check of 10 dependencies against the raw metadata files: `modId` / `version` / `declaredRange`
   / `outcome` must match, 100%
5. `gradle clean test` passes with **0 skipped**; tests must not use `assumeTrue`
6. M0's own numbers (HIGH 331 / 6670, coverage gap 10/191) must remain unchanged — M0.5 adds a file, it does
   not rewrite M0's results
7. `provides` aliases resolve to the version of their owner; when an ID is declared by two or more mods the row
   is `AMBIGUOUS_ALIAS`, the count is printed, and a WARN lists every candidate
8. a dependency whose ID is a real `modId` resolves through rule 1 even when another mod declares the same string
   in `provides`; it is never reported as `AMBIGUOUS_ALIAS` (regression guard for the priority rule)

---

## 3.6 Dependency graph, topological sort and cycle detection (M1)

M1 builds the graph that M0 only registered and M0.5 only evaluated one declaration at a time. It answers exactly one
question: **"does the hard-dependency set of this collection contain a cycle, and if so, which mods are in it?"**

### 3.6.1 Scope boundary (must not be crossed)

M1 **does**:

- build one directed graph per loader universe (§3.6.2), from `status=OK` records only
- classify every hard-dependency declaration into exactly one bucket (§3.6.3)
- decide acyclicity by topological sort and locate cycle groups by strongly connected components (§3.6.4)
- write `cycles.csv` and the three INFO lines of §3.6.6

M1 **does not**:

- start Minecraft, load classes, read class files, process mixins or bytecode, download anything
- re-evaluate version ranges, re-resolve IDs, or re-judge any of the six outcomes of §3.5.5
- rewrite `candidates.jsonl`, `summary.csv`, `conflicts.csv` or `dependencies.csv`
- produce an install order, a load order or any topological order — hard prohibition in §3.6.5
- implement §3's `unsatisfiedRequired` or `duplicateIds`; both stay unimplemented and are not part of M1

> `dependencies.csv` is M0.5's artifact and keeps its content byte for byte. M1 does not read it as the source of
> edges: a row carries `modId` + `version` + `declaredRange` but **not** the `loaderFamily` of the declaring record,
> so a row cannot be attributed to a universe. M1 derives its edges from the `ModCandidate` records, which do carry
> `loaderFamily`.

### 3.6.2 Loader universes (the reason this section exists)

The dataset keeps Fabric and NeoForge jars in one directory, but one machine runs one loader family. A single mixed
graph invents cycles that no machine can have. The real dataset makes this concrete: 191 `status=OK` records collapse
to 116 unique `modId`s, and **71 of those 116 have both a Fabric and a NeoForge variant**. Merging them by `modId`
would union two declaration sets that never coexist.

Rule: **one graph per loader universe. An edge never crosses universes.**

| Universe | Records it contains | Rule |
|---|---|---|
| `FABRIC` | `loaderFamily = FABRIC` or `QUILT` | `QUILT` is folded in because §2.1 assigns it only when `fabric.mod.json` is also present, i.e. the jar carries Fabric-readable metadata |
| `FORGE` | `loaderFamily = FORGE` | deliberately **not** folded into `NEOFORGE`: that would be a compatibility claim, and §0 forbids cross-loader claims |
| `NEOFORGE` | `loaderFamily = NEOFORGE` | — |
| — (excluded) | `loaderFamily = UNKNOWN` | never a node; excluded from graph construction and counted (§3.6.6) |

Consequences, all intentional:

- the same `modId` in two universes is **two different nodes**, each carrying only its own family's declarations
- a dependency whose target is a node only in another universe creates **no edge**, and is counted as `OTHER_UNIVERSE`
- a cycle that exists only when Fabric and NeoForge variants are mixed is therefore **structurally impossible**,
  not filtered out afterwards

> **Factual statement about the current dataset, not a claim of validation.** `QUILT` has 0 records (all are folded
> into `FABRIC`), `FORGE` has 0 records, and the `OTHER_UNIVERSE` bucket is 0 as well — no hard dependency in the
> dataset targets a `modId` that exists only in another universe. Only two universes are non-empty, and the
> cross-loader routing rule of this section is **not exercised by real data at all**: it is covered by the
> constructed records of §3.6.7 items 1 and 2 and by nothing else. This must never be written as
> "the cross-loader rule was verified to work"; the only verified statement is that FABRIC and NEOFORGE were
> separated on a dataset where the separation had no visible effect.

### 3.6.3 Nodes and edges (what enters the graph, and what does not)

**Node**: one per `(universe, modId)`. All `status=OK` records with that `modId` in that universe collapse into one
node, and their edge sets are unioned. M1 does not choose a version — the real dataset has 4 `modId`s with two records
inside the same family.

**Edge**: `A → B` exists if and only if a record of `A` in the universe declares a **hard dependency** whose
`targetId` is exactly the `modId` of node `B` in the same universe.

A hard dependency is a `Constraint` **read from `depends`**. The `required` flag must not be used as the
discriminator: §2.1 and §2.2 set `required=true` for `breaks` as well.

| Declaration | Enters the graph? | Why |
|---|---|---|
| `depends` | yes, when the target is a node of the same universe | the only declaration that asserts "this must be present" |
| `recommends` | **never** | a missing soft dependency is normal (§3.5.2); two mods recommending each other is not a defect, and an edge would manufacture a cycle |
| `breaks` | **never** | `breaks` means "must not coexist", the opposite of a dependency; turning it into an edge fabricates a reversed edge and invents cycles |
| `ordering` (`BEFORE` / `AFTER`) | not used | M1 emits no order (§3.6.5); an ordering hint must not be smuggled into an edge |

**Identity is exactly §3.5.5 rule 1, scoped to the universe: a real `modId` is authoritative.** Rules 2–5 never
produce an edge:

| Bucket (exactly one per hard declaration) | Condition | Edge |
|---|---|---|
| `EDGE` | `targetId` is a node of the same universe | yes |
| `ENVIRONMENT` | `targetId` is an environment-provided ID (§3.5.2) | no — environment IDs are not mods and never become nodes |
| `OTHER_UNIVERSE` | `targetId` is a node, but only in another universe | no |
| `ALIAS_ONLY` | `targetId` is not a node but is declared in some `provides` of this universe | no — an alias is a fallback identity, never an edge; this also covers every `AMBIGUOUS_ALIAS` declaration, and M1 must not pick an owner either |
| `ABSENT` | none of the above | no |

`EDGE + ENVIRONMENT + OTHER_UNIVERSE + ALIAS_ONLY + ABSENT == number of hard declarations` must be asserted at
runtime, in the same spirit as `OK + FAILED == input count`. A declaration that fits no bucket is a program defect
and must fail the run, never be skipped, never be counted as an edge.

**`EDGE` counts declarations; the graph counts distinct edges.** A node with several records (or one record declaring
the same target twice) can produce the same `(source node, target node)` pair more than once. The bucket equation
works on declarations; the edge count of §3.6.6 works on distinct pairs. Both are needed, they answer different
questions, and **neither may ever exceed the number of `EDGE` declarations**.

> Current dataset, measured by `verify_m1_counts.py` from `candidates.jsonl` alone: 98 `FABRIC` nodes, 89 `NEOFORGE`
> nodes, 0 `FORGE` nodes; 531 hard declarations split into `EDGE` 89 (`FABRIC` 69, `NEOFORGE` 20),
> `ENVIRONMENT` 374, `OTHER_UNIVERSE` 0, `ALIAS_ONLY` 18, `ABSENT` 50, sum 531. The `FABRIC` node `continuity`
> holds two records that both declare `fabric-api`, so its 69 `EDGE` declarations collapse to 68 distinct edges and
> the graph carries 68 + 20 = 88 edges. The step-17 preview figure of 85 `FABRIC` edges was 68 distinct edges plus
> 17 alias-to-owner pairs, i.e. it turned `ALIAS_ONLY` declarations into edges — the bucket table above forbids
> exactly that.

> **Known consequence, stated rather than hidden**: an alias-only dependency (`fabric`, owned by `fabric-api`) is not
> modelled. The error direction is under-reporting — a cycle reachable only through an alias would be missed, never
> invented. §3.5.5 already refuses to treat an alias as an authoritative identity, and M1 keeps that refusal.

### 3.6.4 Cycle detection and canonical representation

**Acyclicity** is decided by topological sort (Kahn): repeatedly remove nodes with in-degree 0; the graph is acyclic
if and only if every node is removed. **The removal sequence is never output or logged** (§3.6.5).

**Cycle groups** are the strongly connected components: an SCC with 2 or more nodes, or a single node with an edge to
itself, is one cycle group. Every member of a cycle group lies on at least one directed cycle.

**Consistency invariant**: the topological sort completes ⇔ the number of cycle groups is 0. If the two disagree,
that is a program defect and must fail the run.

**Canonical representation** — the same input must produce a byte-identical `cycles.csv` on every run:

- a cycle group is written as its **member set**, never as a path
- `members` are sorted **ascending by ASCII code point** of the `modId`, so the first member is always the
  lexicographically smallest one — there is no start point and no direction left to normalise
- rows are sorted by `universe` (ASCII ascending: `FABRIC` < `FORGE` < `NEOFORGE`), then by the `members` string
  (ASCII ascending)
- no other column may be added to a row

> **Why a member set instead of a cycle path.** "Start at the smallest member and walk forward" is only canonical when
> the group holds exactly one elementary cycle. An SCC may hold many, and picking one needs an extra arbitrary rule —
> exactly the run-to-run incomparability this section exists to prevent. Enumerating every elementary cycle is
> exponential and its ordering is not canonical either. The member set is the only invariant representation, and it
> answers the question being asked: *which mods are in a cycle*.
>
> One row therefore does **not** claim that a single cycle contains all of its members. It claims that every member
> lies on some cycle.

### 3.6.5 Hard prohibition: no install order, no load order, no topological order

M1 computes a topological sort for exactly one purpose: to decide whether the graph is acyclic. Its result must never
leave the computation.

Forbidden everywhere — in `cycles.csv`, in stdout, at any log level including `DEBUG` and `TRACE`, in any field of any
report object, and in any documentation generated from a run:

- a sequence of modIds presented as an install, load, resolve or "do it in this order" order
- a topological order, a partial topological order, or a prefix of one
- a "nodes with in-degree 0 first" list, a layering, or a dependency depth
- any wording suggesting mods should be installed or loaded in a particular sequence

**A violation invalidates the delivery.**

> Rationale: the moment an order is published, the tool implicitly promises "install it in this order and it will
> work" — a guarantee this project cannot make (§0). A cycle report says only "these mods cannot all be satisfied in
> this collection"; it says nothing about sequencing. If a user asks for an install order, the answer is no, and the
> reason is this section.

### 3.6.6 Output

New file **`cycles.csv`**, header included, no placeholder rows (the current dataset has 0 cycle groups, so the
produced file contains the header only — that is a real measurement, not a placeholder):

```
universe,memberCount,members
FABRIC,2,"sodium;someaddon"
```

- one row per cycle group; `members` is joined with `;`, the same convention as `conflicts.csv`
- with no cycle group the file contains the header only
- it repeats no data from `dependencies.csv`, and `dependencies.csv` keeps its M0.5 content byte for byte
- `dependencies.csv` and `cycles.csv` answer different questions with different scopes and must never be
  cross-summed: `AMBIGUOUS_ALIAS` is decided over the whole dataset (§3.5.5), while `ALIAS_ONLY` is decided inside one
  universe. Both can be true of the same declaration at the same time, and neither contradicts the other.

stdout (INFO), placed after §3.5.6's three lines and before the fixed closing line of §6.6:

```
[INFO]  依赖图：FABRIC 节点 N，边 N | FORGE 节点 N，边 N | NEOFORGE 节点 N，边 N | 未归入宇宙的 OK 记录 N
[INFO]  未进图的硬依赖：环境提供 N | 跨加载器宇宙 N | 仅别名 N | 目标不存在 N
[INFO]  依赖环：N 组
```

All three universes are always printed, including when empty, so that "0" can never be confused with "not evaluated".
`边` is the number of distinct `(source node, target node)` pairs in that universe (§3.6.3), **not** the `EDGE`
bucket count; the two differ whenever a node's records declare the same target more than once.
The bucket counts are sums over all universes; the per-universe equation of §3.6.3 must still be asserted.

One **ERROR** per cycle group, format `依赖环 {}：{}` — the universe, then the members joined by ` | `. Zero cycle
groups produces no ERROR line.

> The block above is a **format template**, not real figures, exactly as stated in §6.6.

### 3.6.7 M1 acceptance criteria

These ten items gate **M1** only. They are independent of §7 (M0, ten items) and §3.5.7 (M0.5, eight items); the three
sets must not be mixed, and passing one never implies passing another.

1. **Loader universes**: a constructed pair where `A` (`FABRIC`) depends on `B` and `B` (`NEOFORGE`) depends on `A`
   produces **0** cycle groups and two nodes, one per universe. This is the primary regression guard against
   cross-loader fake cycles.
2. **Node identity**: two `status=OK` records with the same `modId` in the same universe produce **one** node with
   their edges unioned; the same `modId` in two universes produces **two** nodes.
3. **Declaration kinds**: `recommends` and `breaks` never create an edge. A constructed set where `A` recommends `B`
   and `B` depends on `A` has 0 cycle groups; the same holds when `A` breaks `B`.
4. **No silent drop**: for every universe, `EDGE + ENVIRONMENT + OTHER_UNIVERSE + ALIAS_ONLY + ABSENT == hard
   declarations`, asserted at runtime and again in a test; the four no-edge buckets are printed, never folded into a
   single number.
5. **Cycle detection**: constructed sets for a 2-cycle, a 3-cycle, a self-loop (`A` depends on `A`) and a DAG are each
   classified correctly; none of them may be skipped.
6. **Consistency**: "the topological sort completes" and "0 cycle groups" always agree, in both directions, asserted
   on the real dataset and on the constructed sets.
7. **Canonical output**: `members` is exactly the ASCII-ascending join of the group, rows are sorted as §3.6.4
   requires, and two consecutive runs on the same input produce a byte-identical `cycles.csv`.
8. **No ordering anywhere**: the report exposes no ordered node sequence; `cycles.csv` has exactly the three columns of
   §3.6.6; no stdout or log line contains an install, load or topological order. Reviewing the produced files and the
   log is part of acceptance, not a formality.
9. **M0 and M0.5 untouched**: the SHA-256 of `candidates.jsonl`, `summary.csv`, `conflicts.csv` and `dependencies.csv`
   must be identical before and after M1, and the baselines of §7 and §3.5.7 (195 / 191 / 4, HIGH 331, 6670 pairs,
   coverage gap 10/191, and the six outcome counts 612 / 72 / 7 / 7 / 18 / 0) must be unchanged. Both are recorded in
   `PROGRESS.md`.
10. **Build and logging**: `gradle clean test` passes with **0 skipped** and without `assumeTrue` / `assumeFalse`;
    every cycle group emits exactly one ERROR in the format of §3.6.6 and zero groups emit none; every number in the
    summary comes from the run and no jar count is hard-coded.

---

## 4. Failure classification (`failureCode`)

| code | Meaning | Handling |
|---|---|---|
| `NOT_A_ZIP` | file is not a valid zip | record FAILED for this jar, continue with the rest |
| `NO_METADATA` | no known metadata file found in the zip | FAILED, continue |
| `MALFORMED_JSON` | JSON syntax error | FAILED, continue |
| `MALFORMED_TOML` | TOML syntax error | FAILED, continue |
| `MISSING_REQUIRED_FIELD` | missing required field such as `id` / `modId` / `version` | FAILED, continue |
| `IO_ERROR` | read exception | FAILED, continue |
| `INTERNAL_ERROR` | unexpected program-level exception (not a data problem) | FAILED, continue; message must state this is a program exception, not a data problem, and carry the exception class name |

**Key constraint**: a single jar failing **does not affect the other jars**, but it **must be counted in the
final failure count**.
There is never a case of "200 inputs, 187 output records" without explanation.
The report must always satisfy: `OK count + FAILED count == number of input files`.

---

## 5. Log level rules (mandatory)

SLF4J facade with a Log4j2 implementation. Levels from high to low:
`ERROR` / `WARN` / `INFO` / `DEBUG` / `TRACE`.

| Level | When to use | Example |
|---|---|---|
| `ERROR` | single jar parse failure; unsatisfiable hard dependency in the graph; a cycle exists | `解析失败 {}: {}` |
| `WARN` | result usable but possibly incomplete: unknown field, multi-mod jar, missing soft dependency, deep-invasion hit, excluded mod | `侵入深度 {} 于 {}，信号：{}` |
| `INFO` | stage-level summary, at most one line per stage | `扫描 {} 个 jar：OK {}，FAILED {}` |
| `DEBUG` | per-jar parsing progress | `解析 {} → modId={}` |
| `TRACE` | field-level detail, off by default | `读取 depends 条目 {}` |

### Hard rules (violation means rejection)

1. **Never swallow an exception into WARN or silent continuation with `try/catch`.** Catching an exception
   must: fill `failureCode` + `failureMessage` → log at `ERROR` → mark the record FAILED.
   "Catch and do nothing" is not allowed.
2. **Never demote ERROR to WARN just so the process finishes.** The process continues because
   "the other jars are independent", not because "this error is unimportant".
3. **No `printStackTrace()` and no `System.out.println()`.** Everything goes through SLF4J.
4. **Logging must be parameterised**: `log.warn("侵入深度 {} 于 {}", level, file)`; string concatenation is
   forbidden.
5. **Never log complete jar binaries or large blocks of raw metadata.**
6. **Default output level is `INFO`**; `DEBUG` is enabled via the JVM flag `-Dlog.level=debug`.
7. **Invasiveness hits on RENDER / CORE and EXCLUDED decisions must be emitted at WARN, never demoted.**
8. A single jar's failure produces **at most one ERROR**; retries must not spam the log.

---

## 6. Output formats

### 6.1 `candidates.jsonl`

One JSON object per line, one per input jar, **in input order**.

### 6.2 `summary.csv`

Header: `sourceFile,sha256,loaderFamily,status,modId,version,modType,invasiveness,supportPolicy,failureCode`

### 6.3 `conflicts.csv`

Header: `modA,modB,level,sharedTargets`; rules in §2.4.

### 6.4 `dependencies.csv` (M0.5)

Header: `modId,version,depId,depKind,declaredRange,resolvedVersion,outcome`; rules in §3.5.
One row per declared dependency, no deduplication.

### 6.5 `cycles.csv` (M1)

Header: `universe,memberCount,members`; rules in §3.6.
One row per cycle group, header only when there is none.

### 6.6 stdout (INFO level)

> The block below is a **format template**, not real figures; the real figures are in the README's actual run
> output.

```
[INFO]  输入 jar：200
[INFO]  解析成功：193 (96.5%)
[INFO]  解析失败：7
[INFO]    NOT_A_ZIP: 2 | NO_METADATA: 3 | MALFORMED_JSON: 1 | IO_ERROR: 1
[INFO]  FABRIC: 98 | NEOFORGE: 95 | UNKNOWN: 0
[INFO]  侵入深度：API 41 | MIXIN 131 | CORE 12 | RENDER 16
[INFO]  支持策略：SUPPORTED 172 | DEGRADED 28 | EXCLUDED 0
[INFO]  冲突预测：HIGH 3 组 | MEDIUM 7 组 | LOW 12 组（静态推断，非运行结论）
[INFO]  依赖总数：硬依赖 N | 软依赖 N | 冲突声明 N
[INFO]  不可满足：MISSING_ID N | VERSION_MISMATCH N | BREAKS_VIOLATED N | AMBIGUOUS_ALIAS N
[INFO]  无法解析的版本区间：N（实现缺陷，不是模组问题）
[INFO]  依赖图：FABRIC 节点 N，边 N | FORGE 节点 N，边 N | NEOFORGE 节点 N，边 N | 未归入宇宙的 OK 记录 N
[INFO]  未进图的硬依赖：环境提供 N | 跨加载器宇宙 N | 仅别名 N | 目标不存在 N
[INFO]  依赖环：N 组
```

> Every number must come from an actual run; the file must not hard-code any quantity.

Finally, one fixed line must be emitted:

```
[INFO]  本结果为准入检查，不构成运行稳定性保证
```

**Acceptance invariant (must be asserted explicitly)**: `success count + failure count == input count`.
If this assertion fails, the build is a failure.

---

## 7. Acceptance criteria (M0 only)

These ten items gate **M0**. M0.5 has its own gate in §3.5.7 and M1 its own in §3.6.7; the three sets must not be
mixed, and passing one never implies passing another.

1. **All** real jars under `fixtures/jars/` produce records, none omitted. **The count must be obtained by
   actually enumerating the filesystem; this file must not hard-code any number** (a previous version wrongly
   said "200"; the actual dataset is 195).
2. Manually sample 10 jars, unpack and check that `modId` / `version` / `depends` match the original metadata
   100%.
3. `OK + FAILED == input count` always holds, asserted explicitly in tests.
4. Unit tests cover all **7** `failureCode`s (`NO_METADATA`, `MALFORMED_JSON`, `MALFORMED_TOML`,
   `MISSING_REQUIRED_FIELD`, `IO_ERROR`, `NOT_A_ZIP`, `INTERNAL_ERROR`), and **each test must first construct a
   fixture jar that genuinely triggers that failure and assert the failure**. Mocking away real file reads is
   forbidden. `INTERNAL_ERROR` cannot be triggered by data; verifying that its fallback path exists is allowed,
   but `PROGRESS.md` must state that it was not triggered by real data.
5. Invasiveness rating: manually sample 10 mods of known type (including at least 1 render mod and 1 library)
   and verify that `invasiveness` and `modType` are judged correctly.
6. No `System.out.println`, no `printStackTrace`, no empty catch block — enforced at build time by a static
   check (Checkstyle or ArchUnit).
7. Mixin target index: manually sample 5 mods containing mixins, unpack and verify that `mixinTargets` matches
   the class names in their mixin config files; `mixinTargetsInferred` must be `true`, and the summary must
   contain the phrase "静态推断，非运行结论". **No name containing `Mixin` may remain in the target set.**
8. An unreadable or non-zip input **must not terminate with an exception**; it must produce a `status=FAILED`
   record and be counted in the failure total (see the key constraint in §4).
9. Conflict prediction signal-to-noise: the summary must print the top 10 high-frequency target classes; the
   `HIGH` count must not exceed **5%** of all pairs, and **must be lower** than the baseline without the rarity
   rule (649/6670 = 9.7%). If HIGH equals the baseline, the rarity rule is judged not to have taken effect and
   the threshold and target inference must be re-examined (in particular whether prefix-style names were missed).
10. Target inference coverage: the number of records with non-empty `mixinConfigs` but empty `mixinTargets` must
    not exceed **10%** of the OK total. With suffix-only stripping the gap was 54/191 (28%); adding prefix
    stripping should reduce it significantly.

---

## 8. Tech stack (must not be substituted)

- Java 21
- Gradle 8.x (Kotlin DSL or Groovy, either is fine)
- SLF4J 2.x + Log4j2 2.x
- JSON: `com.google.code.gson:gson`
- TOML: `com.electronwill.night-config:toml`
- Tests: JUnit 5 + AssertJ
- **Forbidden** to introduce dependencies related to Minecraft, Fabric Loader, Forge, NeoForge, Mixin or ASM

> L1 invasiveness scanning only needs zip entry names and metadata text — no bytecode library at all.
> Any class file parsing (L2) is forbidden at this stage.
