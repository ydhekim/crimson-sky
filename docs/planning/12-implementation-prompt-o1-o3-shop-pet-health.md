# Claude Code prompt — O1, O3: shop (repair, scroll, reset token) & pet health

Copy everything below the line into Claude Code. Written 2026-07-16, fourth implementation slice of the M3.5 progression/economy expansion, building on Epics L, M and N (all merged).

---

Add a gold-only shop (weapon repair, skill-restoration-scroll purchase, skill-tree-reset-token purchase — all with a token-alternate-payment path built in from day one) and give pets the same wear-and-repair treatment N2 gave weapons. Read first: `docs/planning/01-system-design-combat-engine.md` §18. Also read, in full, the current `Weapon.java`, `Pet.java`, `Inventory.java`, `PetSlotComponent.java`, `PetResolver.java`, `BattleParticipant.java`, `CombatMath.java`, `RewardService.java`, `CharacterDao.java`, `AccountDao.java`, `SkillTreeService.java` (the closest sibling — same raw-`Jdbi` justification applies here), `ServiceRegistry.java`, `KryoPacketRouter.java`/`KryoPacketRouterFactory.java`, `KryoConfig.java`, and `BattleLeavesInventoryAloneTest.java`. You're extending most of these.

## Scope

Implement **O1** (shop: repair, scroll, reset token) and **O3** (pet health wear + repair). **O2 (potions/`CONSUMABLE` skill type) is deliberately not in this prompt** — it needs a self-targeting heal action that doesn't fit `BattleEngine`'s existing attacker-deals-damage-to-defender shape (a new pre-cascade conditional check, a new `ActionSource`, a different `ResolvedAction` meaning for "heal" vs "damage"). O1/O3 are, by contrast, close mirrors of already-shipped patterns — L1-L3's guarded-spend, N2's wear/durability shape — so bundling O2 in would drag a structurally novel, higher-risk change into an otherwise low-risk prompt. O2 is a clean follow-up once this merges. **O4 (token-earning via weekly quests) also isn't in scope** — quests don't exist yet (Epic P) — but per §18 and the O1 story text, the token-*redemption* path must exist now, not be bolted on later, so both `repairWeapon`/`repairPet` accept a token in place of gold from the start even though nothing can grant one yet.

## 0. Design notes — read before touching code

**No new migration.** Like durability, pet health and the consumable counts are embedded JSONB (`Inventory`), not relational schema (§18) — everything here is a `Weapon`/`Pet`/`Inventory` shape change plus the same `CharacterDao.getInventoryForUpdate`/`updateInventory` primitive N2 already established. No new `CharacterDao` method, no new C2 exception.

**Consumable counts live on `Inventory`, not a new table.** Add `Map<String, Integer> consumables` to `Inventory` (a key → count map, same shape as `Character.skillTree`). Use plain string keys matching §18's naming: `"repair_token"`, `"pet_care_kit"`, `"skill_restoration_scroll"`, `"skill_tree_reset_token"` — define them as `public static final String` constants on `ShopService` (§4 below) rather than inventing an enum for four ad-hoc keys nothing else needs to switch over.

