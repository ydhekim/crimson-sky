# Starter Content — Weapons, Skills, Pets

Last updated: 2026-07-09

Not the Epic E content-authoring pass (that's M5, data-driven, DB/JSON-backed). This is a small, deliberately curated set — enough to give Claude Code concrete fixtures for A6/A7's unit tests and E2's physical-vs-magical balance validation, instead of testing against a placeholder "Hammer" with numbers that never reconcile with real content. All numbers are first-pass, derived directly from the formulas/heuristics in `01-system-design-combat-engine.md` §4.2-§4.4 — expect a tuning pass once Monte Carlo simulation is run, same caveat as everything upstream of this doc.

## Weapons (physical path — STR/DEX)

| Name | `minAttack`/`maxAttack` | `weight` | `staminaCost` | Rarity | Archetype |
|---|---|---|---|---|---|
| Twin Daggers | 8 / 18 (mid 13) | 2 | 8 | COMMON | Spammable, low-commitment — fits a DEX/SPD dodge-and-poke build. Weight low enough it never triggers the STR penalty regardless of build. |
| Steel Longsword | 12 / 28 (mid 20) | 15 | 15 | UNCOMMON | Generalist baseline — the reference weapon the skills below are calibrated against. Needs STR ≳ 30 to avoid any penalty. |
| Warhammer | 15 / 45 (mid 30) | 40 | 25 | RARE | Wide, swingy range and high Stamina cost — a dedicated STR-stacker's weapon (needs STR ≳ 80 to wield penalty-free), rewards a tank/bruiser build specifically. |

## Skills (magical path — INT/WIS), one per Difficulty tier

Midpoints derived directly from the §4.3 compensation heuristic (EASY ≈1.5x, MEDIUM ≈2-2.5x, HARD ≈3x, MYTHIC ≈4x+ a comparable weapon's midpoint), using the Longsword (mid 20) as the reference point — so these are traceable to the heuristic, not picked freehand:

| Name | Difficulty | `minAttack`/`maxAttack` | `manaCost` | Archetype |
|---|---|---|---|---|
| Spark | EASY | 20 / 40 (mid 30, ≈1.5x) | 12 | Cheap, reliable — a WIS/spam-oriented build's bread and butter. |
| Lightning Bolt | MEDIUM | 30 / 60 (mid 45, ≈2.25x) | 28 | The mid-tier magical workhorse — closest analog to the Longsword. |
| Fireball | HARD | 45 / 75 (mid 60, ≈3x) | 45 | Big swing, real mana commitment — punishes a shallow mana pool. |
| Meteor | MYTHIC | 70 / 110 (mid 90, ≈4.5x) | 70 | Build-defining nuke — only a deep-SPI/high-maxMp build can cast this more than once or twice a match. |

## Pets, one per Tameness tier

Names reuse the GDD's own worked-example pets (Wolf, Bear) where the tier matches, for continuity with the source document.

| Name | Tameness | `minAttack`/`maxAttack` | `healthPoint` | `defence` | Archetype |
|---|---|---|---|---|---|
| Sparrow | WILD (−10 INS) | 5 / 15 (mid 10) | 20 | 2 | Unreliable but free-feeling — low investment, low payoff, high variance from the Tameness penalty alone. |
| Hound | STUBBORN (+0) | 10 / 20 (mid 15) | 35 | 5 | Neutral baseline. |
| Wolf | TRACEABLE (+10) | 15 / 25 (mid 20) | 50 | 8 | GDD Scenario 1's pet — solid, reliable mid-tier. |
| Bear | LOYAL (+20) | 20 / 36 (mid 28) | 80 | 15 | GDD Scenario 3's pet — the most reliable and tankiest, rewards a dedicated INS/pet-carry build. |

**Open item, not solved here:** `Pet.healthPoint`/`defence` aren't referenced by any formula in `01-system-design-combat-engine.md` — nothing currently lets a pet be damaged or defeated independently of its owner. They may be vestigial fields or intended for a not-yet-designed "pet can be knocked out separately" mechanic. Flagging so it isn't assumed resolved; doesn't block M2 since pets currently only ever contribute a bonus action to the Result Set, never take one directed at them.

## Why this set, specifically

Three weapons (spam/generalist/heavy), four skills (one per Difficulty, tracing the compensation heuristic), four pets (one per Tameness tier) is enough to stand up: a STR/DEX dodge-poke build, a tank/bruiser Warhammer build, a WIS-spam mage, a mana-hungry Meteor nuker, and pet-carry variants of any of the above — covering the tank/dps/dodge archetypes from your build-diversity goal, without authoring a full catalog before the engine that consumes it has even been built.

## Cross-references

- `01-system-design-combat-engine.md` §4.2-§4.4 — the formulas these numbers are derived from.
- `02-user-stories.md` A6/A7 — use this table as test fixtures instead of inventing placeholder numbers per-test.
- `02-user-stories.md` E1/E2 — this is a seed for that pass, not a replacement for it; Epic E's job is making content data-driven (DB/JSON) and expanding well past this starter set.
