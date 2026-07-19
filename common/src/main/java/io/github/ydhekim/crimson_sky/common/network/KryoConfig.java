package io.github.ydhekim.crimson_sky.common.network;

import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.RecordSerializer;
import io.github.ydhekim.crimson_sky.common.model.*;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.network.packet.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized Kryo registration logic to ensure Client and Server
 * maintain identical registration order and serializers.
 */
public class KryoConfig {
    public static void register(Kryo kryo) {
        // Register Core Java Classes
        kryo.register(Object.class);
        kryo.register(Map.class);
        kryo.register(HashMap.class);
        kryo.register(ArrayList.class);
        kryo.register(LinkedHashMap.class);

        // Register LibGDX Collections
        kryo.register(Array.class, new Serializer<Array>() {
            @Override
            public void write(Kryo kryo, Output output, Array array) {
                output.writeInt(array.size, true);
                for (int i = 0; i < array.size; i++) {
                    kryo.writeClassAndObject(output, array.get(i));
                }
            }

            @Override
            public Array read(Kryo kryo, Input input, Class<? extends Array> type) {
                int size = input.readInt(true);
                Array array = new Array(size);
                kryo.reference(array);
                for (int i = 0; i < size; i++) {
                    array.add(kryo.readClassAndObject(input));
                }
                return array;
            }
        });
        kryo.register(Object[].class);

        // Register Packets (Records require RecordSerializer)
        kryo.register(LoginRequest.class, new RecordSerializer<>(LoginRequest.class));
        kryo.register(LoginResponse.class, new RecordSerializer<>(LoginResponse.class));
        kryo.register(CharacterListRequest.class, new RecordSerializer<>(CharacterListRequest.class));
        kryo.register(CharacterListResponse.class, new RecordSerializer<>(CharacterListResponse.class));
        kryo.register(CreateCharacterRequest.class, new RecordSerializer<>(CreateCharacterRequest.class));
        kryo.register(CreateCharacterResponse.class, new RecordSerializer<>(CreateCharacterResponse.class));
        kryo.register(DeleteCharacterRequest.class, new RecordSerializer<>(DeleteCharacterRequest.class));
        kryo.register(DeleteCharacterResponse.class, new RecordSerializer<>(DeleteCharacterResponse.class));
        kryo.register(LocalizationRequest.class, new RecordSerializer<>(LocalizationRequest.class));
        kryo.register(LocalizationResponse.class, new RecordSerializer<>(LocalizationResponse.class));
        kryo.register(AchievementListRequest.class, new RecordSerializer<>(AchievementListRequest.class));
        kryo.register(AchievementListResponse.class, new RecordSerializer<>(AchievementListResponse.class));
        kryo.register(SaveAccountSettingsRequest.class, new RecordSerializer<>(SaveAccountSettingsRequest.class));
        kryo.register(SaveAccountSettingsResponse.class, new RecordSerializer<>(SaveAccountSettingsResponse.class));

        // Register Models (Records require RecordSerializer)
        kryo.register(Character.class, new RecordSerializer<>(Character.class));
        kryo.register(Character[].class);

        kryo.register(Stats.class, new RecordSerializer<>(Stats.class));
        kryo.register(Weapon.class, new RecordSerializer<>(Weapon.class));
        kryo.register(Weapon[].class);

        kryo.register(Skill.class, new RecordSerializer<>(Skill.class));
        kryo.register(Skill[].class);

        kryo.register(Pet.class, new RecordSerializer<>(Pet.class));
        kryo.register(Pet[].class);

        kryo.register(Inventory.class, new RecordSerializer<>(Inventory.class));
        kryo.register(Loadout.class, new RecordSerializer<>(Loadout.class));

        kryo.register(AccountAchievement.class, new RecordSerializer<>(AccountAchievement.class));
        kryo.register(AccountSettings.class, new RecordSerializer<>(AccountSettings.class));

        // Enums
        kryo.register(PlatformType.class);
        kryo.register(Faction.class);
        kryo.register(Rarity.class);
        kryo.register(SkillType.class);
        kryo.register(Difficulty.class);
        kryo.register(Tameness.class);

        // Skill-tree passive effects (system design §16) — appended after the existing enum block so
        // every positional ID above is untouched (system design §5, append-only).
        kryo.register(PassiveEffectType.class);
        kryo.register(StatName.class);

        // Combat (M2) — appended at the end so existing positional IDs above are untouched
        // (system design §5). ResolvedAction rides inside AttackResponse.turns().
        kryo.register(ActionSource.class);
        kryo.register(ResolvedAction.class, new RecordSerializer<>(ResolvedAction.class));

        // Async attack (M3/B4) — one request, one response, the whole battle resolved server-side.
        // AttackResponse carries no opponent id and no bot flag: a synthesized opponent must be
        // indistinguishable from a real one at the protocol level (system design §7).
        kryo.register(AttackRequest.class, new RecordSerializer<>(AttackRequest.class));
        kryo.register(AttackResponse.class, new RecordSerializer<>(AttackResponse.class));

        // Character progression (Epic L / system design §15) — spending earned stat points. Appended
        // after AttackResponse so every positional ID above is untouched (system design §5, append-only).
        kryo.register(AllocateStatPointsRequest.class, new RecordSerializer<>(AllocateStatPointsRequest.class));
        kryo.register(AllocateStatPointsResponse.class, new RecordSerializer<>(AllocateStatPointsResponse.class));

        // Skill tree (system design §16) — learn/upgrade a node — and the loadout-save capability M3
        // needs to make an equipped passive mean anything. Appended after AllocateStatPointsResponse so
        // every positional ID above is untouched (system design §5, append-only).
        kryo.register(LearnSkillNodeRequest.class, new RecordSerializer<>(LearnSkillNodeRequest.class));
        kryo.register(LearnSkillNodeResponse.class, new RecordSerializer<>(LearnSkillNodeResponse.class));
        kryo.register(SaveLoadoutRequest.class, new RecordSerializer<>(SaveLoadoutRequest.class));
        kryo.register(SaveLoadoutResponse.class, new RecordSerializer<>(SaveLoadoutResponse.class));

        // Shop (Epic O / system design §18) — weapon/pet repair and the two gold-only catalog purchases.
        // Appended after SaveLoadoutResponse so every positional ID above is untouched (system design §5,
        // append-only). Inventory's `consumables` map needs no registration of its own: Map/HashMap are
        // already registered at the top, exactly as Character.skillTree relies on.
        kryo.register(RepairWeaponRequest.class, new RecordSerializer<>(RepairWeaponRequest.class));
        kryo.register(RepairWeaponResponse.class, new RecordSerializer<>(RepairWeaponResponse.class));
        kryo.register(RepairPetRequest.class, new RecordSerializer<>(RepairPetRequest.class));
        kryo.register(RepairPetResponse.class, new RecordSerializer<>(RepairPetResponse.class));
        kryo.register(BuyScrollRequest.class, new RecordSerializer<>(BuyScrollRequest.class));
        kryo.register(BuyScrollResponse.class, new RecordSerializer<>(BuyScrollResponse.class));
        kryo.register(BuyResetTokenRequest.class, new RecordSerializer<>(BuyResetTokenRequest.class));
        kryo.register(BuyResetTokenResponse.class, new RecordSerializer<>(BuyResetTokenResponse.class));

        // Potions (O2 / system design §18) — the enum Skill's new CONSUMABLE fields name. Appended last so
        // every positional ID above is untouched (system design §5, append-only); SkillType and ActionSource
        // each gained a trailing constant, which is append-only within the enum for the same reason.
        kryo.register(ResourceType.class);
    }
}
