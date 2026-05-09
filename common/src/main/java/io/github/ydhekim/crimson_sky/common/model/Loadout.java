package io.github.ydhekim.crimson_sky.common.model;

import com.badlogic.gdx.utils.Array;

public record Loadout(
    Array<Weapon> weapons,
    Array<Skill> skills,
    Array<Pet> pets
) {
}
