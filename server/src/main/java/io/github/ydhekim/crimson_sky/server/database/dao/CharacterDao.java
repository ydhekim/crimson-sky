package io.github.ydhekim.crimson_sky.server.database.dao;

import com.badlogic.gdx.utils.Array;
import io.github.ydhekim.crimson_sky.common.model.Character;
import io.github.ydhekim.crimson_sky.common.model.Loadout;
import io.github.ydhekim.crimson_sky.common.model.Pet;
import io.github.ydhekim.crimson_sky.common.model.Skill;
import io.github.ydhekim.crimson_sky.common.model.Stats;
import io.github.ydhekim.crimson_sky.common.model.Weapon;
import io.github.ydhekim.crimson_sky.server.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CharacterDao {

    public Array<Character> getCharactersByUserId(int userId) {
        Array<Character> characters = new Array<>();
        String sql = "SELECT * FROM characters WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    characters.add(mapResultSetToCharacter(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return characters;
    }

    public boolean createCharacter(int userId, String name) {
        String sql = "INSERT INTO characters (user_id, name, level, experience, max_health, max_mana, base_defence, base_attack_power, strength, dexterity, vitality, intelligence, wisdom, spirit, speed, insight) " +
            "VALUES (?, ?, 1, 0, 100, 50, 5, 10, 5, 5, 5, 5, 5, 5, 5, 5)";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteCharacter(int userId, String name) {
        String sql = "DELETE FROM characters WHERE user_id = ? AND name = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getCharacterCount(int userId) {
        String sql = "SELECT COUNT(*) FROM characters WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Character mapResultSetToCharacter(ResultSet rs) throws SQLException {
        Stats stats = new Stats(
            rs.getInt("strength"),
            rs.getInt("dexterity"),
            rs.getInt("vitality"),
            rs.getInt("intelligence"),
            rs.getInt("wisdom"),
            rs.getInt("spirit"),
            rs.getInt("speed"),
            rs.getInt("insight")
        );

        return new Character(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("level"),
            rs.getLong("experience"),
            rs.getInt("max_health"),
            rs.getInt("max_mana"),
            rs.getInt("base_defence"),
            rs.getInt("base_attack_power"),
            stats,
            new Array<Weapon>(), // TODO: Load items
            new Array<Skill>(), // TODO: Load skills
            new Array<Pet>(), // TODO: Load pets
            new Loadout(new Array<Weapon>(), new Array<Skill>(), new Array<Pet>()) // TODO: Load loadout
        );
    }
}
