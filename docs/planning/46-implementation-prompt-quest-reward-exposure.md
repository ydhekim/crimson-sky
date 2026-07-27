# Implementation prompt — expose quest rewards over the wire

Prerequisite surfaced while designing the Character Hub screen (not part of that prompt itself, so it can land and be verified on its own first). The Hub wants to show each quest's reward clearly; today it can't, because nothing on the wire carries that information at all.

## The gap, precisely

`QuestProgress` (`common/model/QuestProgress.java`) carries `questId`, `description`, win progress, and claim state — no reward field of any kind. `QuestDefinition` (`server/quest/QuestDefinition.java`) says so directly in its own javadoc: "the reward is not modelled here." The actual reward logic is hardcoded inline in `QuestService.claim()`, which is the only place in the codebase that currently knows what each quest pays:

- **Daily** (`daily.win2`) → 1 skill-restoration scroll (`ShopService.SKILL_RESTORATION_SCROLL`), fixed.
- **Weekly** (`weekly.win10`) → the player's choice of 1 Repair Token or 1 Pet Care Kit (`ShopService.REPAIR_TOKEN`/`PET_CARE_KIT`).
- **Repeatable** (`repeatable.win1`) → a flat `QuestService.REPEATABLE_GOLD_REWARD` (15) gold.

None of this reaches the client today. A UI can't show "clear" rewards for data it was never sent.

## 1. New common model: `QuestReward`

```java
package io.github.ydhekim.crimson_sky.common.model;

/**
 * One reward a quest can pay (system design §19). {@code type} is {@code "GOLD"} or {@code "CONSUMABLE"};
 * {@code itemKey} is one of {@code ShopService}'s consumable string constants when type is {@code
 * CONSUMABLE}, {@code null} for {@code GOLD}. {@code amount} is the gold quantity, or the item count (always
 * {@code 1} for every reward this game currently grants). A quest with more than one {@code QuestReward} in
 * its {@code rewardOptions} list (system design §19's weekly quest) means the player picks one, not that
 * they receive all of them.
 */
public record QuestReward(String type, String itemKey, int amount) {
}
```

## 2. `QuestProgress` — one new field

```java
public record QuestProgress(
    String questId,
    String description,
    int currentWins,
    int targetWins,
    boolean claimable,
    boolean alreadyClaimed,
    int claimsRemainingToday,
    Array<QuestReward> rewardOptions
) {
}
```

Adding a field to an already-registered record is safe without touching its Kryo registration — record field changes don't move a type's own positional ID, only a brand-new type needs a new `kryo.register(...)` call (the same rule this codebase's own `KryoConfig` comments already state, e.g. around `Appearance`).

## 3. `QuestService.statusOf(...)` — compute the reward options

Both call sites (`QuestService.java:217` and `:221`) need the new argument. Add a small private helper that mirrors the reward knowledge `claim()` already hardcodes — this is the second place that knowledge needs to live, not a new decision:

```java
private static Array<QuestReward> rewardOptionsFor(QuestDefinition quest) {
    Array<QuestReward> rewards = new Array<>();
    switch (quest) {
        case DAILY_WIN_2 -> rewards.add(new QuestReward("CONSUMABLE", SKILL_RESTORATION_SCROLL, 1));
        case WEEKLY_WIN_10 -> {
            rewards.add(new QuestReward("CONSUMABLE", REPAIR_TOKEN, 1));
            rewards.add(new QuestReward("CONSUMABLE", PET_CARE_KIT, 1));
        }
        case REPEATABLE_WIN_1 -> rewards.add(new QuestReward("GOLD", null, REPEATABLE_GOLD_REWARD));
    }
    return rewards;
}
```

(References `ShopService`'s package-private consumable constants directly — same reasoning `QuestService`'s own class javadoc already gives for living in `server.service` rather than `server.quest`: reuse the existing string constants rather than duplicate them.) Pass `rewardOptionsFor(quest)` as the new trailing argument at both `new QuestProgress(...)` call sites in `statusOf(...)`.

## 4. `KryoConfig` — register the new type

Append after `Appearance` (the current last registration) — do not insert earlier, per system design §5's append-only rule:

```java
kryo.register(QuestReward.class, new RecordSerializer<>(QuestReward.class));
```

## 5. Localization — the three consumable display names

These have never been client-facing display strings before (no shop UI exists yet), so none of these keys exist. New migration, `server/src/main/resources/db/migration/V30__Add_Quest_Reward_Item_Localization.sql`:

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('UI_ITEM_SKILL_RESTORATION_SCROLL', 'UI'),
    ('UI_ITEM_REPAIR_TOKEN', 'UI'),
    ('UI_ITEM_PET_CARE_KIT', 'UI');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_SKILL_RESTORATION_SCROLL'), 'tr_TR', 'Beceri Onarım Parşömeni'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_SKILL_RESTORATION_SCROLL'), 'en_US', 'Skill Restoration Scroll'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_REPAIR_TOKEN'), 'tr_TR', 'Tamir Jetonu'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_REPAIR_TOKEN'), 'en_US', 'Repair Token'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_PET_CARE_KIT'), 'tr_TR', 'Evcil Hayvan Bakım Kiti'),
    ((SELECT id FROM localization_keys WHERE key_name = 'UI_ITEM_PET_CARE_KIT'), 'en_US', 'Pet Care Kit');
```

`itemKey` values (`skill_restoration_scroll`, `repair_token`, `pet_care_kit`) map to these keys via a small client-side lookup — a `Map<String, String>` from `ShopService`'s string constants (mirrored client-side, since the client can't import a server-only class) to the `UI_ITEM_*` key names above. That lookup, and the actual reward display, is the consuming Character Hub prompt's job, not this one — this prompt only needs to guarantee the data and its localized names exist and reach the wire correctly.

## 6. Testing / Definition of Done

Claude Code's job stops at automated checks — no manual client-driving:

1. `gradlew.bat build` — confirm it compiles.
2. `gradlew.bat test` — confirm nothing existing broke. Worth a small new test on `QuestService`: for each of the three `QuestDefinition` values, `statusOf(...)`'s resulting `QuestProgress.rewardOptions()` matches what `claim()` actually grants (one scroll, choice of two tokens, flat gold) — a regression guard against the two pieces of reward knowledge (`claim()`'s hardcoded logic and this new `rewardOptionsFor(...)` helper) silently drifting apart later.
3. Confirm the migration applies cleanly and both languages are seeded for all three new keys.

Manual verification — I'll check this myself once the Hub actually renders this data: each quest shows the reward it's actually documented to pay, in both languages.

Definition of done: `QuestStatusResponse` carries accurate, localizable reward data for all three quests; build and tests green; no change to any existing screen (this prompt touches only `common`/`server`).
