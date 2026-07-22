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
    /**
     * Per-connection buffer sizes for KryoNet's {@code Client}/{@code Server} constructors.
     * <p>
     * {@code OBJECT_BUFFER_SIZE} is the working buffer used to serialize a single outgoing object
     * and to read a single incoming one, so it must exceed the largest packet on both ends — the
     * default 2048 was too small for responses like the localization table (~4 KB), which failed
     * with "Unable to read object larger than read buffer". {@code WRITE_BUFFER_SIZE} is the TCP
     * send queue and is kept larger so multiple objects can be buffered while one is being flushed.
     */
    public static final int WRITE_BUFFER_SIZE = 262144;   // 256 KB
    public static final int OBJECT_BUFFER_SIZE = 65536;    // 64 KB

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

        // Quests (Epic P / system design §19) — quest status and claim. Appended after O2's ResourceType so
        // every positional ID above is untouched (system design §5, append-only). QuestProgress rides
        // inside a QuestStatusResponse's Array and QuestClaimResult inside a ClaimQuestResponse, so both are
        // registered here (before the packets) the same way ResolvedAction is registered before AttackResponse.
        kryo.register(QuestProgress.class, new RecordSerializer<>(QuestProgress.class));
        kryo.register(QuestClaimResult.class, new RecordSerializer<>(QuestClaimResult.class));
        kryo.register(QuestStatusRequest.class, new RecordSerializer<>(QuestStatusRequest.class));
        kryo.register(QuestStatusResponse.class, new RecordSerializer<>(QuestStatusResponse.class));
        kryo.register(ClaimQuestRequest.class, new RecordSerializer<>(ClaimQuestRequest.class));
        kryo.register(ClaimQuestResponse.class, new RecordSerializer<>(ClaimQuestResponse.class));

        // Account levers (Epic Q / system design §20) — the daily-battle-cap rejection. Appended after the
        // quest packets so every positional ID above is untouched (system design §5, append-only). Registered
        // here, not beside AttackResponse, precisely because registration is positional: order must match on
        // both sides, so a conceptually-related packet still goes at the physical end.
        kryo.register(AttackRejectedResponse.class, new RecordSerializer<>(AttackRejectedResponse.class));

        // Ranked ladder (Epic R / system design §21) — the enum AttackRequest's new `mode` field carries.
        // Appended after AttackRejectedResponse so every positional ID above is untouched (system design
        // §5, append-only).
        kryo.register(BattleMode.class);

        // Monthly ladder + claim (Epic R3 / system design §21) — live standing and the once-per-month
        // tier claim. Appended after BattleMode so every positional ID above is untouched (system design
        // §5, append-only). LadderStatus rides inside a LadderStatusResponse and LadderClaimResult inside a
        // ClaimLadderRewardResponse, so both models are registered before the packets — the same way
        // QuestProgress/QuestClaimResult are registered before their packets.
        kryo.register(LadderStatus.class, new RecordSerializer<>(LadderStatus.class));
        kryo.register(LadderClaimResult.class, new RecordSerializer<>(LadderClaimResult.class));
        kryo.register(LadderStatusRequest.class, new RecordSerializer<>(LadderStatusRequest.class));
        kryo.register(LadderStatusResponse.class, new RecordSerializer<>(LadderStatusResponse.class));
        kryo.register(ClaimLadderRewardRequest.class, new RecordSerializer<>(ClaimLadderRewardRequest.class));
        kryo.register(ClaimLadderRewardResponse.class, new RecordSerializer<>(ClaimLadderRewardResponse.class));

        // Character page + equipped title (Epic S3/S4 / system design §22) — the read-only page aggregate and
        // the title equip/clear. Appended after ClaimLadderRewardResponse so every positional ID above is
        // untouched (system design §5, append-only). Models before packets — the same convention
        // LadderStatus/QuestProgress follow, since each model rides inside a response: RecentMatch inside a
        // CharacterStatistics inside a CharacterPage, CharacterPageAchievement in that same page.
        kryo.register(RecentMatch.class, new RecordSerializer<>(RecentMatch.class));
        kryo.register(CharacterStatistics.class, new RecordSerializer<>(CharacterStatistics.class));
        kryo.register(CharacterPageAchievement.class, new RecordSerializer<>(CharacterPageAchievement.class));
        kryo.register(CharacterPage.class, new RecordSerializer<>(CharacterPage.class));
        kryo.register(CharacterPageRequest.class, new RecordSerializer<>(CharacterPageRequest.class));
        kryo.register(CharacterPageResponse.class, new RecordSerializer<>(CharacterPageResponse.class));
        kryo.register(SetEquippedTitleRequest.class, new RecordSerializer<>(SetEquippedTitleRequest.class));
        kryo.register(SetEquippedTitleResponse.class, new RecordSerializer<>(SetEquippedTitleResponse.class));

        // Character customization (Epic T1 / system design §23) — appended after SetEquippedTitleResponse so
        // every positional ID above is untouched (system design §5, append-only), even though
        // CreateCharacterRequest itself (which now carries an Appearance) was registered much earlier —
        // record field changes don't move a type's own registration, only a brand-new type needs a new call.
        kryo.register(Appearance.class, new RecordSerializer<>(Appearance.class));
    }
}
