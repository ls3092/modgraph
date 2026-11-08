# Mixin coverage cross-check: naming convention vs modcrawl

- generated: 2026-11-07T20:02:24.640274+08:00
- modcrawl: `modcrawl 0.11.3`
- modcrawl sha256: `a1f65355951f81753a10b8d4b9b61aa9eef2b65a761ad1e1569e8007509caf56`
- modcrawl LICENSE first line: `MIT License`
- command: `C:\Users\Administrator\AppData\Local\Temp\opencode\modcrawl-bin\modcrawl.exe mixins --json <jar>`
- filesystem JARs: **195**; candidates.jsonl records: **195**; OK: **191**; FAILED: **4**

## 191 OK records: four-way comparison

| bucket | records | unique modIds |
| --- | ---: | ---: |
| BOTH | 157 | 100 |
| NAMING_ONLY | 1 | 1 |
| MODCRAWL_ONLY | 6 | 5 |
| NEITHER | 27 | 21 |
| **sum** | **191** | |

Per-bucket unique modIds may overlap: a modId with both Fabric and NeoForge variants can land in two buckets (for example `sodium`).

## Denominator check

- OK records with empty naming targets: **33** = gap (non-empty `mixinConfigs`) **10** + empty `mixinConfigs` **23**
- NEITHER **27** = gap-remaining **5** + empty-config records with no modcrawl hit **22**
- MODCRAWL_ONLY **6** = gap-filled **5** + empty-config records where modcrawl still found `@Mixin` **1**

### NAMING_ONLY (naming hit, modcrawl hit=0)

sodium (NEOFORGE)

### MODCRAWL_ONLY (naming hit=0, modcrawl hit)

controlling (NEOFORGE); embeddium (NEOFORGE); modelfix (NEOFORGE); presencefootsteps (FABRIC); searchables (FABRIC, NEOFORGE)

## The gap set (OK, empty mixinTargets, non-empty mixinConfigs)

- records: **10**; filled by modcrawl: **5**; remaining: **5**
- unique modIds: 8 (filled 4, remaining 4)

| modId | loader | jar | modcrawl entries | status |
| --- | --- | --- | ---: | --- |
| coroutil | FABRIC | coroutil-fabric-1.21.1-1.3.8.jar | 0 | remaining |
| presencefootsteps | FABRIC | PresenceFootsteps-1.11.2+1.21.jar | 9 | filled |
| searchables | FABRIC | Searchables-fabric-1.21.1-1.0.2.jar | 1 | filled |
| supermartijn642configlib | FABRIC | supermartijn642configlib-1.1.8-fabric-mc1.21.jar | 0 | remaining |
| visualworkbench | FABRIC | VisualWorkbench-v21.1.2-1.21.1-Fabric.jar | 0 | remaining |
| controlling | NEOFORGE | Controlling-neoforge-1.21.1-19.0.5.jar | 5 | filled |
| embeddium | NEOFORGE | embeddium-1.0.15+mc1.21.1.jar | 91 | filled |
| forgeconfigapiport | NEOFORGE | ForgeConfigAPIPort-v21.1.6-1.21.1-NeoForge.jar | 0 | remaining |
| searchables | NEOFORGE | Searchables-neoforge-1.21.1-1.0.2.jar | 1 | filled |
| visualworkbench | NEOFORGE | VisualWorkbench-v21.1.2-1.21.1-NeoForge.jar | 0 | remaining |

### Gap top-level partition

| bucket | records |
| --- | ---: |
| FILLED_BY_MODCRAWL | 5 |
| EMPTY_CONFIG | 5 |
| CONFIG_MISSING | 0 |
| MALFORMED_CONFIG | 0 |
| NESTED_JAR_ONLY | 0 |
| CONFIG_REFERENCES_MISSING_CLASS | 0 |
| CONFIG_CLASS_PRESENT_MODCRAWL_NO_TARGET | 0 |
| UNCLASSIFIED | 0 |
| **sum** | **10** |

## Remaining reason buckets

| bucket | records |
| --- | ---: |
| EMPTY_CONFIG | 5 |
| CONFIG_MISSING | 0 |
| MALFORMED_CONFIG | 0 |
| NESTED_JAR_ONLY | 0 |
| CONFIG_REFERENCES_MISSING_CLASS | 0 |
| CONFIG_CLASS_PRESENT_MODCRAWL_NO_TARGET | 0 |
| UNCLASSIFIED | 0 |
| **sum** | **5** |

## Assertions

- jarsEqualsCandidates: PASS
- statusSumEqualsJars: PASS
- fourWaySumEqualsOk: PASS
- gapFilledPlusRemainingEqualsTotal: PASS
- remainingReasonSumEqualsRemaining: PASS
- gapBucketSumEqualsTotal: PASS
- emptyNamingEqualsGapPlusEmptyConfig: PASS
- neitherBreaksDown: PASS
- modcrawlOnlyBreaksDown: PASS
- noUnclassifiedRemaining: PASS

Note: `mixinTargets` is produced by naming-convention inference; modcrawl reads class-level `@Mixin` annotations. A non-empty list from either side is a detection, not proof that the target names agree.
