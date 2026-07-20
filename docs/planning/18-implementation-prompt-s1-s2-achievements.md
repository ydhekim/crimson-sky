# Implementation prompt — Epic S1-S2: Declarative achievements + automatic unlock

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §22, `docs/planning/02-user-stories.md` Epic S). This prompt covers **S1 and S2 only** — the achievement content model and automatic unlock-with-reward. **S3 (character page) and S4 (equip a title) are deliberately a separate, later prompt**: they're read-mostly aggregation work that depends on unlocks actually existing, and the design doc itself defers their packet shape to implementation time.

**Grounding first, since this replaces a scaffold rather than extending it:** `achievement_definitions`/`account_achievements` already exist (V4 migration) — 10 seeded achievements (the design doc says 9; there are actually 10 rows in `V4__Base_Achievements_And_Localizations.sql` — not fixed here, just noted), a read endpoint (`AchievementService.getPlayerAchievements`), a client screen. **Nothing anywhere writes to `account_achievements`** — every achievement is permanently locked for every account today. This prompt is what makes them unlockable.

**A simplification found while grounding, worth stating up front: this ships as two call sites, not four.** §22 describes four checkpoints (post-battle, post-level-up, post-item-grant, post-account-creation), but in this codebase leveling and the milestone item grant only ever happen *as a consequence of* a battle, both already inside `RewardService.applyRewards`'s existing transaction. So post-battle/post-level-up/post-item-grant collapse into **one** evaluation call at the end of that transaction; only post-account-creation (`UserService.loginTestUser`) is a genuinely separate call site.

Read `docs/planning/01-system-design-combat-engine.md` §22 before starting.

## 1. Migration V15

V14 is already taken (R3's `ladder_claims`), so this is `V15__Redesign_Achievements_And_Character_Statistics.sql`. Includes `battle_history.turn_count` here rather than deferring it to the S3 prompt: `FASTEST_WIN_TURNS` (this story's own criteria vocabulary) needs it to evaluate at all.

```sql
ALTER TABLE battle_history ADD COLUMN turn_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE achievement_definitions ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE achievement_definitions ADD COLUMN category VARCHAR(30);
ALTER TABLE achievement_definitions ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE achievement_definitions ADD COLUMN points INTEGER NOT NULL DEFAULT 10;
ALTER TABLE achievement_definitions ADD COLUMN criteria_type VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_CREATED_BEFORE';
ALTER TABLE achievement_definitions ADD COLUMN criteria_params JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE achievement_definitions ADD COLUMN gold_reward INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN badge_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN title_id VARCHAR(50);
ALTER TABLE achievement_definitions ADD COLUMN bonus_character_slots INTEGER NOT NULL DEFAULT 0;
ALTER TABLE achievement_definitions ADD COLUMN bonus_daily_battles INTEGER NOT NULL DEFAULT 0;

ALTER TABLE account_achievements RENAME TO achievement_unlocks;
ALTER TABLE achievement_unlocks DROP COLUMN progress_data;
ALTER TABLE achievement_unlocks ADD COLUMN character_id INTEGER REFERENCES characters (id) ON DELETE CASCADE;
ALTER TABLE achievement_unlocks DROP CONSTRAINT account_achievements_account_id_achievement_id_key;
CREATE UNIQUE INDEX achv_unlock_account_uq ON achievement_unlocks (account_id, achievement_id) WHERE character_id IS NULL;
CREATE UNIQUE INDEX achv_unlock_character_uq ON achievement_unlocks (account_id, achievement_id, character_id) WHERE character_id IS NOT NULL;
```

The constraint name in the `DROP CONSTRAINT` line is not a guess — it's Postgres's deterministic auto-generated name for V1's inline `UNIQUE (account_id, achievement_id)` (`{table}_{col1}_{col2}_key`), confirmed by reading `V1__Initial_Schema.sql` directly. `characters.equipped_title` is **not** added here — S4 owns that, in its own later migration.

## 2. Content — assign real values to the 10 seeded achievements

This is explicitly first-pass, placeholder-caliber content — adjust individual mappings freely if a better thematic fit occurs to you; the only hard requirement is that all 10 get real, valid `scope`/`criteria_type`/`criteria_params`/`points` values so none are stuck on the migration's default. Add this as an `UPDATE` block (or fold into a new `INSERT ... ON CONFLICT DO UPDATE`) in the same migration or a follow-up statement — your call on the exact SQL shape, but do it via a data migration, not a Java admin tool (nothing like that exists yet).

