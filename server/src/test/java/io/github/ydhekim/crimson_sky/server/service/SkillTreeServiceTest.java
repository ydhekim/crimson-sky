package io.github.ydhekim.crimson_sky.server.service;

import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.server.service.SkillTreeService.LearnSkillNodeResult;
import io.github.ydhekim.crimson_sky.server.support.CombatFixtures;
import io.github.ydhekim.crimson_sky.server.support.FakeCharacterDao;
import io.github.ydhekim.crimson_sky.server.support.HeadlessGdx;
import io.github.ydhekim.crimson_sky.server.support.TestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * System design §16 — learning/upgrading a skill-tree node. Like {@code RewardServiceTest}, this runs
 * the writes against a real (in-memory) database, since "the skill-point, gold, tree and inventory
 * writes commit or roll back together" is a property only a real transaction can demonstrate. Character
 * reads (level/faction/ownership) come from {@link FakeCharacterDao}; the balances/tree/inventory the
 * service actually mutates live in the database, seeded consistently by {@link #seed}.
 */
class SkillTreeServiceTest {

    private static final long ACCOUNT = 10L;
    private static final long OTHER_ACCOUNT = 20L;
    private static final long CHARACTER = 1L;
    private static final int ELO = 1000;
    private static final String EMPTY_INVENTORY = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";
    private static final String EMPTY_LOADOUT = "{\"weapons\":[],\"skills\":[],\"pets\":[]}";

    private TestDatabase db;
    private FakeCharacterDao characterDao;
    private SkillTreeService service;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        characterDao = new FakeCharacterDao();
        db = TestDatabase.create();
    }

    /** Seeds a character at {@code level} in both views, plus its account, skill-point balance, and gold. */
    private void seed(int level, int skillPoints, long gold, String inventoryJson) {
        characterDao.with(CombatFixtures.characterAtLevel(CHARACTER, ACCOUNT, "Ayla", level, 0L), ACCOUNT, ELO);
        db.withAccount(ACCOUNT, gold)
            .withCharacter(CHARACTER, ACCOUNT, "Ayla", level, 0L, ELO, inventoryJson, EMPTY_LOADOUT)
            .withSkillPoints(CHARACTER, skillPoints);
        service = new SkillTreeService(db.jdbi(), new CharacterService(characterDao));
    }

    // --- Rejections --------------------------------------------------------------------------------

    @Test
    void rejectsACharacterTheAccountDoesNotOwn() {
        seed(50, 100, 10_000L, EMPTY_INVENTORY);
        var result = service.learnOrUpgrade(OTHER_ACCOUNT, CHARACTER, "physical.t1.n1");
        assertFalse(result.success());
        assertEquals(MessageCode.ERROR_UNKNOWN, result.code());
    }

    @Test
    void rejectsAnUnknownNode() {
        seed(50, 100, 10_000L, EMPTY_INVENTORY);
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "does.not.exist");
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_NODE_NOT_FOUND, result.code());
    }

    @Test
    void rejectsBelowTheLevelGate() {
        seed(5, 100, 10_000L, EMPTY_INVENTORY); // physical.t2.n1 gates at level 20
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t2.n1");
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_LEVEL_GATE_NOT_MET, result.code());
    }

    @Test
    void rejectsAFactionMismatchOnAFactionNode() {
        // CombatFixtures characters are Faction.A (Crimson); the Skyborn node requires Faction.B.
        seed(50, 100, 10_000L, EMPTY_INVENTORY);
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "faction.skyborn.n1");
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_FACTION_MISMATCH, result.code());
    }

    @Test
    void rejectsInsufficientSkillPoints() {
        seed(50, 0 /* no skill points */, 10_000L, EMPTY_INVENTORY);
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1"); // costs 1 SP
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_POINTS_INSUFFICIENT, result.code());
    }

    @Test
    void rejectsInsufficientGold() {
        seed(50, 100, 0L /* no gold */, EMPTY_INVENTORY);
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1"); // costs 10 gold
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_GOLD_INSUFFICIENT, result.code());
    }

    @Test
    void rejectsAFourthRankAttempt() {
        seed(50, 100, 10_000L, EMPTY_INVENTORY);
        db.withSkillTree(CHARACTER, "{\"physical.t1.n1\":3}"); // already maxed
        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1");
        assertFalse(result.success());
        assertEquals(MessageCode.SKILL_RANK_MAXED, result.code());
    }

    // --- Success -----------------------------------------------------------------------------------

    @Test
    void aRankOneLearnGrantsTheSkillAndDebitsBothCurrencies() {
        seed(50, 5, 100L, EMPTY_INVENTORY);

        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1"); // Iron Grip, STR +2/rank

        assertTrue(result.success());
        LearnSkillNodeResult data = result.data();
        assertEquals(1, data.newRank());
        assertEquals(1000L, data.node().id(), "the node's stable skill id");
        assertEquals(2, data.node().passiveMagnitude(), "rank 1 magnitude is 2 × 1");
        assertEquals(4, data.remainingSkillPoints(), "5 − 1 SP");
        assertEquals(90L, data.remainingGold(), "100 − 10 gold");

        assertEquals(4, db.skillPointsOf(CHARACTER));
        assertEquals(90L, db.goldOf(ACCOUNT));
        assertTrue(db.skillTreeJsonOf(CHARACTER).contains("\"physical.t1.n1\":1"), "the rank persists");
        String inventory = db.inventoryJsonOf(CHARACTER);
        assertTrue(inventory.contains("\"id\":1000"), "the granted skill is written into inventory");
        assertTrue(inventory.contains("\"passiveMagnitude\":2"), "at its rank-1 magnitude");
    }

    @Test
    void anUpgradeReplacesTheSameIdWithTheNewRankInPlace() {
        String inventoryWithRank1 = "{\"weapons\":[],\"skills\":["
            + "{\"id\":1000,\"name\":\"Iron Grip\",\"type\":\"PASSIVE\",\"passiveMagnitude\":2}],\"pets\":[]}";
        seed(50, 5, 100L, inventoryWithRank1);
        db.withSkillTree(CHARACTER, "{\"physical.t1.n1\":1}");

        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1");

        assertTrue(result.success());
        assertEquals(2, result.data().newRank());
        assertEquals(4, result.data().node().passiveMagnitude(), "rank 2 magnitude is 2 × 2");

        assertTrue(db.skillTreeJsonOf(CHARACTER).contains("\"physical.t1.n1\":2"));
        String inventory = db.inventoryJsonOf(CHARACTER);
        // Same id replaced in place — not appended: still exactly one occurrence of the node's skill id.
        assertEquals(1, occurrences(inventory, "\"id\":1000"), "the upgrade replaces, it does not append");
        assertTrue(inventory.contains("\"passiveMagnitude\":4"), "the entry now carries the rank-2 magnitude");
        assertFalse(inventory.contains("\"passiveMagnitude\":2"), "the old rank-1 magnitude is gone");
    }

    @Test
    void aMidTransactionFailureCommitsNothing() {
        // Invalid inventory JSON makes getInventoryForUpdate throw — after skill points, gold, and the
        // tree rank have already been written inside the transaction. Everything must roll back together.
        seed(50, 5, 100L, "not-valid-json");

        var result = service.learnOrUpgrade(ACCOUNT, CHARACTER, "physical.t1.n1");

        assertFalse(result.success(), "a failed grant reports failure");
        assertEquals(5, db.skillPointsOf(CHARACTER), "skill points rolled back");
        assertEquals(100L, db.goldOf(ACCOUNT), "gold rolled back");
        assertEquals("{}", db.skillTreeJsonOf(CHARACTER), "the tree rank rolled back");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
