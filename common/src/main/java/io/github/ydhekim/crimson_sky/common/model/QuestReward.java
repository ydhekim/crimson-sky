package io.github.ydhekim.crimson_sky.common.model;

/**
 * One reward a quest can pay (system design §19). {@code type} is {@code "GOLD"} or {@code "CONSUMABLE"};
 * {@code itemKey} is one of {@code ShopService}'s consumable string constants when type is {@code
 * CONSUMABLE}, {@code null} for {@code GOLD}. {@code amount} is the gold quantity, or the item count (always
 * {@code 1} for every reward this game currently grants). A quest with more than one {@code QuestReward} in
 * its {@code rewardOptions} list (system design §19's weekly quest) means the player picks one, not that
 * they receive all of them.
 *
 * <p>Lives in {@code common} for the same reason {@link QuestProgress} does: it rides inside a
 * {@code QuestStatusResponse} across the wire, and Kryo registration (in {@code common}) can only see
 * {@code common} types. The {@code itemKey} strings are deliberately raw keys, not display text — the
 * client maps them onto the {@code UI_ITEM_*} localization keys seeded in {@code V30}.
 */
public record QuestReward(String type, String itemKey, int amount) {
}