| key_name | scope | criteria_type | criteria_params | points | category | hidden |
|---|---|---|---|---|---|---|
| PIONEER_OF_CRIMSON_SKY | ACCOUNT | ACCOUNT_CREATED_BEFORE | `{"date":"2026-12-31"}` | 10 | ONBOARDING | false |
| DAY_ONE_SURVIVOR | ACCOUNT | ACCOUNT_CREATED_BEFORE | `{"date":"2026-08-01"}` | 25 | ONBOARDING | false |
| A_NEW_LEGEND_RISES | CHARACTER | CHARACTER_LEVEL | `{"threshold":10}` | 25 | PROGRESSION | false |
| FIRST_BLOOD | CHARACTER | TOTAL_WINS | `{"threshold":1}` | 10 | COMBAT | false |
| UNBROKEN | CHARACTER | WIN_STREAK | `{"threshold":3}` | 30 | COMBAT | false |
| THE_FIRST_CRY_OF_STEEL | CHARACTER | ITEM_ACQUIRED | `{"rarity":"COMMON"}` | 10 | COLLECTION | false |
| THE_FIRST_WHISPER_OF_SKIES | CHARACTER | CHARACTER_LEVEL | `{"threshold":5}` | 15 | PROGRESSION | false |
| TWO_SHADOWS_IN_THE_VOID | CHARACTER | TOTAL_WINS | `{"threshold":10}` | 40 | COMBAT | true |
| THE_PERFECT_STORM | CHARACTER | WIN_STREAK | `{"threshold":5}` | 50 | COMBAT | false |
| GHOST_OF_THE_SKIES | CHARACTER | FASTEST_WIN_TURNS | `{"maxTurns":3}` | 75 | COMBAT | true |

Two notes on fit, so nobody re-derives these as bugs later: `UNBROKEN`'s flavor text ("returned from the brink of death") doesn't literally match any of the 6 v1.0 criteria types — mapped to a win streak instead, a defensible reinterpretation, not a literal one. `TWO_SHADOWS_IN_THE_VOID`'s flavor (pet-bonding) has no matching criteria type either, since pets are never granted through the `ITEM_ACQUIRED` hook (only weapons are, see §5) — mapped to a win-count milestone instead.

## 3. `server.achievement` package — pure pieces, no DB/Ashley dependency

New package `server/src/main/java/io/github/ydhekim/crimson_sky/server/achievement/`.

**`AchievementScope`** enum: `ACCOUNT`, `CHARACTER`.

**`AchievementCriteriaType`** enum: `TOTAL_WINS`, `WIN_STREAK`, `FASTEST_WIN_TURNS`, `CHARACTER_LEVEL`, `ITEM_ACQUIRED`, `ACCOUNT_CREATED_BEFORE`.

**`CharacterAchievementFacts`** — everything a character-scoped criterion might need, gathered once per evaluation call:

```java
public record CharacterAchievementFacts(
    int totalWins,
    int currentWinStreak,
    Integer fastestWinTurns,      // null if no win yet (never satisfies FASTEST_WIN_TURNS)
    int characterLevel,
    List<Rarity> justGrantedItemRarities  // this battle's milestone-bonus grants only, possibly empty
) {
}
```

**`AccountAchievementFacts`**:

```java
public record AccountAchievementFacts(Instant accountCreatedAt) {
}
```

**`AchievementEvaluator`** — pure, static, same shape as `ActionResolver`/`AchievementEvaluator` per §22:

