package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.server.content.SkillTreeCatalog;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Learns or upgrades a skill-tree node (system design §16). "Learn" and "upgrade" are the same action —
 * whichever rank comes next.
 *
 * <p><b>Why the raw {@link Jdbi}, not the other services:</b> mirrors {@code RewardService}'s exact
 * justification. A learn/upgrade writes {@code characters.skill_points}, {@code characters.skill_tree},
 * {@code characters.inventory} <b>and</b> {@code accounts.global_currency} — two tables — in one atomic
 * step. {@code CharacterService}/{@code AccountService} wrap {@code onDemand} DAO proxies where every
 * call takes its own connection, so only DAOs attached to one {@link Jdbi#useTransaction} handle commit
 * or roll back together.
 *
 * <p>The current rank, skill-point balance, and gold wallet are read from the database (not the possibly
 * stale {@code Character} record) so they stay consistent with the writes; the guarded decrements are
 * the authoritative overspend/race protection, the same posture as the stat-point spend (§15).
 */
public class SkillTreeService {

    private static final Logger log = new Logger("SkillTreeService", Logger.DEBUG);

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public SkillTreeService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    /**
     * The granted {@link Skill} at its new rank plus the balances left after the spend — kept server-side
     * so the service stays free of network-packet types; the handler folds it into a
     * {@code LearnSkillNodeResponse}.
     */
    public record LearnSkillNodeResult(Skill node, int newRank, int remainingSkillPoints, long remainingGold) {
    }

    /**
     * Learns or upgrades {@code nodeId} for {@code characterId} on behalf of {@code accountId}. Validation
     * order: ownership (fail closed); node exists; rank not already maxed; level gate (or faction match for
     * a Faction node); sufficient skill points and gold for the <i>next</i> rank. On success, inside one
     * transaction: decrement skill points and gold (guarded), write the incremented skill-tree rank, and
     * grant-or-replace the node's {@link Skill} in inventory under a row lock.
     */
    public ServiceResult<LearnSkillNodeResult> learnOrUpgrade(long accountId, long characterId, String nodeId) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Skill-tree learn rejected: character " + characterId + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            SkillTreeCatalog.Node node = SkillTreeCatalog.find(nodeId);
            if (node == null) {
                return ServiceResult.failure(MessageCode.SKILL_NODE_NOT_FOUND);
            }

            ServiceResult<Character> characterResult = characterService.getCharacter(characterId);
            if (!characterResult.success()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            Character character = characterResult.data();

            // Level / faction gate.
            if (node.isFactionNode()) {
                if (character.faction() != node.requiredFaction()) {
                    return ServiceResult.failure(MessageCode.SKILL_FACTION_MISMATCH);
                }
            } else if (character.level() < node.levelGate()) {
                return ServiceResult.failure(MessageCode.SKILL_LEVEL_GATE_NOT_MET);
            }

            // Balances and current rank, read from the DB so they agree with the writes below.
            Map<String, Integer> skillTree = jdbi.withHandle(h ->
                h.attach(CharacterDao.class).getSkillTree(characterId)).orElseGet(HashMap::new);
            int currentRank = skillTree.getOrDefault(nodeId, 0);
            if (currentRank >= SkillTreeCatalog.MAX_RANK) {
                return ServiceResult.failure(MessageCode.SKILL_RANK_MAXED);
            }
            int nextRank = currentRank + 1;

            Optional<Integer> skillPoints = jdbi.withHandle(h ->
                h.attach(CharacterDao.class).getSkillPoints(characterId));
            Optional<Long> gold = jdbi.withHandle(h ->
                h.attach(AccountDao.class).getGlobalCurrency(accountId));
            if (skillPoints.isEmpty() || gold.isEmpty()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            int spCost = node.skillPointCostPerRank();
            long goldCost = node.goldCostPerRank();
            if (skillPoints.get() < spCost) {
                return ServiceResult.failure(MessageCode.SKILL_POINTS_INSUFFICIENT);
            }
            if (gold.get() < goldCost) {
                return ServiceResult.failure(MessageCode.SKILL_GOLD_INSUFFICIENT);
            }

            Skill granted = node.skillAtRank(nextRank);
            Map<String, Integer> newTree = new HashMap<>(skillTree);
            newTree.put(nodeId, nextRank);

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                AccountDao accountDao = handle.attach(AccountDao.class);

                if (characterDao.spendSkillPoints(characterId, spCost) == 0) {
                    throw new IllegalStateException("skill-point balance lost a race for character " + characterId);
                }
                if (accountDao.spendGlobalCurrency(accountId, goldCost) == 0) {
                    throw new IllegalStateException("gold wallet lost a race for account " + accountId);
                }
                characterDao.updateSkillTree(characterId, newTree);
                grantOrReplace(characterDao, characterId, granted);
            });

            log.info("Character " + characterId + " learned/upgraded " + nodeId + " to rank " + nextRank
                + " (-" + spCost + " SP, -" + goldCost + " gold).");
            return ServiceResult.success(MessageCode.SUCCESS,
                new LearnSkillNodeResult(granted, nextRank, skillPoints.get() - spCost, gold.get() - goldCost));
        } catch (Exception e) {
            log.error("Skill-tree learn/upgrade failed for character " + characterId + " / node " + nodeId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * Grants {@code granted} into the character's stored inventory, or replaces the existing entry with
     * the same node {@code id} (the upgrade case) — a read-modify-write under the same row lock as the
     * reward bonus grant (Epic L). Tolerates the legacy {@code null}-array inventory shape.
     */
    private void grantOrReplace(CharacterDao characterDao, long characterId, Skill granted) {
        Inventory inventory = characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>()));
        Array<Skill> skills = inventory.skills() != null ? inventory.skills() : new Array<>();

        int existingIndex = -1;
        for (int i = 0; i < skills.size; i++) {
            if (skills.get(i).id() == granted.id()) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            skills.set(existingIndex, granted); // upgrade replaces the same id's entry in place
        } else {
            skills.add(granted); // rank 1 — a fresh grant
        }
        characterDao.updateInventory(characterId,
            new Inventory(inventory.weapons(), skills, inventory.pets()));
    }
}
