package io.github.ydhekim.crimson_sky.ecs.component;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Pet;

public class InventoryComponent implements Component, Poolable {
    public Array<Weapon> weapons = new Array<>();
    public Array<Skill> skills = new Array<>();
    public Array<Pet> pets = new Array<>();

    @Override
    public void reset() {
        weapons.clear();
        skills.clear();
        pets.clear();
    }
}