```java
public final class AchievementEvaluator {
    private AchievementEvaluator() {
    }

    public static boolean isSatisfiedForCharacter(AchievementDefinitionEntity def, CharacterAchievementFacts facts) {
        return switch (def.criteriaType()) {
            case TOTAL_WINS -> facts.totalWins() >= intParam(def, "threshold");
            case WIN_STREAK -> facts.currentWinStreak() >= intParam(def, "threshold");
            case FASTEST_WIN_TURNS -> facts.fastestWinTurns() != null
                && facts.fastestWinTurns() <= intParam(def, "maxTurns");
            case CHARACTER_LEVEL -> facts.characterLevel() >= intParam(def, "threshold");
            case ITEM_ACQUIRED -> {
                String rarity = (String) def.criteriaParams().get("rarity");
                yield rarity != null && facts.justGrantedItemRarities().stream()
                    .anyMatch(r -> r.name().equals(rarity));
            }
            case ACCOUNT_CREATED_BEFORE -> false; // account-scoped only, never asked of a character
        };
    }

    public static boolean isSatisfiedForAccount(AchievementDefinitionEntity def, AccountAchievementFacts facts) {
        if (def.criteriaType() != AchievementCriteriaType.ACCOUNT_CREATED_BEFORE) {
            return false; // character-scoped criteria, never asked of an account
        }
        LocalDate cutoff = LocalDate.parse((String) def.criteriaParams().get("date"));
        return facts.accountCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().isBefore(cutoff)
            || facts.accountCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().isEqual(cutoff);
    }

    private static int intParam(AchievementDefinitionEntity def, String key) {
        return ((Number) def.criteriaParams().get(key)).intValue();
    }
}
```

Jackson deserializes JSON numbers as `Integer`/`Long`/`Double` depending on shape — `Number.intValue()` handles all of them safely; don't cast directly to `Integer`.

**`UnlockedAchievement`** — a small result record so a caller can see what just happened (not wired to any client packet in this pass — see §7):

```java
public record UnlockedAchievement(String keyName, int points) {
}
```

## 4. `AchievementDefinitionEntity`

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/entity/AchievementDefinitionEntity.java`:

```java
public record AchievementDefinitionEntity(
    long id,
    String keyName,
    AchievementScope scope,
    AchievementCriteriaType criteriaType,
    @Json Map<String, Object> criteriaParams,
    int xpReward,
    int goldReward,
    String badgeId,
    String titleId,
    int bonusCharacterSlots,
    int bonusDailyBattles,
    int points,
    boolean hidden,
    String category
) {
}
```

## 5. `AchievementDao` — extended, not replaced

`server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/AchievementDao.java` keeps its existing `getAchievementsForAccount` query untouched (still used by the existing read endpoint) but its `LEFT JOIN account_achievements aa` needs updating to `achievement_unlocks` and its `aa.progress_data AS progressData` column needs dropping (that column is gone after V15) — `AccountAchievement`'s `progressData` field becomes dead; either drop the field (and fix its one constructor call site) or leave it always-null with a comment explaining why. Your call — the existing read endpoint isn't this story's focus, just don't leave it referencing a column that no longer exists.

Add:

```java
@RegisterConstructorMapper(AchievementDefinitionEntity.class)
@SqlQuery("SELECT id, key_name AS keyName, scope, criteria_type AS criteriaType, criteria_params AS criteriaParams, " +
    "xp_reward AS xpReward, gold_reward AS goldReward, badge_id AS badgeId, title_id AS titleId, " +
    "bonus_character_slots AS bonusCharacterSlots, bonus_daily_battles AS bonusDailyBattles, " +
    "points, hidden, category FROM achievement_definitions")
List<AchievementDefinitionEntity> findAllDefinitions();

/**
 * Attempts an ACCOUNT-scope unlock, idempotently (system design §22) — targets the
 * {@code achv_unlock_account_uq} partial index. Returns rows affected: 1 on a genuinely new unlock, 0 if
 * already unlocked. No separate "is this unlocked" read-then-write: the ON CONFLICT clause is the only
 * check, so there's no TOCTOU gap to accept here at all.
 */
@SqlUpdate("INSERT INTO achievement_unlocks (account_id, achievement_id, character_id) " +
    "VALUES (:accountId, :achievementId, NULL) ON CONFLICT (account_id, achievement_id) WHERE character_id IS NULL DO NOTHING")
int insertAccountUnlockIgnoringConflict(@Bind("accountId") long accountId, @Bind("achievementId") long achievementId);

/** As above, but CHARACTER-scope — targets {@code achv_unlock_character_uq} instead, a different partial index. */
@SqlUpdate("INSERT INTO achievement_unlocks (account_id, achievement_id, character_id) " +
    "VALUES (:accountId, :achievementId, :characterId) ON CONFLICT (account_id, achievement_id, character_id) WHERE character_id IS NOT NULL DO NOTHING")
