package io.github.ydhekim.crimson_sky.server.service;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Inventory;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.database.dao.AccountDao;
import io.github.ydhekim.crimson_sky.server.database.dao.CharacterDao;
import org.jdbi.v3.core.Jdbi;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The gold-only shop (system design §18): weapon repair, pet-health restore, and the two catalog
 * purchases (skill-restoration scroll, skill-tree reset token). <b>Nothing here is ever sold for premium
 * currency</b> — IAP stays scoped to the account-level pacing levers, never to combat power.
 *
 * <p><b>Two payment paths from day one (§18).</b> Repair and pet-restore each accept a redeemable token
 * ({@code repair_token} / {@code pet_care_kit}) <i>in place of</i> gold. Nothing grants either yet — that
 * is the weekly-quest reward table (Epic P/O4) — but §18 locks the redemption path in now rather than
 * bolting it on later, so both paths are real and callable today.
 *
 * <p><b>Why the raw {@link Jdbi}, not {@code CharacterService}/{@code AccountService}:</b> identical
 * reasoning to {@code RewardService}/{@code SkillTreeService}. A repair or purchase spans
 * {@code characters.inventory} and (on the gold path) {@code accounts.global_currency}, and {@code onDemand}
 * DAO proxies take a new connection per call — so only DAOs attached to one {@link Jdbi#useTransaction}
 * handle commit or roll back together.
 *
 * <p><b>The row lock is the only guard a JSONB field gets.</b> {@link AccountDao#spendGlobalCurrency}'s
 * {@code WHERE global_currency >= :cost} is a real SQL guard against a concurrent gold race. A consumable
 * count embedded in {@code inventory} has no SQL-level equivalent: the only thing serializing two
 * concurrent repairs of the same character is {@link CharacterDao#getInventoryForUpdate}'s
 * {@code FOR UPDATE}. So the friendly pre-checks below (ownership, item exists, cost, balance) run against
 * a plain read for a clean rejection reason, but every decrement and repair re-reads its inputs from the
 * <i>locked</i> copy inside the transaction and acts on that. A disagreement means someone else's repair
 * landed first, and throws — reported as {@code ERROR_UNKNOWN} by the outer catch, the same treatment a
 * lost gold race gets in {@code SkillTreeService}.
 */
public class ShopService {

    private static final Logger log = new Logger("ShopService", Logger.DEBUG);

    /** Repair price, per missing point of durability/health (§18): a 0/20 weapon costs 100 gold to fix. */
    static final int REPAIR_GOLD_PER_POINT = 5;
    static final int SCROLL_GOLD_COST = 50;
    static final int RESET_TOKEN_GOLD_COST = 1000;

    // Consumable keys (§18). Plain string constants, not an enum: four ad-hoc keys in a `key → count` map
    // that nothing switches over, living in the same embedded JSONB as everything else this epic touched.
    static final String REPAIR_TOKEN = "repair_token";
    static final String PET_CARE_KIT = "pet_care_kit";
    static final String SKILL_RESTORATION_SCROLL = "skill_restoration_scroll";
    static final String SKILL_TREE_RESET_TOKEN = "skill_tree_reset_token";

    /** The repaired weapon plus the balances left after the spend — folded into a response by the handler. */
    public record RepairWeaponResult(Weapon weapon, long remainingGold, int remainingRepairTokens) {
    }

    public record RepairPetResult(Pet pet, long remainingGold, int remainingPetCareKits) {
    }

    /** A catalog purchase's outcome: the new count of what was bought, and the wallet after paying. */
    public record PurchaseResult(int newCount, long remainingGold) {
    }

    private final Jdbi jdbi;
    private final CharacterService characterService;

    public ShopService(Jdbi jdbi, CharacterService characterService) {
        this.jdbi = jdbi;
        this.characterService = characterService;
    }

    /**
     * Restores {@code weaponId} to its {@code maxDurability} for {@code characterId} on behalf of
     * {@code accountId}. Validation order, fail-closed throughout: ownership; the weapon is in the
     * character's inventory; it is actually damaged (a no-op repair is rejected rather than silently
     * charging 0 gold or burning a token for nothing); and the chosen currency covers it.
     */
    public ServiceResult<RepairWeaponResult> repairWeapon(long accountId, long characterId, long weaponId,
                                                          boolean useToken) {
        try {
            Inventory snapshot = ownedInventory(accountId, characterId, "weapon repair");
            if (snapshot == null) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            Weapon weapon = findWeapon(snapshot, weaponId);
            if (weapon == null) {
                return ServiceResult.failure(MessageCode.SHOP_ITEM_NOT_FOUND);
            }

            int missing = missingDurability(weapon);
            if (missing == 0) {
                return ServiceResult.failure(MessageCode.SHOP_NOTHING_TO_REPAIR);
            }

            Payment payment = priceRepair(accountId, useToken, missing, snapshot, REPAIR_TOKEN);
            if (payment.rejection() != null) {
                return ServiceResult.failure(payment.rejection());
            }

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                spendGold(handle.attach(AccountDao.class), accountId, payment.goldCost());

                Inventory locked = lockedInventory(characterDao, characterId);
                Array<Weapon> weapons = weaponsOf(locked);
                int index = indexOfWeapon(weapons, weaponId);
                if (index < 0) {
                    throw new IllegalStateException("weapon " + weaponId + " lost a race out of the inventory of character "
                        + characterId);
                }
                Map<String, Integer> consumables = consumablesOf(locked);
                if (useToken) {
                    redeem(consumables, REPAIR_TOKEN, characterId);
                }
                weapons.set(index, weapons.get(index).repaired());
                characterDao.updateInventory(characterId,
                    new Inventory(weapons, locked.skills(), locked.pets(), consumables));
            });

            log.info("Character " + characterId + " repaired weapon " + weaponId + " (" + missing
                + " points restored, " + (useToken ? "1 repair token" : payment.goldCost() + " gold") + ").");
            return ServiceResult.success(MessageCode.SUCCESS, new RepairWeaponResult(
                weapon.repaired(), payment.remainingGold(), payment.remainingTokens()));
        } catch (Exception e) {
            log.error("Weapon repair failed for character " + characterId + " / weapon " + weaponId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * Restores {@code petId} to its {@code healthPoint} — the pet-side mirror of {@link #repairWeapon},
     * deliberately identical in shape and validation order because the wear mechanic it undoes is (§18).
     */
    public ServiceResult<RepairPetResult> repairPet(long accountId, long characterId, long petId,
                                                    boolean useToken) {
        try {
            Inventory snapshot = ownedInventory(accountId, characterId, "pet repair");
            if (snapshot == null) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            Pet pet = findPet(snapshot, petId);
            if (pet == null) {
                return ServiceResult.failure(MessageCode.SHOP_ITEM_NOT_FOUND);
            }

            int missing = missingPetHealth(pet);
            if (missing == 0) {
                return ServiceResult.failure(MessageCode.SHOP_NOTHING_TO_REPAIR);
            }

            Payment payment = priceRepair(accountId, useToken, missing, snapshot, PET_CARE_KIT);
            if (payment.rejection() != null) {
                return ServiceResult.failure(payment.rejection());
            }

            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                spendGold(handle.attach(AccountDao.class), accountId, payment.goldCost());

                Inventory locked = lockedInventory(characterDao, characterId);
                Array<Pet> pets = petsOf(locked);
                int index = indexOfPet(pets, petId);
                if (index < 0) {
                    throw new IllegalStateException("pet " + petId + " lost a race out of the inventory of character "
                        + characterId);
                }
                Map<String, Integer> consumables = consumablesOf(locked);
                if (useToken) {
                    redeem(consumables, PET_CARE_KIT, characterId);
                }
                pets.set(index, pets.get(index).repaired());
                characterDao.updateInventory(characterId,
                    new Inventory(locked.weapons(), locked.skills(), pets, consumables));
            });

            log.info("Character " + characterId + " restored pet " + petId + " (" + missing
                + " points restored, " + (useToken ? "1 pet care kit" : payment.goldCost() + " gold") + ").");
            return ServiceResult.success(MessageCode.SUCCESS, new RepairPetResult(
                pet.repaired(), payment.remainingGold(), payment.remainingTokens()));
        } catch (Exception e) {
            log.error("Pet repair failed for character " + characterId + " / pet " + petId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /** Buys one skill-restoration scroll for a flat {@link #SCROLL_GOLD_COST} (§18). Gold only. */
    public ServiceResult<PurchaseResult> buyScroll(long accountId, long characterId) {
        return buy(accountId, characterId, SKILL_RESTORATION_SCROLL, SCROLL_GOLD_COST);
    }

    /** Buys one skill-tree reset token for a flat {@link #RESET_TOKEN_GOLD_COST} (§18). Gold only. */
    public ServiceResult<PurchaseResult> buyResetToken(long accountId, long characterId) {
        return buy(accountId, characterId, SKILL_TREE_RESET_TOKEN, RESET_TOKEN_GOLD_COST);
    }

    /**
     * The shared catalog-purchase path: no item lookup, just a guarded gold spend followed by a
     * {@code +1} on one consumables key, both inside one transaction.
     */
    private ServiceResult<PurchaseResult> buy(long accountId, long characterId, String key, long goldCost) {
        try {
            if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
                log.info("Shop purchase of " + key + " rejected: character " + characterId
                    + " not owned by account " + accountId);
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }

            Optional<Long> gold = jdbi.withHandle(h -> h.attach(AccountDao.class).getGlobalCurrency(accountId));
            if (gold.isEmpty()) {
                return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
            }
            if (gold.get() < goldCost) {
                return ServiceResult.failure(MessageCode.SHOP_GOLD_INSUFFICIENT);
            }

            int[] newCount = new int[1];
            jdbi.useTransaction(handle -> {
                CharacterDao characterDao = handle.attach(CharacterDao.class);
                spendGold(handle.attach(AccountDao.class), accountId, goldCost);

                Inventory locked = lockedInventory(characterDao, characterId);
                Map<String, Integer> consumables = consumablesOf(locked);
                newCount[0] = consumables.getOrDefault(key, 0) + 1;
                consumables.put(key, newCount[0]);
                characterDao.updateInventory(characterId,
                    new Inventory(locked.weapons(), locked.skills(), locked.pets(), consumables));
            });

            log.info("Character " + characterId + " bought 1 " + key + " for " + goldCost + " gold; now holds "
                + newCount[0] + ".");
            return ServiceResult.success(MessageCode.SUCCESS,
                new PurchaseResult(newCount[0], gold.get() - goldCost));
        } catch (Exception e) {
            log.error("Shop purchase of " + key + " failed for character " + characterId, e);
            return ServiceResult.failure(MessageCode.ERROR_UNKNOWN);
        }
    }

    /**
     * How a repair is being paid for, decided against the pre-check snapshot. {@code rejection} non-null
     * means the pre-check already said no and nothing should open a transaction; otherwise exactly one of
     * {@code goldCost} / the token path applies, and the two {@code remaining*} values are what the caller
     * reports back on success.
     */
    private record Payment(MessageCode rejection, long goldCost, long remainingGold, int remainingTokens) {

        static Payment rejected(MessageCode code) {
            return new Payment(code, 0L, 0L, 0);
        }
    }

    /**
     * Prices a repair on whichever path {@code useToken} selects, against the pre-check {@code snapshot}.
     * The counts read here are for a friendly rejection and the reported balances only — the authoritative
     * checks are {@code spendGlobalCurrency}'s {@code WHERE} clause and, for the token, the re-read under
     * the row lock (see this class's docstring).
     */
    private Payment priceRepair(long accountId, boolean useToken, int missing, Inventory snapshot,
                                String tokenKey) {
        Optional<Long> gold = jdbi.withHandle(h -> h.attach(AccountDao.class).getGlobalCurrency(accountId));
        if (gold.isEmpty()) {
            return Payment.rejected(MessageCode.ERROR_UNKNOWN);
        }

        int tokens = consumablesOf(snapshot).getOrDefault(tokenKey, 0);
        if (useToken) {
            if (tokens < 1) {
                return Payment.rejected(MessageCode.SHOP_TOKEN_INSUFFICIENT);
            }
            return new Payment(null, 0L, gold.get(), tokens - 1);
        }

        long cost = (long) REPAIR_GOLD_PER_POINT * missing;
        if (gold.get() < cost) {
            return Payment.rejected(MessageCode.SHOP_GOLD_INSUFFICIENT);
        }
        return new Payment(null, cost, gold.get() - cost, tokens);
    }

    /**
     * The character's current inventory snapshot, or {@code null} when {@code accountId} doesn't own it or
     * it can't be loaded — the ownership guardrail every character-scoped action runs, failing closed.
     */
    private Inventory ownedInventory(long accountId, long characterId, String action) {
        if (!characterService.isCharacterOwnedBy(accountId, characterId)) {
            log.info("Shop " + action + " rejected: character " + characterId + " not owned by account " + accountId);
            return null;
        }
        ServiceResult<Character> character = characterService.getCharacter(characterId);
        if (!character.success()) {
            return null;
        }
        Inventory inventory = character.data().inventory();
        return inventory != null ? inventory : new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>());
    }

    /** The row-locked inventory the transaction actually acts on, tolerating the legacy null-array shape. */
    private Inventory lockedInventory(CharacterDao characterDao, long characterId) {
        return characterDao.getInventoryForUpdate(characterId)
            .orElseGet(() -> new Inventory(new Array<>(), new Array<>(), new Array<>(), new HashMap<>()));
    }

    /** No-ops on the token path ({@code goldCost == 0}); otherwise the guarded spend, which must not lose. */
    private void spendGold(AccountDao accountDao, long accountId, long goldCost) {
        if (goldCost > 0 && accountDao.spendGlobalCurrency(accountId, goldCost) == 0) {
            throw new IllegalStateException("gold wallet lost a race for account " + accountId);
        }
    }

    /**
     * Decrements {@code key} by one, re-verifying against this (locked) copy rather than the pre-check's:
     * a count in JSONB has no {@code WHERE ... >= 1} to fall back on, so this read is the guard.
     */
    private void redeem(Map<String, Integer> consumables, String key, long characterId) {
        int held = consumables.getOrDefault(key, 0);
        if (held < 1) {
            throw new IllegalStateException(key + " balance lost a race for character " + characterId);
        }
        consumables.put(key, held - 1);
    }

    static int missingDurability(Weapon weapon) {
        return Math.max(0, weapon.maxDurability() - weapon.currentDurability());
    }

    static int missingPetHealth(Pet pet) {
        return Math.max(0, pet.healthPoint() - pet.currentHealth());
    }

    private static Weapon findWeapon(Inventory inventory, long weaponId) {
        Array<Weapon> weapons = weaponsOf(inventory);
        int index = indexOfWeapon(weapons, weaponId);
        return index < 0 ? null : weapons.get(index);
    }

    private static Pet findPet(Inventory inventory, long petId) {
        Array<Pet> pets = petsOf(inventory);
        int index = indexOfPet(pets, petId);
        return index < 0 ? null : pets.get(index);
    }

    private static int indexOfWeapon(Array<Weapon> weapons, long weaponId) {
        for (int i = 0; i < weapons.size; i++) {
            if (weapons.get(i).id() == weaponId) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfPet(Array<Pet> pets, long petId) {
        for (int i = 0; i < pets.size; i++) {
            if (pets.get(i).id() == petId) {
                return i;
            }
        }
        return -1;
    }

    private static Array<Weapon> weaponsOf(Inventory inventory) {
        return inventory.weapons() != null ? inventory.weapons() : new Array<>();
    }

    private static Array<Pet> petsOf(Inventory inventory) {
        return inventory.pets() != null ? inventory.pets() : new Array<>();
    }

    /**
     * A mutable copy of the consumables map. Never {@code null}: an inventory persisted before §18 has no
     * {@code consumables} key at all, and the first purchase against one must increment into an empty map
     * rather than NPE.
     */
    private static Map<String, Integer> consumablesOf(Inventory inventory) {
        return inventory.consumables() != null ? new HashMap<>(inventory.consumables()) : new HashMap<>();
    }
}
