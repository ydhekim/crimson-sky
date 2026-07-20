# Implementation prompt — Epic T1: Character customization

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §23, `docs/planning/02-user-stories.md` Epic T). This is the ninth and final slice of the 2026-07-10 a–k expansion, and the smallest — purely cosmetic, no stats, no combat interaction. It closes out the entire expansion once merged.

**Grounding first, since this touches an already-live screen, unlike every other epic in this expansion.** Name, faction, and stat-point distribution are already chosen in `CharacterCreationScreen` (core module) and sent via `CreateCharacterRequest(Character character)` — this screen is real, working UI, not a placeholder (M4's placeholder-rendering caveat is about the *combat* screen, not character creation). T1 extends this live screen with four more choices rather than deferring UI like every other epic here did.

**A deliberate scope decision, found while grounding:** `Character`/`CharacterEntity` are NOT where appearance lives. `Character` is constructed at 16 call sites across combat tests, `BotFactory`, and shop/loadout tests — none of which care about cosmetics, so adding a field there would force an irrelevant update at every one of them (the same reasoning that kept S4's `equippedTitle` off `Character` too). `CharacterEntity` is a much narrower, server-only projection with exactly one construction path (`CharacterEntity.fromCommonModel`, called only from `CharacterService.createCharacter`) — that's where `appearance` goes instead. `CreateCharacterRequest` gains `appearance` as a **second, sibling field**, not a component of the `character` it already carries.

## 1. Migration V17

`V17__Add_Character_Appearance.sql` (V15 is S1-S2's, V16 is S3-S4's):

```sql
-- Purely cosmetic (system design §23) — no stats, no combat interaction, not read by anything yet (M4 is
-- still placeholder-rendering the combat screen; M5's real art pipeline is what eventually consumes this).
-- A JSONB blob, not real columns, for the same reason skill_tree (§16) is one: avoids inventing atlas/sprite
-- IDs before real art exists to back them.
ALTER TABLE characters ADD COLUMN appearance JSONB NOT NULL DEFAULT '{}'::jsonb;
```

## 2. `common.model.Appearance` — new file

The curated v1.0 set lives here, as public constants, for the same reason `Stats.MAX_STAT_VALUE` is a shared constant the creation screen and server both read (its own comment says so explicitly) rather than two copies that can drift: one set of allowed values, read by the client's button UI and the server's validation alike.

```java
package io.github.ydhekim.crimson_sky.common.model;

import java.util.List;

/**
 * A character's purely cosmetic appearance (system design §23) — gender, hair type/color, skin tone. No
 * stat, no combat interaction, not consumed by rendering yet (M4 is still placeholder-rendering; M5's real
 * art pipeline is what eventually reads this). Set once at character creation; no edit endpoint in v1.0.
 *
 * <p>v1.0 ships a small curated set, not open free text (same "curated now, data-driven once real content
 * exists" precedent as starter weapons/skills/pets and the achievement seed data, §22) — the four constant
 * lists below are the single source of truth for what's allowed, read by both
 * {@code CharacterCreationScreen}'s selection buttons and {@link #isValid()}'s server-side check, so the two
 * can never drift apart.
 */
public record Appearance(String gender, String hairType, String hairColor, String skinColor) {

    public static final List<String> GENDERS = List.of("MALE", "FEMALE");
    public static final List<String> HAIR_TYPES = List.of("SHORT", "LONG", "BALD");
    public static final List<String> HAIR_COLORS = List.of("BLACK", "BROWN", "BLONDE", "RED");
    public static final List<String> SKIN_COLORS = List.of("PALE", "LIGHT", "TAN", "DARK");

    /** True only when every component is a member of its own curated list (system design §23). */
    public boolean isValid() {
        return gender != null && GENDERS.contains(gender)
            && hairType != null && HAIR_TYPES.contains(hairType)
            && hairColor != null && HAIR_COLORS.contains(hairColor)
            && skinColor != null && SKIN_COLORS.contains(skinColor);
    }
}
```

## 3. `CreateCharacterRequest` — one new field

```java
package io.github.ydhekim.crimson_sky.common.network.packet;

import io.github.ydhekim.crimson_sky.common.model.Appearance;
import io.github.ydhekim.crimson_sky.common.model.Character;

public record CreateCharacterRequest(Character character, Appearance appearance) {
}
```

`CreateCharacterResponse` is unchanged — it echoes back `Character`, which still doesn't carry appearance (nothing reads it yet, same as `equippedTitle` not riding the general character read path).

## 4. `MessageCode` — one addition

```java
// Character customization (system design §23)
CHAR_INVALID_APPEARANCE
```

## 5. `CharacterEntity` — one new field, `fromCommonModel` gains a parameter

```java
public record CharacterEntity(
    long id,
    long accountId,
    String name,
    Faction faction,
    int level,
    long experience,
    int maxHp,
    int maxMp,
    int maxStamina,
    int baseDef,
    int baseAtk,
    @Json Stats stats,
    @Json Inventory inventory,
    @Json Loadout loadout,
    @Json Map<String, Integer> skillTree,
    @Json Appearance appearance) {

    public Character toCommonModel() {
        // Unchanged — appearance deliberately does not ride the shared Character model (see context above).
        return new Character(id, accountId, name, faction, level, experience, maxHp, maxMp, maxStamina, baseDef, baseAtk,
            stats, inventory, loadout, skillTree != null ? skillTree : new HashMap<>());
    }

    public static CharacterEntity fromCommonModel(long accountId, Character c, Appearance appearance) {
        return new CharacterEntity(
            c.id(), accountId, c.name(), c.faction(), c.level(), c.experience(),
            c.maxHp(), c.maxMp(), c.maxStamina(), c.baseDef(), c.baseAtk(),
            c.stats(), c.inventory(), c.loadout(),
            c.skillTree() != null ? c.skillTree() : new HashMap<>(),
            appearance
        );
    }
}
```

## 6. `CharacterDao.createCharacter` — one column added

```java
@SqlUpdate("INSERT INTO characters (account_id, name, faction, level, experience, max_hp, max_mp, max_stamina, base_def, base_atk, stats, inventory, loadout, skill_tree, appearance) " +
    "VALUES (:c.accountId, :c.name, :c.faction, :c.level, :c.experience, :c.maxHp, :c.maxMp, :c.maxStamina, :c.baseDef, :c.baseAtk, :c.stats, :c.inventory, :c.loadout, :c.skillTree, :c.appearance)")
@GetGeneratedKeys("id")
long createCharacter(@BindMethods("c") CharacterEntity characterEntity);
```

## 7. `CharacterService.createCharacter` — validates appearance first

```java
public ServiceResult<Long> createCharacter(long accountId, int maxSlots, Character character, Appearance appearance) {
    try {
        if (appearance == null || !appearance.isValid()) {
            log.info("Character creation failed for account ID " + accountId + ": invalid appearance " + appearance);
            return ServiceResult.failure(MessageCode.CHAR_INVALID_APPEARANCE);
        }

        if (characterDao.getCharacterCount(accountId) >= maxSlots) {
            log.info("Character creation failed for account ID " + accountId + ": Maximum character slots reached.");
            return ServiceResult.failure(MessageCode.CHAR_MAX_SLOTS_REACHED);
        }

        if (characterDao.isNameTaken(character.name())) {
            log.info("Character creation failed for account ID " + accountId + ": Name '" + character.name() + "' is already taken.");
            return ServiceResult.failure(MessageCode.CHAR_NAME_TAKEN);
        }

        CharacterEntity newEntity = CharacterEntity.fromCommonModel(accountId, character, appearance);
        long newId = characterDao.createCharacter(newEntity);

        if (newId > 0) {
            log.info("Successfully created character '" + character.name() + "' (ID: " + newId + ") for account ID: " + accountId);
            return ServiceResult.success(MessageCode.CHAR_CREATE_SUCCESS, newId);
        } else {
            log.error("Failed to create character for account ID " + accountId + ". Database returned ID 0.");
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    } catch (Exception e) {
        log.error("Exception occurred while creating character '" + character.name() + "' for account ID: " + accountId, e);
        return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
    }
}
```

The appearance check runs before the slot/name checks, same as `allocateStatPoints`' own negative-delta check runs before touching the database — a pure input-shape rejection doesn't need a DB round trip first.

## 8. `CreateCharacterRequestHandler` — pass the new field through

```java
var result = characterService.createCharacter(
    connection.account.id(), connection.account.maxSlots(), request.character(), request.appearance());
```

The rest of the handler (logging, `CreateCharacterResponse`) is unchanged.

## 9. `CharacterCreationScreen` — the one real client change in this expansion

Every other epic in this session deferred client wiring because the screen it would touch didn't exist yet or was placeholder-only. This screen is live, so T1 actually extends it, mirroring the existing faction-button pattern (`createFactionSelectionTable`, lines ~85-118) for each of the four new choices.

Add four selected-value fields, defaulting to each list's first entry:

```java
private String selectedGender = Appearance.GENDERS.get(0);
private String selectedHairType = Appearance.HAIR_TYPES.get(0);
private String selectedHairColor = Appearance.HAIR_COLORS.get(0);
private String selectedSkinColor = Appearance.SKIN_COLORS.get(0);
```

Add a new section, built the same way `createFactionSelectionTable` builds a row of toggle buttons per option — one row per category is enough for a first pass (no portrait/preview yet, since nothing renders this data):

```java
private VisTable createAppearanceSelectionTable() {
    VisTable appearanceTable = new VisTable();
    appearanceTable.add(createOptionRow("Gender", Appearance.GENDERS, selectedGender, v -> selectedGender = v)).expandX().fillX().row();
    appearanceTable.add(createOptionRow("Hair Type", Appearance.HAIR_TYPES, selectedHairType, v -> selectedHairType = v)).expandX().fillX().row();
    appearanceTable.add(createOptionRow("Hair Color", Appearance.HAIR_COLORS, selectedHairColor, v -> selectedHairColor = v)).expandX().fillX().row();
    appearanceTable.add(createOptionRow("Skin Color", Appearance.SKIN_COLORS, selectedSkinColor, v -> selectedSkinColor = v)).expandX().fillX();
    return appearanceTable;
}

private VisTable createOptionRow(String label, List<String> options, String initiallySelected, java.util.function.Consumer<String> onSelect) {
    VisTable rowTable = new VisTable();
    rowTable.add(new VisLabel(label + ": ")).width(100);
    for (String option : options) {
        new UIButtonBuilder(option)
            .withStyle(customButtonStyle)
            .withSize(90, 30)
            .withAction(() -> onSelect.accept(option))
            .buildAndAddTo(rowTable);
    }
    return rowTable;
}
```

`createOptionRow`'s button doesn't visually track which option is currently selected (no pressed/highlighted state) — a real first pass would want that, but `VisImageButton`/checked-style toggling is a UI-polish concern orthogonal to T1's actual acceptance criteria (the value reaching the server correctly); add it if it's a small lift given the existing button style, but don't block on it.

Wire the new table into `setupUI()`, alongside `createStatsTable`:

```java
middleTable.add(createFactionSelectionTable()).expandX().fillX().padBottom(20).row();
middleTable.add(createAppearanceSelectionTable()).expandX().fillX().padBottom(20).row();
middleTable.add(createStatsTable()).expand().fill();
```

And build the real `Appearance` in `submitCharacterCreation`, passing it as `CreateCharacterRequest`'s second argument:

```java
Appearance appearance = new Appearance(selectedGender, selectedHairType, selectedHairColor, selectedSkinColor);
game.getNetworkClient().sendTCP(new CreateCharacterRequest(newCharacter, appearance));
```

Add the `io.github.ydhekim.crimson_sky.common.model.Appearance` import (and `java.util.List` if not already imported).

## 10. `KryoConfig` — one registration, appended at the very end

`CreateCharacterRequest`/`CreateCharacterResponse` are already registered near the top of the file (they predate this a–k expansion) — that registration doesn't move, and doesn't need touching just because the record grew a field. Only the brand-new `Appearance` type needs registering, and it goes at the very end (system design §5, append-only), after `SetEquippedTitleResponse`:

```java
// Character customization (Epic T1 / system design §23) — appended after SetEquippedTitleResponse so every
// positional ID above is untouched (system design §5, append-only), even though CreateCharacterRequest
// itself (which now carries an Appearance) was registered much earlier — record field changes don't move
// a type's own registration, only a brand-new type needs a new call.
kryo.register(Appearance.class, new RecordSerializer<>(Appearance.class));
```

## 11. Test/fixture call-site fixes

**`FakeCharacterDao.createCharacter(CharacterEntity)`** — already takes a whole `CharacterEntity`, so it needs no signature change; just confirm it doesn't reference the old field count anywhere that would break (it shouldn't — it likely just stores the whole entity or extracts an id).

**Any test that builds a `CharacterEntity` directly** — grep confirms `CharacterEntity`'s own canonical constructor is referenced nowhere but `CharacterEntity.java` itself, so this is a one-file change plus its one caller (`CharacterService.createCharacter`, covered above); no test fixture needs updating for the new field.

## 12. Tests — new coverage, since `createCharacter` has none today

Grounding found that `CharacterService.createCharacter` has no dedicated test file at all yet — this is the first time it gets one. New `CharacterServiceCreateCharacterTest` (real `TestDatabase`, mirrors the shape of `CharacterServiceAllocateStatPointsTest`):

- A valid appearance (one real value per category) succeeds; the row's `appearance` column round-trips the same four values back out (query it directly via a small new `TestDatabase.appearanceOf(characterId)` helper, similar to `equippedTitleOf`).
- An appearance with one field outside its curated list (e.g. `gender = "OTHER"`) is rejected with `CHAR_INVALID_APPEARANCE`, and no row is inserted.
- A `null` appearance is rejected the same way, not an NPE.
- The appearance check doesn't short-circuit the existing `CHAR_MAX_SLOTS_REACHED`/`CHAR_NAME_TAKEN` checks in a way that breaks them — one test with a *valid* appearance but a full slot count still returns `CHAR_MAX_SLOTS_REACHED`, confirming the new check was added, not substituted.

**New `AppearanceTest`** (pure, no DB): `isValid()` true for a fully-curated instance, false for each individual field being outside its list, false for `null` fields.

## Testing

Run `gradlew.bat build` (this touches `core`, so `server:test` alone isn't enough — confirm the client module still compiles). Confirm the new tests pass and `CreateCharacterRequestHandlerTest` (if one exists) still compiles against the two-argument `createCharacter` call.

## Definition of done

- A player picks gender, hair type, hair color, and skin color on the character creation screen; an invalid combination (shouldn't be reachable through the UI, but is server-enforced regardless) is rejected.
- `characters.appearance` persists the choice; nothing reads it back yet (correct — M5's art pipeline is what eventually will).
- `gradlew.bat build` is green, including the `core` module.
- This closes out the entire 2026-07-10 a–k expansion (Epics L through T). The remaining backlog is M4 (combat client) and M5, both already sequenced after this work in `00-project-plan.md`.