int insertCharacterUnlockIgnoringConflict(@Bind("accountId") long accountId, @Bind("achievementId") long achievementId,
                                          @Bind("characterId") long characterId);
```

Two separate `INSERT` methods, not one dynamic query, because the two partial unique indexes are two distinct conflict targets — a single parameterized `ON CONFLICT` clause can't switch between them.

## 6. `BattleHistoryDao` — three additions

Add to `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/dao/BattleHistoryDao.java`:

```java
/** All-time win count, no period bound (system design §22) — TOTAL_WINS' own live-computed input. */
@SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId AND won = true")
int countTotalWins(@Bind("characterId") long characterId);

/**
 * The most recent {@code limit} outcomes, newest first (system design §22) — WIN_STREAK's input.
 * {@link AchievementUnlockService} counts leading {@code true}s; a generous limit (50) is plenty for any
 * realistic streak length without scanning the whole table.
 */
@SqlQuery("SELECT won FROM battle_history WHERE character_id = :characterId ORDER BY created_at DESC LIMIT :limit")
List<Boolean> findRecentOutcomes(@Bind("characterId") long characterId, @Bind("limit") int limit);

/**
 * The lowest {@code turn_count} among this character's wins, or empty if none yet (system design §22) —
 * FASTEST_WIN_TURNS' input. {@code turn_count > 0} excludes rows written before this column existed
 * (defaulted to 0), which would otherwise look like an unbeatable 0-turn win.
 */
@SqlQuery("SELECT MIN(turn_count) FROM battle_history WHERE character_id = :characterId AND won = true AND turn_count > 0")
Optional<Integer> findFastestWinTurnCount(@Bind("characterId") long characterId);
```

`insert` gains `turnCount`:

```java
@SqlUpdate("INSERT INTO battle_history (character_id, opponent_character_id, opponent_is_bot, won, gold_delta, experience_delta, elo_delta, battle_mode, ranked_elo_delta, turn_count) " +
    "VALUES (:characterId, :opponentCharacterId, :opponentIsBot, :won, :goldDelta, :expDelta, :eloDelta, :battleMode, :rankedEloDelta, :turnCount)")
void insert(@Bind("characterId") long characterId,
            @Bind("opponentCharacterId") Long opponentCharacterId,
            @Bind("opponentIsBot") boolean opponentIsBot,
            @Bind("won") boolean won,
            @Bind("goldDelta") int goldDelta,
            @Bind("expDelta") long expDelta,
            @Bind("eloDelta") int eloDelta,
            @Bind("battleMode") String battleMode,
            @Bind("rankedEloDelta") Integer rankedEloDelta,
            @Bind("turnCount") int turnCount);
```

`turnCount` is `result.turns().size` from `RewardService`'s already-loaded `AttackResult` — that `Array` is one entry per turn the attacker's side produced, the same number `AttackService.resolveBattle`'s own log line already reports as `battleEngine.turnNumber()`. No change needed to `AttackResult`/`AttackService` — the number already exists where `RewardService` can reach it.

## 7. `CharacterDao` — one addition

```java
/**
 * A pure additive XP grant (system design §22's achievement rewards) — deliberately does NOT recompute
 * level the way {@link #applyBattleProgress} does. An achievement's XP reward compounds into the
 * character's next real battle's level-up check rather than triggering an immediate cascading recompute
 * here; extremely rare in practice (would need an achievement's own XP to itself cross a level threshold
 * the triggering battle's XP didn't), and this keeps achievement evaluation from recursing into another
 * achievement evaluation. A documented first-pass simplification, not an oversight.
 */
@SqlUpdate("UPDATE characters SET experience = experience + :xpDelta WHERE id = :characterId")
void addExperience(@Bind("characterId") long characterId, @Bind("xpDelta") long xpDelta);
```

## 8. `AchievementUnlockService` — the DB-touching orchestrator

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/service/AchievementUnlockService.java`. Stateless — no constructor dependencies, since every method receives the caller's own JDBI `Handle` and attaches whatever DAOs it needs to that same handle, so an unlock and its reward always land in whichever transaction the caller already has open.

