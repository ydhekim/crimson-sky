# Implementation prompt — Epic S3-S4: Character page + equip a title

Paste this whole file to Claude Code as the task.

## Context

Crimson Sky (`docs/planning/01-system-design-combat-engine.md` §22, `docs/planning/02-user-stories.md` Epic S). S1-S2 already shipped (`achievement_definitions`/`achievement_unlocks` with real criteria, automatic unlock-with-reward — see `18-implementation-prompt-s1-s2-achievements.md`). This prompt covers the two stories deferred from that one: **S3** (a read-only character-page aggregate: stats, battle history, unlocked achievements) and **S4** (equip an unlocked title). Both are read-mostly and only make sense now that unlocks actually exist.

**No client/UI wiring in this prompt** — same posture as every other epic in this expansion (Q, R, S1-S2): new packets, a new service, new handlers, server-side only. `PacketHandlerRegistry`/screens are M4 work.

## 1. Migration V16

`V16__Add_Equipped_Title.sql` (V15 is S1-S2's):

```sql
ALTER TABLE characters ADD COLUMN equipped_title VARCHAR(50);
```

## 2. New common-model records (`common/model`)

These cross the wire, so they live in `common.model` alongside `LadderStatus`/`QuestProgress`, not server-only.

```java
public record RecentMatch(boolean won, String battleMode, int eloDelta, Integer rankedEloDelta,
                          int turnCount, String opponentName, Instant occurredAt) {
}
```

`rankedEloDelta` is `null` for a `NORMAL` battle (mirrors `battle_history.ranked_elo_delta`'s own nullability). `opponentName` is never `null` on the wire — see §4 for why.

```java
public record CharacterStatistics(int totalWins, int totalLosses, double winPercentage,
                                  int currentWinStreak, Integer fastestWinTurns, List<RecentMatch> recentMatches) {
}
```

`fastestWinTurns` is `null` when the character has never won (same nullability as `AchievementEvaluator`'s own fact of the same name). `winPercentage` is `0.0` when `totalWins + totalLosses == 0` — don't divide by zero.

```java
public record CharacterPageAchievement(String keyName, String titleLocKey, String descLocKey, int points,
                                       String badgeId, boolean hidden, String category,
                                       boolean isUnlocked, String unlockedAt) {
}
```

Same shape as the existing `AccountAchievement` plus the S1 columns (`points`/`badgeId`/`hidden`/`category`) minus `xpReward`/`iconId` (not needed for this view). Deliberately a new record rather than widening `AccountAchievement` — the existing read endpoint's contract stays exactly as it is. `hidden` is carried through as data only; nothing server-side masks a hidden-and-locked achievement's title/desc based on it — there's no established client convention for "???" masking yet (that's M4 work), so don't invent one here.

```java
public record CharacterPage(Character character, CharacterStatistics statistics,
                            List<CharacterPageAchievement> achievements, int achievementScore,
                            String equippedTitle) {
}
```

`achievementScore` is the sum of `points` across every entry in `achievements` where `isUnlocked` — compute it in Java after the list is assembled, not as a separate query. `equippedTitle` is `null` when nothing is equipped.

## 3. New packets (`common/network/packet`)

```java
/** Client → server request for a character's full page (system design §22): stats, history, achievements. */
public record CharacterPageRequest(long characterId) {
}

public record CharacterPageResponse(boolean success, String message, CharacterPage page) {
}

/**
 * Client → server request to equip an unlocked title, or clear it ({@code titleId == null}). Ownership of
 * {@code characterId} and unlock-ownership of {@code titleId} are both validated server-side.
 */
public record SetEquippedTitleRequest(long characterId, String titleId) {
}

public record SetEquippedTitleResponse(boolean success, String message, String equippedTitle) {
}
```

## 4. `BattleHistoryDao` — three additions

A genuine gap found while grounding this: a bot opponent has no row in `characters` at all (system design §8/V8), and the bot's generated name is never persisted anywhere — `BotFactory` synthesizes it fresh at battle time. So a `LEFT JOIN` for a historical bot-opponent match comes back with a `NULL` name, and V8's own comment is explicit that "a bot must stay indistinguishable from a real opponent" — but a bare `NULL` (or an `opponentIsBot` flag) would itself be exactly that tell. Resolution: null-name rows get a fixed, non-revealing placeholder (`"Unknown Challenger"`) rather than exposing `NULL` or the `opponent_is_bot` column — applied in `CharacterPageService`, not the DAO, since the DAO's own row type should carry the raw (possibly null) fact.

```java
/** All-time battle count, win or lose (system design §22) — the denominator for win percentage. */
@SqlQuery("SELECT COUNT(*) FROM battle_history WHERE character_id = :characterId")
int countTotalBattles(@Bind("characterId") long characterId);

/**
 * The most recent {@code limit} battles with enough detail for a match-history list (system design §22).
 * {@code opponentName} is {@code NULL} for a bot opponent (no `characters` row to join) or a since-deleted
 * real character — {@link io.github.ydhekim.crimson_sky.server.service.CharacterPageService} is what turns
 * that {@code NULL} into a display-safe placeholder, not this query.
 */
@RegisterConstructorMapper(RecentMatchRow.class)
@SqlQuery("SELECT bh.won AS won, bh.battle_mode AS battleMode, bh.elo_delta AS eloDelta, " +
    "bh.ranked_elo_delta AS rankedEloDelta, bh.turn_count AS turnCount, bh.created_at AS occurredAt, " +
    "c.name AS opponentName FROM battle_history bh LEFT JOIN characters c ON bh.opponent_character_id = c.id " +
    "WHERE bh.character_id = :characterId ORDER BY bh.created_at DESC LIMIT :limit")
List<RecentMatchRow> findRecentMatches(@Bind("characterId") long characterId, @Bind("limit") int limit);
```

New file `server/src/main/java/io/github/ydhekim/crimson_sky/server/database/entity/RecentMatchRow.java`:

```java
public record RecentMatchRow(boolean won, String battleMode, int eloDelta, Integer rankedEloDelta,
                             int turnCount, Instant occurredAt, String opponentName) {
}
```

## 5. `CharacterDao` — two additions

```java
@SqlQuery("SELECT equipped_title FROM characters WHERE id = :characterId")
Optional<String> getEquippedTitle(@Bind("characterId") long characterId);

@SqlUpdate("UPDATE characters SET equipped_title = :titleId WHERE id = :characterId")
void setEquippedTitle(@Bind("characterId") long characterId, @Bind("titleId") String titleId);
```

## 6. `AchievementDao` — two additions

```java
/**
 * Every achievement's unlock status as this character's page should show it (system design §22): an
 * ACCOUNT-scope achievement counts if the account has ever unlocked it; a CHARACTER-scope achievement
 * counts only if *this* character unlocked it — the same OR-by-scope join {@code AchievementUnlockService}
 * uses to decide which partial index applies, read instead of written here.
 */
@RegisterConstructorMapper(CharacterPageAchievement.class)
@SqlQuery("SELECT ad.key_name AS keyName, lk_t.key_name AS titleLocKey, lk_d.key_name AS descLocKey, " +
    "ad.points AS points, ad.badge_id AS badgeId, ad.hidden AS hidden, ad.category AS category, " +
    "(au.id IS NOT NULL) AS isUnlocked, au.unlocked_at AS unlockedAt " +
    "FROM achievement_definitions ad " +
    "JOIN localization_keys lk_t ON ad.title_loc_key = lk_t.id " +
    "JOIN localization_keys lk_d ON ad.desc_loc_key = lk_d.id " +
    "LEFT JOIN achievement_unlocks au ON ad.id = au.achievement_id AND au.account_id = :accountId " +
    "  AND ((ad.scope = 'ACCOUNT' AND au.character_id IS NULL) OR (ad.scope = 'CHARACTER' AND au.character_id = :characterId)) " +
    "ORDER BY ad.id")
List<CharacterPageAchievement> getAchievementsForCharacterPage(@Bind("accountId") long accountId,
                                                                @Bind("characterId") long characterId);

/** Whether {@code titleId} is unlocked for this account/character (system design §22) — S4's write-gate. */
@SqlQuery("SELECT EXISTS (SELECT 1 FROM achievement_definitions ad JOIN achievement_unlocks au ON ad.id = au.achievement_id " +
    "WHERE ad.title_id = :titleId AND au.account_id = :accountId " +
    "AND ((ad.scope = 'ACCOUNT' AND au.character_id IS NULL) OR (ad.scope = 'CHARACTER' AND au.character_id = :characterId)))")
boolean isTitleUnlockedFor(@Bind("titleId") String titleId, @Bind("accountId") long accountId,
                           @Bind("characterId") long characterId);
```

## 7. `MessageCode` — one addition

```java
// Character page / title equip (system design §22)
TITLE_NOT_UNLOCKED
```

## 8. `CharacterPageService` — new file, `server/service`

Same "odd one out, takes the raw `Jdbi`" shape as `LadderService` — a page assembly reads across three tables, and the title-equip write is a single-table update but still benefits from the same handle-attach convention for consistency.

```java
public class CharacterPageService {

    private static final Logger log = new Logger("CharacterPageService", Logger.DEBUG);
    private static final int RECENT_MATCH_LIMIT = 5;
    private static final String UNKNOWN_OPPONENT_PLACEHOLDER = "Unknown Challenger";

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public CharacterPageService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    public ServiceResult<CharacterPage> getCharacterPage(long accountId, long characterId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Character page rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            ServiceResult<Character> character = characterService.getCharacter(characterId);
            if (!character.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            CharacterPage page = jdbi.withHandle(handle -> {
                BattleHistoryDao battleHistoryDao = handle.attach(BattleHistoryDao.class);
                AchievementDao achievementDao = handle.attach(AchievementDao.class);
                CharacterDao characterDao = handle.attach(CharacterDao.class);

                int totalWins = battleHistoryDao.countTotalWins(characterId);
                int totalBattles = battleHistoryDao.countTotalBattles(characterId);
                int totalLosses = totalBattles - totalWins;
                double winPercentage = totalBattles == 0 ? 0.0 : (totalWins * 100.0) / totalBattles;
                int currentStreak = RewardService.currentStreak(
                    battleHistoryDao.findRecentOutcomes(characterId, 50));
                Integer fastestWinTurns = battleHistoryDao.findFastestWinTurnCount(characterId).orElse(null);

                List<RecentMatch> recentMatches = battleHistoryDao.findRecentMatches(characterId, RECENT_MATCH_LIMIT)
                    .stream()
                    .map(row -> new RecentMatch(row.won(), row.battleMode(), row.eloDelta(), row.rankedEloDelta(),
                        row.turnCount(), row.opponentName() != null ? row.opponentName() : UNKNOWN_OPPONENT_PLACEHOLDER,
                        row.occurredAt()))
                    .toList();

                CharacterStatistics statistics = new CharacterStatistics(
                    totalWins, totalLosses, winPercentage, currentStreak, fastestWinTurns, recentMatches);

                List<CharacterPageAchievement> achievements =
                    achievementDao.getAchievementsForCharacterPage(accountId, characterId);
                int achievementScore = achievements.stream()
                    .filter(CharacterPageAchievement::isUnlocked)
                    .mapToInt(CharacterPageAchievement::points)
                    .sum();

                String equippedTitle = characterDao.getEquippedTitle(characterId).orElse(null);

                return new CharacterPage(character.data(), statistics, achievements, achievementScore, equippedTitle);
            });

            return ServiceResult.success(MessageCode.SUCCESS, page);
        } catch (Exception e) {
            log.error("Character page assembly failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * {@code titleId == null} always succeeds (clearing the title never needs an unlock check). A non-null
     * {@code titleId} must be unlocked for this account/character (system design §22) — the same
     * account-vs-character OR-by-scope rule {@link AchievementUnlockService} writes against, read here.
     */
    public ServiceResult<String> setEquippedTitle(long accountId, long characterId, String titleId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Title equip rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            jdbi.useHandle(handle -> {
                if (titleId != null) {
                    boolean unlocked = handle.attach(AchievementDao.class)
                        .isTitleUnlockedFor(titleId, accountId, characterId);
                    if (!unlocked) {
                        throw new TitleNotUnlockedException();
                    }
                }
                handle.attach(CharacterDao.class).setEquippedTitle(characterId, titleId);
            });

            log.info("Character " + characterId + " equipped title: " + titleId);
            return ServiceResult.success(MessageCode.SUCCESS, titleId);
        } catch (TitleNotUnlockedException e) {
            log.info("Title equip rejected for character " + characterId + ": '" + titleId + "' is not unlocked.");
            return ServiceResult.failure(MessageCode.TITLE_NOT_UNLOCKED);
        } catch (Exception e) {
            log.error("Title equip failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** Internal control-flow signal only, to unwind out of the {@code useHandle} lambda with the right code. */
    private static final class TitleNotUnlockedException extends RuntimeException {
    }
}
```

`RewardService.currentStreak` is package-private static — reachable here because both classes live in `server.service`. Reusing it (rather than re-deriving the same leading-run logic) means the character page's streak can never silently drift from the number an achievement unlock actually saw.

## 9. Handlers — two new files, `server/network/handler`

Mirror `LadderStatusRequestHandler` exactly (unauthenticated/not-owned connections are logged and dropped, not answered):

```java
public class CharacterPageRequestHandler implements RequestHandler<CharacterPageRequest> {
    private static final Logger log = new Logger("CharacterPageRequestHandler", Logger.DEBUG);
    private final CharacterPageService characterPageService;
    private final CharacterService characterService;

    public CharacterPageRequestHandler(CharacterPageService characterPageService, CharacterService characterService) {
        this.characterPageService = characterPageService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, CharacterPageRequest request) {
        if (connection.account == null) {
            log.info("Rejected character page request from unauthenticated Connection ID: " + connection.getID());
            return;
        }
        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected character page: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id());
            return;
        }

        ServiceResult<CharacterPage> result =
            characterPageService.getCharacterPage(connection.account.id(), request.characterId());
        connection.sendTCP(result.success()
            ? new CharacterPageResponse(true, result.code().name(), result.data())
            : new CharacterPageResponse(false, result.code().name(), null));
    }
}
```

```java
public class SetEquippedTitleRequestHandler implements RequestHandler<SetEquippedTitleRequest> {
    private static final Logger log = new Logger("SetEquippedTitleRequestHandler", Logger.DEBUG);
    private final CharacterPageService characterPageService;
    private final CharacterService characterService;

    public SetEquippedTitleRequestHandler(CharacterPageService characterPageService, CharacterService characterService) {
        this.characterPageService = characterPageService;
        this.characterService = characterService;
    }

    @Override
    public void handle(GameConnection connection, SetEquippedTitleRequest request) {
        if (connection.account == null) {
            log.info("Rejected set-title request from unauthenticated Connection ID: " + connection.getID());
            return;
        }
        if (!characterService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected set-title: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id());
            return;
        }

        ServiceResult<String> result = characterPageService.setEquippedTitle(
            connection.account.id(), request.characterId(), request.titleId());
        connection.sendTCP(new SetEquippedTitleResponse(result.success(), result.code().name(),
            result.success() ? result.data() : null));
    }
}
```

## 10. Wiring

**`ServiceRegistry`** — add a field, construct it near `ladderService` (same dependency shape), add a getter:

```java
this.characterPageService = new CharacterPageService(dbManager.getJdbi(), characterService);
```

**`KryoPacketRouter`** — one more constructor parameter (`CharacterPageService characterPageService`), two more `handlers.put(...)` lines:

```java
handlers.put(CharacterPageRequest.class, new CharacterPageRequestHandler(characterPageService, characterService));
handlers.put(SetEquippedTitleRequest.class, new SetEquippedTitleRequestHandler(characterPageService, characterService));
```

**`KryoPacketRouterFactory`** — pass `serviceRegistry.getCharacterPageService()` through in the same position.

**`KryoConfig`** — append at the very end (after `ClaimLadderRewardResponse`, system design §5, append-only):

```java
kryo.register(RecentMatch.class, new RecordSerializer<>(RecentMatch.class));
kryo.register(CharacterStatistics.class, new RecordSerializer<>(CharacterStatistics.class));
kryo.register(CharacterPageAchievement.class, new RecordSerializer<>(CharacterPageAchievement.class));
kryo.register(CharacterPage.class, new RecordSerializer<>(CharacterPage.class));
kryo.register(CharacterPageRequest.class, new RecordSerializer<>(CharacterPageRequest.class));
kryo.register(CharacterPageResponse.class, new RecordSerializer<>(CharacterPageResponse.class));
kryo.register(SetEquippedTitleRequest.class, new RecordSerializer<>(SetEquippedTitleRequest.class));
kryo.register(SetEquippedTitleResponse.class, new RecordSerializer<>(SetEquippedTitleResponse.class));
```

Models before packets — same convention `LadderStatus`/`QuestProgress` already follow, since a model rides inside a response.

## 11. Tests

**`TestDatabase`**: add `equipped_title VARCHAR(50)` to the `characters` H2 table; add `equippedTitleOf(characterId)` (mirrors `maxSlotsOf`); extend `withAchievementDefinition` (or add an overload) to also accept `badgeId`/`titleId` so tests can seed a title-granting achievement.

**New `CharacterPageServiceTest`** (real `TestDatabase`):
- A character with no battles: `totalWins`/`totalLosses` both 0, `winPercentage` 0.0 (not `NaN`/divide-by-zero), `fastestWinTurns` null, `recentMatches` empty, `equippedTitle` null.
- Seed a few `battle_history` rows (mix of wins/losses, one against a real named opponent, one bot — `opponentCharacterId = null`); confirm `recentMatches` is newest-first, capped at 5, and the bot row's `opponentName` is the placeholder, not null.
- Seed one unlocked and one locked achievement; confirm `achievementScore` sums only the unlocked one's `points`, and both appear in `achievements` with the right `isUnlocked`.
- A page request for a character not owned by the caller's account fails (`ERROR_UNKNOWN`), no data leaked.

**Equip-title cases** (same test class or a new one):
- Equipping an unlocked title succeeds and `getEquippedTitle` reflects it afterward.
- Equipping a title with no matching unlock fails with `TITLE_NOT_UNLOCKED`, and the column is unchanged.
- Equipping `null` always succeeds (clears the column) regardless of unlock state.
- An ACCOUNT-scope achievement's title is honored for *any* character on the account, not just the one that happened to trigger the unlock (since the unlock write for an ACCOUNT-scope achievement carries `character_id = NULL`) — worth one explicit test, since it's the detail most likely to get miscoded as "only the unlocking character can wear it."

## Testing

Run `gradlew.bat server:test` (or `gradlew.bat build`). Confirm the new tests pass and nothing in `AchievementDao`/`BattleHistoryDao`/`CharacterDao`'s existing callers broke.

## Definition of done

- A character page assembles live stats, last-5 match history, unlocked achievements + score, and equipped title from a single request — no new persistence beyond `equipped_title`.
- A player can equip any title their account or character has actually unlocked, and clear it; an unowned title is rejected.
- Bot opponents in match history remain indistinguishable from real ones (fixed placeholder name, not a tell).
- `gradlew.bat build` is green.
- This closes out Epic S. Epic T (character customization, system design §23) is the next and final epic in the a–k expansion.
