package io.github.ydhekim.crimson_sky.server.database.dao;

import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins review §2.2's fix: {@code battle_history.opponent_character_id} is ON DELETE SET NULL, not CASCADE.
 * Deleting the opponent's character must leave the attacker's own history row intact (its win count,
 * achievements and ranked standing are all computed live off this table — §19/§20/§21/§22), reading the
 * opponent back as NULL exactly like a bot fight, never deleting the row.
 */
class BattleHistoryDaoOpponentDeletionTest {

    private static final long ACCOUNT = 10L;
    private static final long ATTACKER = 1L;
    private static final long OPPONENT = 2L;
    private static final String EMPTY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private BattleHistoryDao battleHistoryDao;
    private CharacterDao characterDao;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        db = TestDatabase.create()
            .withAccount(ACCOUNT, 0L)
            .withCharacter(ATTACKER, ACCOUNT, "Ayla", 0L, 1000, EMPTY, EMPTY)
            .withCharacter(OPPONENT, ACCOUNT, "Boran", 0L, 1000, EMPTY, EMPTY);
        battleHistoryDao = db.jdbi().onDemand(BattleHistoryDao.class);
        characterDao = db.jdbi().onDemand(CharacterDao.class);
    }

    @Test
    void deletingTheOpponentCharacterNullsTheColumnRatherThanDeletingTheRow() {
        battleHistoryDao.insert(ATTACKER, OPPONENT, false, true, 10, 5, 3, "NORMAL", null, 4);

        characterDao.deleteCharacter(ACCOUNT, "Boran");

        assertEquals(1, db.battleHistoryRowCount(), "the attacker's own row must survive the opponent's deletion");
        assertNull(db.onlyBattleHistoryRow().opponentCharacterId(),
            "the deleted opponent should read as NULL, like a bot fight — not delete the row");
    }
}
