package io.github.ydhekim.crimson_sky.common.model;

import com.badlogic.gdx.utils.Array;

import java.util.Map;

/**
 * Everything a character owns. The single source of truth for item state — a {@link Loadout} only means
 * "this item id is equipped", and both durability (§17) and pet health (§18) are read back from here at
 * battle setup and written back here after.
 *
 * <p><b>{@code consumables} is a key → count map</b> (§18), the same shape as {@code Character.skillTree},
 * deliberately not a table: shop stock is embedded JSONB like everything else in this record, so it needed
 * no migration. Keys are the {@code ShopService} string constants ({@code repair_token},
 * {@code pet_care_kit}, {@code skill_restoration_scroll}, {@code skill_tree_reset_token}).
 *
 * <p>Every field is null-tolerant by convention: characters created before a given field existed persist
 * it as {@code null} (creation itself stores null arrays), so readers coalesce rather than assume.
 */
public record Inventory(
    Array<Weapon> weapons,
    Array<Skill> skills,
    Array<Pet> pets,
    Map<String, Integer> consumables
) {
}