**The row lock is the only guard a JSONB field gets — re-verify against the locked read, not the earlier pre-check.** `AccountDao.spendGlobalCurrency`'s `WHERE global_currency >= :cost` is a real SQL guard against a concurrent gold race. A `consumables` count has no SQL-level equivalent — the only thing serializing two concurrent repairs against the same character is `getInventoryForUpdate`'s `FOR UPDATE` row lock. So: do the friendly "can this even succeed" checks (ownership, item exists, cost, balance) against a plain (non-locked) read first — exactly `SkillTreeService`'s style, for a clean rejection reason — but the actual decrement/repair must re-read the token count and the target item from the **locked** `getInventoryForUpdate` result inside the transaction and act on that copy, not the pre-check's. If the locked re-read disagrees (someone else's repair landed first), throw, same as `SkillTreeService.learnOrUpgrade`'s `IllegalStateException("... lost a race ...")` — it's caught by the outer `catch (Exception e)` and reported as `ERROR_UNKNOWN`, the same treatment a lost gold race gets there.

**Why the raw `Jdbi`, not `CharacterService`/`AccountService`:** identical reasoning to `RewardService`/`SkillTreeService` — a repair or purchase spans `characters.inventory` and (on the gold path) `accounts.global_currency`, and `onDemand` DAO proxies take a new connection per call, so only DAOs attached to one `Jdbi#useTransaction` handle commit or roll back together.

## 1. `Inventory.consumables` — fix every call site

```java
public record Inventory(
    Array<Weapon> weapons,
    Array<Skill> skills,
    Array<Pet> pets,
    Map<String, Integer> consumables
) {}
```
This changes `Inventory`'s constructor arity. Fix every existing call site — `new Inventory(weapons, skills, pets)` becomes `new Inventory(weapons, skills, pets, <map>)`, using `new HashMap<>()` for a fresh/empty one and `new HashMap<>()` (never `null`) anywhere the old code passed `null` for the first three arrays too (match whatever null-tolerance convention that call site already used). Known sites, from a repo-wide grep for `new Inventory(`:
- `CombatFixtures.java` (×2), `CharacterServiceAllocateStatPointsTest.java`, `AllocateStatPointsRequestHandlerTest.java`, `SaveLoadoutRequestHandlerTest.java`, `BattleEngineTest.java`, `BattleParticipantDurabilityTest.java`, `BattleParticipantPassivesTest.java`, `CharacterServiceSaveLoadoutWeightTest.java` (×2) — test fixtures, all get an empty map.
- `SkillTreeService.java` (×2 — the "reload with empty inventory" default and the grant-or-replace write-back — both must carry the *existing* `consumables` map through unchanged, not reset it).
- `RewardService.java` (×2 — same "carry existing consumables through" requirement as `SkillTreeService`).
- `BotFactory.java` — a fresh empty map (a bot has no shop history).
- `CharacterCreationScreen.java` — the `new Inventory(null, null, null)` at character creation; make it `new Inventory(null, null, null, new HashMap<>())` (consistent with the rest of that record already tolerating `null` arrays but a fresh character needing a real, empty consumables map to increment into later — an absent/`null` map would NPE the first shop purchase).

Register `Inventory` with Jackson/Kryo exactly as before — no new registration needed for `Map<String, Integer>` itself (`Character.skillTree` already proves this shape round-trips through both Jackson's `@Json` and Kryo without any extra registration).

## 2. `Pet.currentHealth` — mirrors `Weapon.currentDurability` exactly

```java
public record Pet(
    long id, String name, String description, Tameness tameness,
    int healthPoint, int defence, int minAttack, int maxAttack,
    int currentHealth
) {
    /** A copy with currentHealth reduced by one, floored at 0 — one battle's wear (§18). */
    public Pet worn() {
        return new Pet(id, name, description, tameness, healthPoint, defence, minAttack, maxAttack,
            Math.max(0, currentHealth - 1));
    }

    /** A copy carrying `health` as its current value — the combat-time Inventory cross-reference. */
    public Pet withCurrentHealth(int health) {
        return new Pet(id, name, description, tameness, healthPoint, defence, minAttack, maxAttack, health);
    }

    /** A copy fully restored to `healthPoint` — the shop repair action (§18). */
    public Pet repaired() {
        return withCurrentHealth(healthPoint);
    }
}
```
Fix the four existing construction sites — `BotFactory`'s `HOUND`/`WOLF`/`BEAR` (append their own `healthPoint` value again as `currentHealth`, i.e. full health: `new Pet(2L, "Hound", "", Tameness.STUBBORN, 35, 5, 10, 20, 35)`) and `PouchResolutionTest`'s inline bear (same treatment, `80`). A fixture shipping at `0` would silently make every pet-resolution test's pet unusable — same caution N2's prompt called out for weapons.

Also add `Weapon.repaired()` (mirrors the new `Pet.repaired()` and the existing `worn()`):
```java
public Weapon repaired() {
    return new Weapon(id, name, description, rarity, weight, minAttack, maxAttack, staminaCost,
        maxDurability, maxDurability);
}
```

## 3. Pet health gates combat the same non-blocking way durability does

`CombatMath` gains:
```java
/** Pet-branch convenience: has this pet got any health left to act with? Mirrors Weapon's isAffordable. */
public static boolean isPetUsable(Pet pet) {
    return pet.currentHealth() > 0;
}
```
`PetResolver.choosePetAction` gains the guard, at the very top, right after the existing `pet == null` check: `if (!CombatMath.isPetUsable(pet)) { return null; }` — no gate roll consumed, same treatment as no pet at all. This is what makes "the pet's bonus action is simply skipped that battle" (§18) actually true.

`BattleParticipant.fromCharacter` needs the identical Inventory-cross-reference `atInventoryDurability` already does for weapons, now for the pet's health — add `atInventoryPetHealth(Pet equipped, Inventory inventory)` next to it (same shape: scan `inventory.pets()` for a matching id, return `equipped.withCurrentHealth(owned.currentHealth())`, else fall back to `equipped`'s own value). Replace:
```java
petSlot.equipped = pet;
petSlot.currentHealth = pet.healthPoint();
```
with:
```java
Pet crossReferenced = atInventoryPetHealth(pet, character.inventory());
petSlot.equipped = crossReferenced;
petSlot.currentHealth = crossReferenced.currentHealth();
```
`PetSlotComponent.currentHealth` stays exactly what it already was — a battle-scoped copy, never itself decremented in combat (nothing currently damages a pet mid-battle; that's still out of scope, per §18's explicit "a real pets-can-be-hurt-by-the-opponent mechanic is a separate question"). The only thing changing is what it's *seeded from*: the persisted, wear-tracked value instead of always-full `healthPoint()`.

## 4. Pet wear — extend `RewardService`'s existing transaction, not a new write path

Exactly like N2's weapon durability: a pet that acted at all this battle (appears in any `PET`-source `ResolvedAction` with a nonzero `itemId` — already populated uniformly since N2) loses 1 `currentHealth`, decided before the transaction opens, written in the same `getInventoryForUpdate`/`updateInventory` read-modify-write the weapon decrement and item grant already share.

```java
/** The attacker's equipped pet's id if it acted at least once this battle (§18) — mirrors firedWeaponIds; at most one id since a character carries a single pet. */
static Set<Long> firedPetId(AttackResult result) {
    Set<Long> ids = new LinkedHashSet<>();
    if (result.turns() == null) {
        return ids;
    }
    for (Array<ResolvedAction> turn : result.turns()) {
        for (ResolvedAction action : turn) {
            if (action.source() == ActionSource.PET && action.itemId() != 0L) {
                ids.add(action.itemId());
            }
        }
    }
    return ids;
}
```
Extend the existing `updateInventory(CharacterDao, long, Set<Long> firedWeaponIds, List<Weapon> bonusWeapons)` helper with a fourth parameter, `Set<Long> firedPetIds`, and a pets loop mirroring the weapons loop (`weapons.set(i, weapon.worn())` → `pets.set(i, pet.worn())`), folded into the same `changed` flag and the same single `updateInventory` call at the end — construct the final `Inventory` as `new Inventory(weapons, inventory.skills(), pets, inventory.consumables())` (consumables carried through untouched; this pass never mutates them). Update `applyRewards`'s call site to pass `firedPetId(result)` alongside the existing `firedWeaponIds(result)`, and fold the pet-wear condition into the existing `if (!bonusWeapons.isEmpty() || !firedWeaponIds.isEmpty())` guard (add `|| !firedPetIds.isEmpty()`).

## 5. `ShopService` — new, mirrors `SkillTreeService`'s shape

```java
public class ShopService {
    static final int REPAIR_GOLD_PER_POINT = 5;
    static final int SCROLL_GOLD_COST = 50;
    static final int RESET_TOKEN_GOLD_COST = 1000;

    static final String REPAIR_TOKEN = "repair_token";
    static final String PET_CARE_KIT = "pet_care_kit";
    static final String SKILL_RESTORATION_SCROLL = "skill_restoration_scroll";
    static final String SKILL_TREE_RESET_TOKEN = "skill_tree_reset_token";

    public record RepairWeaponResult(Weapon weapon, long remainingGold, int remainingRepairTokens) {}
    public record RepairPetResult(Pet pet, long remainingGold, int remainingPetCareKits) {}
    public record PurchaseResult(int newCount, long remainingGold) {}

    public ServiceResult<RepairWeaponResult> repairWeapon(long accountId, long characterId, long weaponId, boolean useToken) { ... }
    public ServiceResult<RepairPetResult> repairPet(long accountId, long characterId, long petId, boolean useToken) { ... }
    public ServiceResult<PurchaseResult> buyScroll(long accountId, long characterId) { ... }
    public ServiceResult<PurchaseResult> buyResetToken(long accountId, long characterId) { ... }
}
```

**`repairWeapon`/`repairPet` validation order** (fail closed throughout, same posture as `SkillTreeService.learnOrUpgrade`):
1. Ownership (`characterService.isCharacterOwnedBy`) → `ERROR_UNKNOWN` if not owned.
2. Load the character (`characterService.getCharacter`), find the weapon/pet by id in its *current* (non-locked) inventory snapshot → `SHOP_ITEM_NOT_FOUND` if absent.
3. `missingDurability`/`missingPetHealth` computed from that snapshot; if `0` → `SHOP_NOTHING_TO_REPAIR` (reject a no-op repair rather than silently charging 0 gold or burning a token for nothing).
4. If `useToken`: check the relevant `consumables` count from that same snapshot → `SHOP_TOKEN_INSUFFICIENT` if `< 1`. Else: `cost = REPAIR_GOLD_PER_POINT × missing…`; check gold (`AccountDao.getGlobalCurrency`) → `SHOP_GOLD_INSUFFICIENT` if short.
5. Inside `jdbi.useTransaction`: if paying gold, `accountDao.spendGlobalCurrency(accountId, cost)` guarded (0 rows → throw, per §0). Then `characterDao.getInventoryForUpdate(characterId)` (the row lock — the real guard for the token path, per §0): re-find the item and, if `useToken`, re-verify the token count on *this* locked read (not step 4's) before decrementing it; write the repaired item and (if token-paid) the decremented consumables count back in one `updateInventory` call.
6. Log and return the result balances.

**`buyScroll`/`buyResetToken`** are simpler — no item lookup, just a guarded gold spend followed by a `consumables` increment: pre-check gold for a friendly `SHOP_GOLD_INSUFFICIENT`, then inside the transaction `spendGlobalCurrency` guarded, then `getInventoryForUpdate`/increment-the-one-key/`updateInventory`.

## 6. `MessageCode` additions

```java
// Shop (system design §18)
SHOP_ITEM_NOT_FOUND,
SHOP_NOTHING_TO_REPAIR,
SHOP_GOLD_INSUFFICIENT,
SHOP_TOKEN_INSUFFICIENT
```

## 7. Packets, handlers, wiring

New packets (all in `common/network/packet`, all records):
- `RepairWeaponRequest(long characterId, long weaponId, boolean useToken)` / `RepairWeaponResponse(boolean success, String message, Weapon repairedWeapon, long remainingGold, int remainingRepairTokens)`
- `RepairPetRequest(long characterId, long petId, boolean useToken)` / `RepairPetResponse(boolean success, String message, Pet repairedPet, long remainingGold, int remainingPetCareKits)`
- `BuyScrollRequest(long characterId)` / `BuyScrollResponse(boolean success, String message, int scrollCount, long remainingGold)`
- `BuyResetTokenRequest(long characterId)` / `BuyResetTokenResponse(boolean success, String message, int resetTokenCount, long remainingGold)`

Four new handlers in `server/network/handler`, each following `LearnSkillNodeRequestHandler`'s exact shape: unauthenticated connection and non-owned character are logged-and-dropped guardrails (never answered), everything else (including all four new `SHOP_*` codes) gets a response so the client can explain the refusal.

Wire all four:
- `KryoConfig`: register the 8 new packet classes with `RecordSerializer`, appended after `SaveLoadoutResponse` (append-only, per system design §5 — every positional id above stays untouched).
- `ServiceRegistry`: add `ShopService shopService = new ShopService(dbManager.getJdbi(), characterService)` with the same "spans `characters` and `accounts`, needs the raw Jdbi" comment `RewardService`/`SkillTreeService` carry, plus a getter.
- `KryoPacketRouterFactory`/`KryoPacketRouter`: thread `serviceRegistry.getShopService()` through the constructor, register the four new handlers.

## Testing

- `ShopServiceTest`, styled exactly like `SkillTreeServiceTest` (real in-memory `TestDatabase`, `FakeCharacterDao` for character reads, seeded gold/inventory JSON): a weapon repair at `<full>` durability restores it to `maxDurability` and charges `5 × missingDurability` gold; a weapon already at full durability is rejected `SHOP_NOTHING_TO_REPAIR` without charging anything; an unaffordable repair is rejected `SHOP_GOLD_INSUFFICIENT` and leaves gold/inventory untouched; a token-paid repair (`useToken = true`) restores the weapon and decrements `repair_token` by 1 without touching gold at all; a token-paid repair with `0` tokens is rejected `SHOP_TOKEN_INSUFFICIENT`; the mirrored set of cases for `repairPet`/`pet_care_kit`; `buyScroll`/`buyResetToken` charge the flat price and increment the right `consumables` key by exactly 1; a character the account doesn't own is rejected `ERROR_UNKNOWN` for every one of the four operations.
- `CombatMath.isPetUsable`: `true` for any `currentHealth > 0`, `false` at exactly `0`.
- `PetResolver`: a pet at `currentHealth = 0` never acts (no gate roll consumed — same statistical-independence check style as the existing frequency tests) regardless of Insight; a pet with health left behaves exactly as before (regression, no change to the existing roll-rate tests).
- `BattleParticipant`: an equipped pet's combat-time health reflects `Inventory`'s copy, not a stale `Loadout` copy — same shape as the existing `BattleParticipantDurabilityTest`, likely add a sibling `BattleParticipantPetHealthTest` (construct a character whose `Loadout` pet has `currentHealth = healthPoint` but whose matching `Inventory` entry has `currentHealth = 0`, confirm the assembled participant's pet never acts).
- `RewardService`: a battle where the equipped pet acts decrements its `currentHealth` by exactly 1 in the persisted inventory regardless of hit/frequency count within that battle; a battle where the pet never rolled a success leaves its health untouched; health never goes below 0; a battle with both a firing weapon and an acting pet updates both in the single `updateInventory` call (assert on the write count via whatever mechanism the existing durability test uses, if any, or just assert the resulting `Inventory` reflects both changes).
- C2 (`BattleLeavesInventoryAloneTest`): no new exception needed (§0) — extend the existing durability-write positive test (or add a sibling) confirming a pet-health decrement and a shop repair/purchase all land in `inventory` only via the already-sanctioned `updateInventory`, and `onlyTheSanctionedGrantPathCanReachTheStoredItems` still passes unmodified.
- Full existing suite regression: every fixture/test touching `Inventory`, `Pet`, or `Weapon` construction needs its arity fixed (§1/§2) — confirm none were left with a stale 3-arg `Inventory(...)` or 8-arg `Pet(...)` call that wouldn't even compile, and that no fixture accidentally ships a pet at `currentHealth = 0` (would silently break existing pet-resolution tests the same way a 0-durability weapon fixture would have).

## Definition of done

`gradlew.bat build` and `gradlew.bat server:test`/`core:test` pass. Update `docs/planning/02-user-stories.md` — O1, O3 to `done` with close-out notes covering: the row-lock-is-the-only-guard-for-JSONB-fields reasoning (§0), and that O1's token-redemption path is real and callable today even though nothing yet grants a Repair Token or Pet Care Kit (that's Epic P/O4). Leave O2 and O4 as `todo`, and flag O2 (potions/`CONSUMABLE`) as the natural next slice — explain in the note why it was deliberately split out of this pass (§ the scope note above) rather than silently deferred.