```java
public class AchievementUnlockService {

    public List<UnlockedAchievement> evaluateCharacterAchievements(
            Handle handle, long accountId, long characterId, CharacterAchievementFacts facts) {
        AchievementDao achievementDao = handle.attach(AchievementDao.class);
        List<UnlockedAchievement> unlocked = new ArrayList<>();
        for (AchievementDefinitionEntity def : achievementDao.findAllDefinitions()) {
            if (def.scope() != AchievementScope.CHARACTER) {
                continue;
            }
            if (!AchievementEvaluator.isSatisfiedForCharacter(def, facts)) {
                continue;
            }
            int rows = achievementDao.insertCharacterUnlockIgnoringConflict(accountId, def.id(), characterId);
            if (rows == 0) {
                continue; // already unlocked — ON CONFLICT caught it, no TOCTOU gap to worry about
            }
            applyReward(handle, accountId, characterId, def);
            unlocked.add(new UnlockedAchievement(def.keyName(), def.points()));
        }
        return unlocked;
    }

    public List<UnlockedAchievement> evaluateAccountAchievements(
            Handle handle, long accountId, AccountAchievementFacts facts) {
        AchievementDao achievementDao = handle.attach(AchievementDao.class);
        List<UnlockedAchievement> unlocked = new ArrayList<>();
        for (AchievementDefinitionEntity def : achievementDao.findAllDefinitions()) {
            if (def.scope() != AchievementScope.ACCOUNT) {
                continue;
            }
            if (!AchievementEvaluator.isSatisfiedForAccount(def, facts)) {
                continue;
            }
            int rows = achievementDao.insertAccountUnlockIgnoringConflict(accountId, def.id());
            if (rows == 0) {
                continue;
            }
            applyReward(handle, accountId, null, def);
            unlocked.add(new UnlockedAchievement(def.keyName(), def.points()));
        }
        return unlocked;
    }

    /**
     * XP/gold target the triggering character/account; bonus_character_slots/bonus_daily_battles reuse
     * Epic Q's atomic-increment grant paths — their first real caller, built then and left unwired for
     * exactly this (system design §20/§22). badge_id/title_id need no code action here: a badge is simply
     * the fact of the unlock row plus a non-null badge_id (S3 reads it); a title needs
     * characters.equipped_title, which doesn't exist until S4.
     */
    private void applyReward(Handle handle, long accountId, Long characterId, AchievementDefinitionEntity def) {
        if (def.xpReward() > 0 && characterId != null) {
            handle.attach(CharacterDao.class).addExperience(characterId, def.xpReward());
        }
        if (def.goldReward() > 0) {
            handle.attach(AccountDao.class).addGlobalCurrency(accountId, def.goldReward());
        }
        if (def.bonusCharacterSlots() > 0) {
            handle.attach(AccountDao.class).addCharacterSlots(accountId, def.bonusCharacterSlots());
        }
        if (def.bonusDailyBattles() > 0 && characterId != null) {
            handle.attach(CharacterDao.class).addBonusDailyBattles(characterId, def.bonusDailyBattles());
        }
    }
}
```

## 9. `RewardService` — one call, end of the existing transaction

`RewardService`'s constructor gains an `AchievementUnlockService` dependency, appended after `BattleHistoryDao` (same low-disruption pattern R1-R2 used):

```java
public RewardService(Jdbi jdbi, CharacterService characterService, BattleHistoryDao battleHistoryDao,
                     AchievementUnlockService achievementUnlockService) {
    this(jdbi, characterService, battleHistoryDao, achievementUnlockService, new Random());
}

public RewardService(Jdbi jdbi, CharacterService characterService, BattleHistoryDao battleHistoryDao,
                     AchievementUnlockService achievementUnlockService, Random random) {
    this.characterService = characterService;
    this.battleHistoryDao = battleHistoryDao;
    this.achievementUnlockService = achievementUnlockService;
    this.random = random;
    this.jdbi = jdbi;
}
```

Inside `applyRewards`'s `jdbi.useTransaction(handle -> { ... })`, after the existing history insert (now passing `result.turns().size` as `turnCount`) and the existing inventory-update block, add the achievement evaluation as the last step:

```java
BattleHistoryDao txBattleHistoryDao = handle.attach(BattleHistoryDao.class);
txBattleHistoryDao.insert(
    result.characterId(), result.opponentCharacterId(), result.opponentIsBot(),
    result.won(), outcome.goldDelta(), outcome.expDelta(), outcome.eloDelta(),
    mode.name(), mode == BattleMode.RANKED ? outcome.eloDelta() : null,
    result.turns() != null ? result.turns().size : 0);

// ...existing inventory-update block, unchanged...

CharacterAchievementFacts facts = new CharacterAchievementFacts(
    txBattleHistoryDao.countTotalWins(result.characterId()),
    currentStreak(txBattleHistoryDao.findRecentOutcomes(result.characterId(), 50)),
    txBattleHistoryDao.findFastestWinTurnCount(result.characterId()).orElse(null),
    newLevel,
    bonusWeapons.stream().map(Weapon::rarity).toList());
achievementUnlockService.evaluateCharacterAchievements(handle, accountId, result.characterId(), facts);
```

Add a small private static helper:

```java
/** Leading true-run length of a newest-first outcome list (system design §22) — the current win streak. */
static int currentStreak(List<Boolean> recentOutcomesNewestFirst) {
    int streak = 0;
    for (boolean won : recentOutcomesNewestFirst) {
        if (!won) {
            break;
        }
        streak++;
    }
    return streak;
}
```

Package-private and static so it's unit-testable without a database, same convention `expNeededForLevel`/`levelAfter`/`isMilestone` already use in this file.

**A newly-unlocked achievement's XP/gold do not get folded into this same battle's reported `AttackResponse`/`RewardOutcome`** — that would mean the response's numbers no longer equal what `RewardService`'s own formulas computed, muddying "what did this battle pay" with "what did this battle plus whatever it unlocked pay". Achievement rewards land silently in the same transaction; surfacing "you just unlocked X" to the client is explicitly out of scope here (see §11) and can read the character's now-higher gold/XP the next time it asks, the same way any other server-side mutation the client didn't directly request is discovered.

## 10. `UserService` — the one standalone checkpoint

`server/src/main/java/io/github/ydhekim/crimson_sky/server/service/UserService.java` gains a `Jdbi` and an `AchievementUnlockService` dependency:

```java
public UserService(UserDao userDao, Jdbi jdbi, AchievementUnlockService achievementUnlockService) {
    this.userDao = userDao;
    this.jdbi = jdbi;
    this.achievementUnlockService = achievementUnlockService;
}
```

After a **new** account is created (the `else` branch in `loginTestUser` where `userDao.createUserAndAccount(...)` runs — not the existing-user branch), evaluate account-scope achievements in their own small transaction. A failure here must not fail the login — mirrors `RewardService`'s own "the underlying thing already happened, don't let a side-effect's failure hide that" posture:

```java
account = userDao.createUserAndAccount(platformType, identityToken);
log.info("Created new test user and account. Account ID: " + account.id() + ", Platform: " + platformType);

try {
    jdbi.useTransaction(handle -> achievementUnlockService.evaluateAccountAchievements(
        handle, account.id(), new AccountAchievementFacts(account.createdAt())));
} catch (Exception e) {
    log.error("Account-creation achievement evaluation failed for account " + account.id()
        + " — account was still created successfully.", e);
}
```

## 11. Wiring

`ServiceRegistry`:

```java
// Stateless — every method takes the caller's own Handle, so it needs no Jdbi/DAO of its own.
this.achievementUnlockService = new AchievementUnlockService();

// ...existing characterDao/attackService construction...

this.rewardService = new RewardService(dbManager.getJdbi(), characterService, battleHistoryDao, achievementUnlockService);

// ...

this.userService = new UserService(userDao, dbManager.getJdbi(), achievementUnlockService);
```

