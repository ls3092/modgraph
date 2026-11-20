# §0.x draft preservation check

`producedBy: draft_a`

## Scope

This checklist covers only:

- `forbric-comparison/spec-draft-0x.md`
- `forbric-comparison/spec-draft-0x.zh-CN.md`

Neither draft has been inserted into `SPEC.md` or `SPEC.zh-CN.md`. No implementation, jar, Minecraft process, Gradle task, or git write was used.

## Exact source preservation

| ID | Source | Line | Protected original | English draft | Chinese draft |
|---|---|---:|---|---|---|
| P1 | `AGENTS.md` | 17 | `- 不启动 Minecraft、不加载类、不解析 class 文件、不处理 Mixin、不改写字节码。` | exact | exact |
| P2 | `SPEC.md` | 15 | `M0 **does not start Minecraft, does not load classes, does not process mixins, does not rewrite bytecode**.` | exact | exact |
| P3 | `SPEC.md` | 19-20 | `M1 (§3.6) builds a dependency graph on top of those records and detects cycles. It does not start Minecraft` + newline + `either, does not load classes, does not read class files, and **does not output an install order** (§3.6.5).` | exact, same line break | exact, same line break |
| P4 | `AGENTS.md` | 18 | `- 不引入 Minecraft / Fabric Loader / Forge / NeoForge / Mixin / ASM 依赖。` | exact | exact |

“Exact” means the protected text after the blockquote marker is byte-for-byte equal to the source text. The source line number is metadata and is not part of the quoted line.

## Required semantic checks

| Check | Required state |
|---|---|
| Static and loader modes | Mutually exclusive; exactly one mode per run/report; switching creates new evidence |
| Static source protection | Four protected source groups unchanged in both drafts |
| Loader scope | Frozen cohort, controlled environment, jars outside repository and not redistributed |
| Prohibited output | No installation, load, topological, layering, or installation-order recommendation |
| Automatic actions | No automatic compatibility, repair, version choice, or candidate recommendation |
| Runtime-error accounting | Long-running runtime errors versus §5 parse-error rule remains pending decision |
| Minecraft launch | Separate approval required; this draft does not activate it |
| Accuracy denominator | `N_answered = CORRECT + INCORRECT`; all other states remain single buckets |
| 4-bucket decomposition | Sum equals `N_common_3` |
| 8-pattern decomposition | Sum equals `N_common_3` |
| Orthogonality | The 4-bucket and 8-pattern partitions are not added or mixed |

## Mechanical consistency results

| Assertion | Result |
|---|---|
| Number of Markdown headings is equal | Pass: English 6, Chinese 6 |
| Heading line-number vectors are equal | Pass: both `4,10,29,42,48,60` |
| Physical line counts are equal | Pass: English 63, Chinese 63 |
| Ordered numeric-token sequence is equal | Pass: both have 26 tokens with an identical ordered sequence |
| P1 occurs exactly once in each draft | Pass: English 1, Chinese 1; each equals `AGENTS.md:17` after the quote marker |
| P2 occurs exactly once in each draft | Pass: English 1, Chinese 1; each equals `SPEC.md:15` after the quote marker |
| P3 occurs exactly once in each draft with the same line break | Pass: each has one adjacent two-line occurrence; both physical lines equal `SPEC.md:19-20` |
| P4 occurs exactly once in each draft | Pass: English 1, Chinese 1; each equals `AGENTS.md:18` after the quote marker |
| No accuracy denominator other than `N_answered` is present | Pass: `N_eval` occurrences are 0 in both drafts; the sole accuracy formula is `accuracy = CORRECT / N_answered` |

## Handoff

### Files changed

- Added: `forbric-comparison/spec-draft-0x.md`
- Added: `forbric-comparison/spec-draft-0x.zh-CN.md`
- Added: `forbric-comparison/spec-draft-0x-preservation-check.md`
- Modified: none
- Deleted: none
- `SPEC.md`, `SPEC.zh-CN.md`, `src/`, existing artifacts, badges, README, BOUNDARIES, LICENSE, AGENTS.md, and PROGRESS.md were not modified.

### Test numbers

Not run. This is a documentation-only draft and no code changed. `gradle test` was not run.

### Key numbers

- English draft: 63 physical lines, 6 Markdown headings, 26 numeric tokens.
- Chinese draft: 63 physical lines, 6 Markdown headings, 26 numeric tokens.
- Protected-origin occurrences: P1 `1 + 1`, P2 `1 + 1`, P3 `1 + 1` two-line groups, P4 `1 + 1`; all exact.
- No runtime or corpus result was produced.

### Violations

One scoped-write / route-boundary violation occurred: a malformed shell invocation created a temporary repository-local `$null` file outside the three permitted output paths. The file contained only error text and was deleted immediately; there is no residual file, and no existing file was modified by that incident.

No Minecraft launch, class loading, class parsing, Mixin processing, bytecode rewrite, loader dependency, jar download, order output, automatic repair, git write, or implementation work occurred.
