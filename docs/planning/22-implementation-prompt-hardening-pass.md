# Implementation prompt — Backend hardening pass (review §2/§3)

Paste this whole file to Claude Code as the task.

## Context

Follows `docs/planning/21-backend-architecture-review.md`, a code-grounded audit of the server/common backend done ahead of M4. This prompt implements every finding in that review's §2 (bugs) and three of its four §3 gaps (3.1 index, 3.2 handler-test coverage, 3.3 i18n content). **§3.4 (swapping `com.badlogic.gdx.utils.Logger` for a real structured-logging library) is deliberately excluded** — it's a bigger, separate architectural decision (choosing a library, a config mechanism for log levels, updating every one of 38 call sites) that deserves its own dedicated prompt later, not a bolt-on to this one. §5's AOP-style helper suggestions are also excluded — those are a gradual refactor best done alongside future feature work touching those files, not a standalone pass.

Migrations: the last one on disk is `V17__Add_Character_Appearance.sql` (Epic T1), so this pass uses V18-V20.

## 1. Two handlers missing the auth guard (review §2.1)

`server/src/main/java/io/github/ydhekim/crimson_sky/server/network/handler/AchievementListRequestHandler.java` and `.../SaveAccountSettingsRequestHandler.java` both dereference `connection.account.id()` with no preceding null check — every other data-accessing handler in the package has one. Add the same guard, matching the exact shape used everywhere else (e.g. `LadderStatusRequestHandler`):

```java
@Override
public void handle(GameConnection connection, AchievementListRequest request) {
    if (connection.account == null) {
        log.info("Rejected achievement list request from unauthenticated Connection ID: " + connection.getID());
        return;
    }

    log.info("Received achievement list request from Connection ID: " + connection.getID());
    var result = achievementService.getPlayerAchievements(connection.account.id());
    // ...unchanged...
}
```

Same pattern for `SaveAccountSettingsRequestHandler`:

```java
@Override
public void handle(GameConnection connection, SaveAccountSettingsRequest request) {
    if (connection.account == null) {
        log.info("Rejected save-account-settings request from unauthenticated Connection ID: " + connection.getID());
        return;
    }

    long accountId = connection.account.id();
    // ...unchanged...
}
```

**Also fix `AchievementListRequestHandler`'s copy-pasted logger name** — it currently constructs `new Logger("LocalizationRequestHandler", Logger.DEBUG)`. Change the string literal to `"AchievementListRequestHandler"`.

### Tests