Add the field + getter for `achievementUnlockService` only if some other consumer needs it directly (none does yet in this prompt's scope — `RewardService`/`UserService` each hold their own reference). `UserService`'s construction line moves after `achievementUnlockService` is built, or `achievementUnlockService` moves earlier — whichever keeps `ServiceRegistry`'s constructor reading top-to-bottom without a forward reference.

**No packet/handler changes in this prompt.** Achievement unlocks aren't surfaced to the client anywhere yet — `AchievementListRequestHandler`/`AchievementService.getPlayerAchievements` still serve the existing (now-actually-populated) read endpoint unchanged. S3's character page is where "what did I just unlock" becomes visible.

## 12. Exhaustive call-site fixes

**Every `new RewardService(...)` call site** (the same ones enumerated when R1-R2 added `BattleHistoryDao` to this constructor — grep `new RewardService(` to get the current authoritative list, there are around 9) needs one more trailing argument before `Random` where present: `new AchievementUnlockService()`, e.g. `new RewardService(db.jdbi(), characterService, db.jdbi().onDemand(BattleHistoryDao.class), new AchievementUnlockService())`. `AchievementUnlockService` has a no-arg constructor, so this is a one-line addition at each site, no test fixture needed.

**Every `new UserService(...)` call site** — grep `new UserService(`. Production (`ServiceRegistry`) is covered by §11; check for test call sites and give each a real `Jdbi` (from that test's own `TestDatabase`) and a fresh `new AchievementUnlockService()`.

**`FakeBattleHistoryDao`** needs the three new methods (`countTotalWins`, `findRecentOutcomes`, `findFastestWinTurnCount`) and the new `insert` signature (`turnCount` param). Implement all three for real against its in-memory `Row` list (add a `turnCount` field to `Row`) — unlike the ranked-Elo case, none of these needs cross-DAO data the fake can't reach, so there's no need to stub/approximate here the way R1-R2 had to for ranked candidates.

## 13. Tests

**`AchievementEvaluatorTest`** (pure, no DB) — one case per criteria type: satisfied and not-satisfied, plus `FASTEST_WIN_TURNS`'s null-fastest-time case (never satisfied) and `ACCOUNT_CREATED_BEFORE`'s on-the-boundary-date case (satisfied — the check is inclusive).

**`RewardServiceTest.currentStreak` cases** (or a small dedicated test class) — empty list → 0; all wins → full length; a loss partway through → streak stops there, doesn't look past it.

**New `AchievementUnlockServiceTest`** (real `TestDatabase`, mirrors `LadderServiceTest`'s shape):
- Seed one CHARACTER-scope achievement satisfied by the given facts; confirm one `achievement_unlocks` row appears, gold/XP/bonus fields land where expected.
- Calling `evaluateCharacterAchievements` twice with the same satisfied facts unlocks it only once (second call's `ON CONFLICT` no-ops, no duplicate reward).
- An ACCOUNT-scope achievement and a CHARACTER-scope achievement for the same account/character don't collide with each other's partial index (seed both, confirm both unlock independently).
- `bonus_character_slots`/`bonus_daily_battles` rewards actually move `accounts.max_slots`/`characters.bonus_daily_battles` — the first real test exercising Epic Q's grant paths.

**Extend `RewardServiceTest`** (or a new focused test) with an end-to-end case: seed a character one win away from a real achievement (e.g. `FIRST_BLOOD`'s `TOTAL_WINS >= 1`), run `applyRewards` on a won battle, confirm the achievement unlocked and its `battle_history` row carries the right `turn_count`.

**`TestDatabase`** needs `achievement_definitions`/`achievement_unlocks` added to its H2 schema (mirroring the V15 shape — note H2's `CREATE UNIQUE INDEX ... WHERE ...` partial-index syntax; confirm it's supported the same way Postgres's is, or adapt if H2 needs a different partial-index spelling) plus `battle_history.turn_count`, and a couple of new seed helpers (`withAchievementDefinition(...)`, `achievementUnlockCountOf(...)` at minimum).

## Testing

Run `gradlew.bat server:test` (or `gradlew.bat build`). Confirm:

- Every call site in §12 compiles.
- `BattleLeavesInventoryAloneTest` (C2) still passes — achievement rewards never touch `inventory`/`loadout`, only `experience`/`global_currency`/`max_slots`/`bonus_daily_battles`, none of which C2 guards.
- New tests from §13 pass.
- The existing `AchievementListRequestHandler`/read-endpoint tests (if any) still pass against the renamed `achievement_unlocks` table and dropped `progress_data` column.

## Definition of done

- All 10 seeded achievements have real, evaluable `scope`/`criteria_type`/`criteria_params`.
- A battle that satisfies a character-scope achievement unlocks it and applies its reward atomically with the battle's own reward, exactly once ever.
- A new account that qualifies for an account-scope achievement unlocks it at creation, without ever blocking login on failure.
- Epic Q's `AccountDao.addCharacterSlots`/`CharacterDao.addBonusDailyBattles` have their first real caller.
- `gradlew.bat build` is green.
- S3 (character page) and S4 (equip title) are explicitly out of scope for this prompt.
