# Implementation prompt — rename `Faction.A`/`Faction.B` to `Faction.CRIMSON`/`Faction.SKYBORN`

Prerequisite for the CharacterCreationScreen wizard redesign (prompt 39) — kept separate because this part is a mechanical, repo-wide rename touching the database, not a UI change, and isolating it keeps the risky part small and easy to verify on its own.

Grounded in the actual codebase, not just the design docs: `SkillTreeCatalog.java` already treats `Faction.A`/`Faction.B` as "Crimson"/"Skyborn" in practice — its faction-locked skill nodes are literally named `"faction.crimson.n1"` ("Crimson Fury", crit) and `"faction.skyborn.n1"` ("Skyborn Grace", dodge), and its own javadoc states the mapping outright: "Faction mapping: `Faction#A` → Crimson (crit), `Faction#B` → Skyborn (dodge)." This rename finishes something the rest of the codebase already assumes, not something new. (Separately, the *player-facing display name* for the Crimson-coded faction is "The Wardens," not "Crimson" anything — see `03-lore-and-worldbuilding.md`'s 2026-07-27 naming note. That's prompt 39's concern; this prompt only touches the enum constant names and color-coded content, which stay `CRIMSON`/`SKYBORN`.)

## 1. Rename the enum

`common/src/main/java/io/github/ydhekim/crimson_sky/common/model/Faction.java`:

```java
public enum Faction {
    CRIMSON, SKYBORN
}
```

Kryo wire compatibility is safe by construction here: Kryo's default enum serialization is ordinal-based, and this rename preserves declaration order exactly (`CRIMSON` takes `A`'s old ordinal 0, `SKYBORN` takes `B`'s old ordinal 1) — no `KryoConfig` change needed, no client/server version skew risk.

## 2. Database: convert existing rows, update the CHECK constraint

`characters.faction` has `CHECK (faction IN ('A', 'B'))` from `V1__Initial_Schema.sql:67` — an inline, unnamed constraint, so Postgres auto-generated its name as `characters_faction_check` (the standard `{table}_{column}_check` pattern for an unnamed single-column CHECK). **Verify this against the actual dev database before running** (`\d characters` in `psql`, or `SELECT conname FROM pg_constraint WHERE conrelid = 'characters'::regclass;`) in case it was ever named explicitly — if the query shows a different name, use that instead.

New migration, `server/src/main/resources/db/migration/V28__Rename_Faction_Values.sql`:

```sql
UPDATE characters SET faction = 'CRIMSON' WHERE faction = 'A';
UPDATE characters SET faction = 'SKYBORN' WHERE faction = 'B';

ALTER TABLE characters DROP CONSTRAINT characters_faction_check;
ALTER TABLE characters ADD CONSTRAINT characters_faction_check CHECK (faction IN ('CRIMSON', 'SKYBORN'));
```

(Data conversion first, constraint swap second — the old constraint would reject the new values if the ALTER ran before the UPDATEs.)

## 3. Sweep every remaining `Faction.A`/`Faction.B` reference

A repo-wide search (`grep -rn "Faction\.A\b\|Faction\.B\b"`) found these as the only non-historical-doc references — every one is a mechanical `Faction.A` → `Faction.CRIMSON`, `Faction.B` → `Faction.SKYBORN` swap, no logic change:

- `server/src/main/java/io/github/ydhekim/crimson_sky/server/content/SkillTreeCatalog.java` — also update the javadoc comment referencing `{@link Faction#A}`/`{@link Faction#B}` to `{@link Faction#CRIMSON}`/`{@link Faction#SKYBORN}`. Leave the node keys (`"faction.crimson.n1"`, `"faction.skyborn.n1"`) and skill names ("Crimson Fury", "Skyborn Grace") exactly as they are — already correct.
- `server/src/main/java/io/github/ydhekim/crimson_sky/server/combat/BotFactory.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/service/CharacterServiceCreateCharacterTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/service/CharacterServiceSaveLoadoutWeightTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/network/handler/SaveLoadoutRequestHandlerTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/service/ShopServiceTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/support/CombatFixtures.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/service/CharacterServiceAllocateStatPointsTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/network/handler/AllocateStatPointsRequestHandlerTest.java`
- `server/src/test/java/io/github/ydhekim/crimson_sky/server/service/SkillTreeServiceTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleParticipantPassivesTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleEnginePotionTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleParticipantConsumablesTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleParticipantPetHealthTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleEngineTest.java`
- `core/src/test/java/io/github/ydhekim/crimson_sky/combat/BattleParticipantDurabilityTest.java`
- `core/src/main/java/io/github/ydhekim/crimson_sky/screen/CharacterCreationScreen.java` — **compile-safety only**, not a design change: swap its two `Faction.A`/`Faction.B` literals to `Faction.CRIMSON`/`Faction.SKYBORN` so the build stays green. This entire file gets rewritten by prompt 39 immediately after — don't spend effort on anything beyond the literal swap here.

**Do not touch** `docs/planning/02-user-stories.md` or `docs/planning/10-implementation-prompt-m1-m3-skill-tree.md` — both are historical records of what was actually asked/built at the time, not living specs; retroactively editing them would misrepresent the project history.

## 4. Testing / Definition of Done

1. `gradlew.bat build` — confirm everything compiles with zero remaining `Faction.A`/`Faction.B` references outside the two historical docs.
2. `gradlew.bat test` — confirm every test file touched above still passes (pure rename, no behavior change expected).
3. Run `V28` against the dev DB, confirm existing character rows now read `CRIMSON`/`SKYBORN` and the CHECK constraint accepts only those two values (`INSERT ... faction = 'A'` should now fail).
4. `gradlew.bat server:run` + `gradlew.bat lwjgl3:run`, log in with the existing test account, reach Characters — confirm the existing "test" character (previously faction `A`) still loads without error (proves the client correctly deserializes the renamed enum value from a converted row).

Definition of done: `Faction` has exactly two values, `CRIMSON`/`SKYBORN`; every code reference (production and test) uses the new names; existing DB rows are converted; the build and full test suite are green.
