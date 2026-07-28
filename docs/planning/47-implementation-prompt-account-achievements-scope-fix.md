# Implementation prompt — fix MainMenu's achievement query to actually aggregate across characters

Second prerequisite surfaced while designing the Character Hub screen. This one is a real, live bug in the already-shipped `AchievementsScreen` (MainMenu destination), not new scope — found while checking why the same achievement list would show differently between MainMenu and the planned Hub.

## The bug, precisely

`achievement_definitions.scope` is either `ACCOUNT` or `CHARACTER` (`V15__Redesign_Achievements_And_Character_Statistics.sql`). Of the ten seeded achievements, only two are `ACCOUNT`-scope (the onboarding ones); the other eight are `CHARACTER`-scope (level, wins, streak, item, fastest-win — `V15` lines 45-66).

`AchievementDao.getAchievementsForAccount()` (used only by MainMenu's `AchievementsScreen`) selects from every definition, but its `LEFT JOIN achievement_unlocks au` requires `au.character_id IS NULL` — matching only `ACCOUNT`-scope unlocks. The eight `CHARACTER`-scope achievements are still listed (the query selects from all definitions), but their `isUnlocked` can never be true through this query, no matter how much a player has actually accomplished — there is no code path by which a `CHARACTER`-scope achievement ever produces an unlock row with `character_id IS NULL`. Today, MainMenu's Achievements screen shows 8 of its 10 entries as permanently locked, regardless of real progress. `CharacterPage`'s achievement list (used by the planned Character Hub) is unaffected — it already joins correctly by scope (`AchievementDao` lines 79/87) for one specific character.

## The fix: make the account-wide view a genuine aggregate, not a narrower one

Not "remove the duplicate screen" — the two views are legitimately different and both worth keeping once this is fixed: Character Hub shows *this character's* unlocks; MainMenu's Achievements screen should show whether *any* character on the account has ever unlocked it. Today it accidentally shows neither correctly for `CHARACTER`-scope achievements.

`getAchievementsForAccount` needs a query that returns exactly one row per definition (never fanning out if more than one of the account's characters happens to have unlocked the same `CHARACTER`-scope achievement independently) and picks the earliest qualifying unlock:

```sql
@SqlQuery("SELECT " +
    "  ad.key_name AS keyName, " +
    "  lk_t.key_name AS titleLocKey, " +
    "  lk_d.key_name AS descLocKey, " +
    "  ad.xp_reward AS xpReward, " +
    "  ad.icon_id AS iconId, " +
    "  (u.unlocked_at IS NOT NULL) AS isUnlocked, " +
    "  u.unlocked_at AS unlockedAt " +
    "FROM achievement_definitions ad " +
    "JOIN localization_keys lk_t ON ad.title_loc_key = lk_t.id " +
    "JOIN localization_keys lk_d ON ad.desc_loc_key = lk_d.id " +
    "LEFT JOIN LATERAL (" +
    "  SELECT MIN(au.unlocked_at) AS unlocked_at " +
    "  FROM achievement_unlocks au " +
    "  WHERE au.achievement_id = ad.id " +
    "    AND ((ad.scope = 'ACCOUNT' AND au.account_id = :accountId AND au.character_id IS NULL) " +
    "      OR (ad.scope = 'CHARACTER' AND au.character_id IN (SELECT id FROM characters WHERE account_id = :accountId)))" +
    ") u ON true " +
    "ORDER BY ad.id")
@RegisterConstructorMapper(AccountAchievement.class)
List<AccountAchievement> getAchievementsForAccount(@Bind("accountId") long accountId);
```

The `LEFT JOIN LATERAL ... ON true` with `MIN(unlocked_at)` is what guarantees exactly one output row per achievement definition regardless of whether zero, one, or several of the account's characters have unlocked it — a plain `LEFT JOIN` against `achievement_unlocks` directly would duplicate a `CHARACTER`-scope achievement's row once per qualifying character, which would both look wrong (repeated entries) and break `AchievementsScreen`'s existing unlocked-count math. `MIN()` picks whichever character unlocked it first, which is the only sensible "when did the account first earn this" answer when more than one qualifies.

No change needed to `AccountAchievement` itself — same fields, just correctly populated now.

## Update the screen's framing, not just the query

Now that MainMenu's view is a genuine "across every character on this account" aggregate rather than an accidentally-narrower one, it's worth being explicit about that distinction where a player can see it — otherwise the same achievement appearing unlocked on one screen and not the other (before a player's checked both) reads as a bug even after this fix, just a different one. Concretely: add a short subtitle/caption under `AchievementsScreen`'s existing title or count line clarifying the account-wide scope (a new small `UI_*` localization key, seeded the same way every other plain UI string in this codebase is — see `CLAUDE.md`'s note on seeding a new key in the same change as its call site). The exact wording is a small, low-risk copy decision — flag it for a quick look rather than treating it as locked in by this prompt.

## Testing / Definition of Done

Claude Code's job stops at automated checks — no manual client-driving:

1. `gradlew.bat build` — confirm it compiles.
2. `gradlew.bat test` — confirm nothing existing broke.
3. **New regression test, the one that would have caught this originally:** seed two characters on one account (`FakeAccountDao`/existing test-support fixtures); unlock a `CHARACTER`-scope achievement for character B only (not A); assert `getAchievementsForAccount(accountId)` reports it `isUnlocked = true`. Also assert an achievement unlocked by *neither* character still reports `false`, and that an `ACCOUNT`-scope achievement's existing behavior is unchanged.
4. Confirm the query returns exactly one row per achievement definition even when a `CHARACTER`-scope achievement has been unlocked by more than one character on the same account (no duplicate/fanned-out rows).

Manual verification — I'll check this myself: an achievement actually unlocked on a specific character now shows unlocked from MainMenu too, not just from that character's page.

Definition of done: MainMenu's Achievements screen correctly reflects real progress for every achievement regardless of scope; the two achievement views (account-wide aggregate vs. per-character) are each internally correct and distinguishable to a player; build and tests green.
