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

        // Enums
        kryo.register(PlatformType.class);
        kryo.register(Faction.class);
        kryo.register(Rarity.class);
        kryo.register(SkillType.class);
        kryo.register(Difficulty.class);
        kryo.register(Tameness.class);
    }
}