Both handlers get a new test file (there's no existing `AchievementListRequestHandlerTest`/`SaveAccountSettingsRequestHandlerTest` — this is their first coverage). Use `FakeGameConnection.unauthenticated(id)` (`server/support/FakeGameConnection.java`) and construct each handler with a **`null` service dependency** — deliberately: if the auth guard is ever removed again, the handler will NPE trying to call a method on `null` instead of silently passing, so the test fails loudly rather than passing vacuously.

```java
class AchievementListRequestHandlerTest {
    @Test
    void anUnauthenticatedConnectionGetsNoResponse() {
        var connection = FakeGameConnection.unauthenticated(1);
        var handler = new AchievementListRequestHandler(null);

        handler.handle(connection, new AchievementListRequest());

        assertTrue(connection.sentNothing());
    }
}
```

```java
class SaveAccountSettingsRequestHandlerTest {
    @Test
    void anUnauthenticatedConnectionGetsNoResponse() {
        var connection = FakeGameConnection.unauthenticated(1);
        var handler = new SaveAccountSettingsRequestHandler(null);

        handler.handle(connection, new SaveAccountSettingsRequest(AccountSettings.createDefault()));

        assertTrue(connection.sentNothing());
    }
}
```

Adjust constructor args to whatever `AchievementListRequest`/`SaveAccountSettingsRequest` actually require (check the record definitions — `AccountSettings.createDefault()` mirrors how `UserDao.createUserAndAccount` already builds one). This closes review §3.2 for these two handlers specifically; a broader sweep adding the same test to the other 19 handlers is optional future work, not required here.

## 2. `battle_history.opponent_character_id`'s cascade (review §2.2)

`V18__Fix_Battle_History_Opponent_Cascade.sql`:

```sql
-- A battle_history row is owned by character_id (the attacker); opponent_character_id is a secondary
-- reference to whoever they fought. It was declared ON DELETE CASCADE (V8), which deletes THIS row — the
-- attacker's own history — when the OPPONENT's character is later deleted, silently corrupting a
-- completely different player's live-computed win count/achievements/ranked standing (system design
-- §19/§20/§21/§22 all read this table live). A deleted opponent should read the same as a bot opponent
-- (opponent_character_id NULL, opponent_is_bot already distinguishes the two) — not delete the row.
ALTER TABLE battle_history DROP CONSTRAINT battle_history_opponent_character_id_fkey;
ALTER TABLE battle_history ADD CONSTRAINT battle_history_opponent_character_id_fkey
    FOREIGN KEY (opponent_character_id) REFERENCES characters (id) ON DELETE SET NULL;
```

The constraint name is Postgres's deterministic auto-generated one for a single-column FK declared inline in `V8__Battle_History.sql` (`{table}_{column}_fkey`), the same naming convention already confirmed correct for the achievement unique constraint in `V15`.

### Test schema and coverage

`TestDatabase`'s H2 `battle_history` table declares `opponent_character_id INTEGER REFERENCES characters (id)` with **no** `ON DELETE` clause at all — it never modeled the cascade bug to begin with, so there's nothing to "un-break" there. Update it to explicitly match the now-fixed production behavior instead of leaving it unconstrained:

```java
+ "opponent_character_id INTEGER REFERENCES characters (id) ON DELETE SET NULL, "
```

Add a new test proving the fix, e.g. `server/src/test/java/.../database/dao/BattleHistoryDaoOpponentDeletionTest.java`:

```java
@Test
void deletingTheOpponentCharacterNullsTheColumnRatherThanDeletingTheRow() {
    // seed an attacker and an opponent, write one battle_history row via BattleHistoryDao.insert,
    // then delete the opponent character directly (CharacterDao.deleteCharacter or a raw DELETE),
    // then assert: battleHistoryDao row count is still 1, and its opponent_character_id reads NULL
    // (not that the row vanished).
}
```

## 3. Indexing `battle_history` (review §3.1)

`V19__Add_Battle_History_Performance_Indexes.sql`:

```sql
-- battle_history has no index beyond its own primary key today. Every quest check (§19), the daily battle
-- cap (§20), ranked Elo/rank (§21), and every achievement/character-page read (§22) filters this table by
-- character_id, several also ordering by created_at DESC — all currently full table scans. One composite
-- index covers both the plain character_id filters and the ORDER BY created_at DESC queries.
CREATE INDEX battle_history_character_id_created_at_idx ON battle_history (character_id, created_at DESC);

-- characters.account_id is read by every character-list/character-count lookup; far lower row-count risk
-- than battle_history, but equally uncovered by any existing index.
CREATE INDEX characters_account_id_idx ON characters (account_id);
```

No test needed for an index addition itself (it changes a query plan, not a result) — just confirm the existing full test suite still passes against the new migration (H2 doesn't need the equivalent index for correctness, only Postgres needs it for performance, so no `TestDatabase` schema change here either).

## 4. Seeding real translations for the `MessageCode` enum (review §3.3)

`V20__Seed_Message_Code_Localizations.sql` — the `ERROR` `group_type`, matching `CHAR_NAME_TAKEN`'s existing precedent from `V2__Localization_Setup.sql`. `CHAR_NAME_TAKEN` itself is already seeded; this covers every other current value (26 keys, `SUCCESS` and `ERROR_UNKNOWN` included so `LanguageManager.get(MessageCode.SUCCESS)` also resolves to real text rather than the `"!SUCCESS!"` fallback):

```sql
INSERT INTO localization_keys (key_name, group_type) VALUES
    ('SUCCESS', 'ERROR'), ('ERROR_UNKNOWN', 'ERROR'), ('CHAR_CREATE_SUCCESS', 'ERROR'),
    ('CHAR_MAX_SLOTS_REACHED', 'ERROR'), ('LOGIN_FAILED_INVALID_TOKEN', 'ERROR'),
    ('STAT_POINTS_INSUFFICIENT', 'ERROR'), ('STAT_CAP_EXCEEDED', 'ERROR'),
    ('SKILL_NODE_NOT_FOUND', 'ERROR'), ('SKILL_LEVEL_GATE_NOT_MET', 'ERROR'),
    ('SKILL_FACTION_MISMATCH', 'ERROR'), ('SKILL_RANK_MAXED', 'ERROR'),
    ('SKILL_POINTS_INSUFFICIENT', 'ERROR'), ('SKILL_GOLD_INSUFFICIENT', 'ERROR'),
    ('LOADOUT_ITEM_NOT_OWNED', 'ERROR'), ('LOADOUT_SKILL_SLOTS_EXCEEDED', 'ERROR'),
    ('LOADOUT_WEIGHT_EXCEEDED', 'ERROR'), ('SHOP_ITEM_NOT_FOUND', 'ERROR'),
    ('SHOP_NOTHING_TO_REPAIR', 'ERROR'), ('SHOP_GOLD_INSUFFICIENT', 'ERROR'),
    ('SHOP_TOKEN_INSUFFICIENT', 'ERROR'), ('QUEST_NOT_FOUND', 'ERROR'),
    ('QUEST_NOT_COMPLETE', 'ERROR'), ('QUEST_ALREADY_CLAIMED', 'ERROR'),
    ('QUEST_DAILY_CLAIM_CAP_REACHED', 'ERROR'), ('QUEST_INVALID_REWARD_CHOICE', 'ERROR'),
    ('DAILY_BATTLE_CAP_REACHED', 'ERROR'), ('RANKED_LEVEL_GATE_NOT_MET', 'ERROR'),
    ('LADDER_NOT_RANKED_ELIGIBLE', 'ERROR'), ('LADDER_NO_REWARD_THIS_RANK', 'ERROR'),
    ('LADDER_ALREADY_CLAIMED', 'ERROR'), ('TITLE_NOT_UNLOCKED', 'ERROR'),
    ('CHAR_INVALID_APPEARANCE', 'ERROR');

INSERT INTO localization_values (key_id, lang_code, text_value) VALUES
    ((SELECT id FROM localization_keys WHERE key_name = 'SUCCESS'), 'en_US', 'Success.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SUCCESS'), 'tr_TR', 'Başarılı.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'ERROR_UNKNOWN'), 'en_US', 'An unexpected error occurred. Please try again.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'ERROR_UNKNOWN'), 'tr_TR', 'Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_CREATE_SUCCESS'), 'en_US', 'Character created successfully.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_CREATE_SUCCESS'), 'tr_TR', 'Karakter başarıyla oluşturuldu.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_MAX_SLOTS_REACHED'), 'en_US', 'You have reached your maximum number of character slots.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_MAX_SLOTS_REACHED'), 'tr_TR', 'Maksimum karakter slot sayısına ulaştınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOGIN_FAILED_INVALID_TOKEN'), 'en_US', 'Login failed: invalid identity token.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOGIN_FAILED_INVALID_TOKEN'), 'tr_TR', 'Giriş başarısız: geçersiz kimlik belirteci.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'STAT_POINTS_INSUFFICIENT'), 'en_US', 'You don''t have enough stat points for that.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'STAT_POINTS_INSUFFICIENT'), 'tr_TR', 'Bunun için yeterli stat puanınız yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'STAT_CAP_EXCEEDED'), 'en_US', 'That would exceed the maximum value for this stat.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'STAT_CAP_EXCEEDED'), 'tr_TR', 'Bu, bu stat için izin verilen maksimum değeri aşar.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_NODE_NOT_FOUND'), 'en_US', 'That skill node could not be found.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_NODE_NOT_FOUND'), 'tr_TR', 'Bu yetenek düğümü bulunamadı.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_LEVEL_GATE_NOT_MET'), 'en_US', 'Your character level is too low to learn this skill.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_LEVEL_GATE_NOT_MET'), 'tr_TR', 'Bu yeteneği öğrenmek için karakter seviyeniz yetersiz.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_FACTION_MISMATCH'), 'en_US', 'This skill isn''t available to your faction.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_FACTION_MISMATCH'), 'tr_TR', 'Bu yetenek fraksiyonunuz için mevcut değil.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_RANK_MAXED'), 'en_US', 'This skill is already at its maximum rank.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_RANK_MAXED'), 'tr_TR', 'Bu yetenek zaten maksimum seviyede.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_POINTS_INSUFFICIENT'), 'en_US', 'You don''t have enough skill points for that.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_POINTS_INSUFFICIENT'), 'tr_TR', 'Bunun için yeterli yetenek puanınız yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_GOLD_INSUFFICIENT'), 'en_US', 'You don''t have enough gold for that.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SKILL_GOLD_INSUFFICIENT'), 'tr_TR', 'Bunun için yeterli altınınız yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_ITEM_NOT_OWNED'), 'en_US', 'You don''t own one of the items in that loadout.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_ITEM_NOT_OWNED'), 'tr_TR', 'Bu düzenlemedeki eşyalardan birine sahip değilsiniz.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_SKILL_SLOTS_EXCEEDED'), 'en_US', 'That loadout has too many skills equipped.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_SKILL_SLOTS_EXCEEDED'), 'tr_TR', 'Bu düzenleme çok fazla yetenek içeriyor.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_WEIGHT_EXCEEDED'), 'en_US', 'Those weapons are too heavy to carry together.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LOADOUT_WEIGHT_EXCEEDED'), 'tr_TR', 'Bu silahlar birlikte taşınamayacak kadar ağır.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_ITEM_NOT_FOUND'), 'en_US', 'That item is not available in the shop.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_ITEM_NOT_FOUND'), 'tr_TR', 'Bu eşya mağazada mevcut değil.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_NOTHING_TO_REPAIR'), 'en_US', 'There is nothing that needs repairing.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_NOTHING_TO_REPAIR'), 'tr_TR', 'Onarılması gereken bir şey yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_GOLD_INSUFFICIENT'), 'en_US', 'You don''t have enough gold for that purchase.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_GOLD_INSUFFICIENT'), 'tr_TR', 'Bu satın alma için yeterli altınınız yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_TOKEN_INSUFFICIENT'), 'en_US', 'You don''t have enough tokens for that.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'SHOP_TOKEN_INSUFFICIENT'), 'tr_TR', 'Bunun için yeterli jetonunuz yok.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_NOT_FOUND'), 'en_US', 'That quest could not be found.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_NOT_FOUND'), 'tr_TR', 'Bu görev bulunamadı.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_NOT_COMPLETE'), 'en_US', 'You haven''t completed this quest yet.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_NOT_COMPLETE'), 'tr_TR', 'Bu görevi henüz tamamlamadınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_ALREADY_CLAIMED'), 'en_US', 'You''ve already claimed this quest''s reward.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_ALREADY_CLAIMED'), 'tr_TR', 'Bu görevin ödülünü zaten aldınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_DAILY_CLAIM_CAP_REACHED'), 'en_US', 'You''ve reached today''s quest reward limit.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_DAILY_CLAIM_CAP_REACHED'), 'tr_TR', 'Bugünkü görev ödülü limitine ulaştınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_INVALID_REWARD_CHOICE'), 'en_US', 'That is not a valid reward choice for this quest.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'QUEST_INVALID_REWARD_CHOICE'), 'tr_TR', 'Bu, bu görev için geçerli bir ödül seçeneği değil.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'DAILY_BATTLE_CAP_REACHED'), 'en_US', 'You''ve reached today''s battle limit.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'DAILY_BATTLE_CAP_REACHED'), 'tr_TR', 'Bugünkü savaş limitine ulaştınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'RANKED_LEVEL_GATE_NOT_MET'), 'en_US', 'Your character level is too low for ranked battles.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'RANKED_LEVEL_GATE_NOT_MET'), 'tr_TR', 'Karakter seviyeniz derecelendirmeli savaşlar için yetersiz.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_NOT_RANKED_ELIGIBLE'), 'en_US', 'This character isn''t eligible for the ranked ladder yet.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_NOT_RANKED_ELIGIBLE'), 'tr_TR', 'Bu karakter henüz sıralama ligine uygun değil.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_NO_REWARD_THIS_RANK'), 'en_US', 'There''s no reward for this rank.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_NO_REWARD_THIS_RANK'), 'tr_TR', 'Bu sıralama için ödül bulunmuyor.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_ALREADY_CLAIMED'), 'en_US', 'You''ve already claimed this month''s ladder reward.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'LADDER_ALREADY_CLAIMED'), 'tr_TR', 'Bu ayın lig ödülünü zaten aldınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'TITLE_NOT_UNLOCKED'), 'en_US', 'You haven''t unlocked that title yet.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'TITLE_NOT_UNLOCKED'), 'tr_TR', 'Bu unvanın kilidini henüz açmadınız.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_INVALID_APPEARANCE'), 'en_US', 'That appearance combination isn''t available.'),
    ((SELECT id FROM localization_keys WHERE key_name = 'CHAR_INVALID_APPEARANCE'), 'tr_TR', 'Bu görünüm kombinasyonu mevcut değil.');
```

First-pass copy, same spirit as the achievement content mapping in the S1-S2 prompt — adjust individual wording freely; the goal is full enum coverage existing at all, not these exact words being final.

### Coverage test — the actual point of this section

A translation gap should fail a test, not get discovered in front of a player once M4 wires up error dialogs. New test `common/src/test/.../model/MessageCodeLocalizationCoverageTest.java` (or under `server/src/test` if `common` still has no test source set — check `settings.gradle`/existing test layout, mirroring wherever `AppearanceTest` landed for the same reason):

```java
/**
 * Guards against review §3.3 regressing: every MessageCode must have a seeded localization key, so
 * LanguageManager.get(MessageCode) never silently falls back to a "!CODE!" placeholder in front of a
 * player. Reads the migration SQL directly rather than needing a live Postgres — simple regex extraction
 * of every 'KEY_NAME' inserted into localization_keys across the two migrations that seed MessageCode
 * content (V2 for CHAR_NAME_TAKEN, V20 for everything else), diffed against MessageCode.values().
 */
class MessageCodeLocalizationCoverageTest {

    @Test
    void everyMessageCodeHasASeededLocalizationKey() throws IOException {
        Set<String> seededKeys = new HashSet<>();
        seededKeys.addAll(extractKeyNames("/db/migration/V2__Localization_Setup.sql"));
        seededKeys.addAll(extractKeyNames("/db/migration/V20__Seed_Message_Code_Localizations.sql"));

        List<String> missing = Arrays.stream(MessageCode.values())
            .map(Enum::name)
            .filter(name -> !seededKeys.contains(name))
            .toList();

        assertTrue(missing.isEmpty(), "MessageCode values with no seeded localization: " + missing);
    }

    private static Set<String> extractKeyNames(String classpathResource) throws IOException {
        String sql = new String(getClass().getResourceAsStream(classpathResource).readAllBytes());
        Matcher matcher = Pattern.compile("'([A-Z_]+)',\\s*'(?:ERROR|UI|ACHIEVEMENT)'").matcher(sql);
        Set<String> keys = new HashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
```

The regex is intentionally simple (matches this migration-file style specifically, not general SQL parsing) — good enough to enforce the convention going forward, not a general-purpose migration parser. If a future `MessageCode` value is added without a matching localization row, this test fails immediately instead of the gap being silently carried forward like it was from V2 to V17.

## Testing

Run `gradlew.bat build`. Confirm:
- Both new handler tests pass, and fail if you temporarily remove either auth guard (a quick sanity check worth doing once, not part of the permanent suite).
- `BattleHistoryDaoOpponentDeletionTest` passes.
- `MessageCodeLocalizationCoverageTest` passes with zero missing codes.
- Existing suite (particularly anything touching `battle_history` or `characters`) still passes against the new indexes/FK behavior.

## Definition of done

- §2.1, §2.2, §2.3 (the raw-token-in-logs line in `UserService.java:66` — just drop `" with token " + identityToken` from that one log line, no test needed for a log-message edit) are fixed.
- §3.1's two indexes exist.
- §3.2 is closed for the two handlers this prompt touches.
- §3.3's coverage test passes and stays green as a permanent regression guard.
- §3.4 (structured logging) and §5 (AOP-style shared helpers) remain explicitly deferred, not silently dropped — worth their own future prompts.
