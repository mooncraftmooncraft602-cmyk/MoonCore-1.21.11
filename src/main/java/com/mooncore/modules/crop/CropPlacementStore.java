package com.mooncore.modules.crop;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Persistance des <b>emplantations</b> de cultures (table {@code mooncore_crop_placement}), Étape C3.
 * Chaque plant posé dans le monde est une ligne (position, culture, étape, date de plantation).
 * <p>Plage de migration réservée aux cultures : <b>1401</b> (après {@code mooncore_content}=1400).
 * Toutes les écritures passent par les variantes asynchrones (aucune I/O DB sur le thread principal) ;
 * seule {@link #loadAll()} est synchrone (démarrage).
 */
public final class CropPlacementStore {

    /** Une emplantation. {@code locKey}/{@code chunkKey} sont dérivés de la position. */
    public record Placement(String world, int x, int y, int z, String cropId, int stage, long plantedAt) {
        public String locKey() { return world + ":" + x + ":" + y + ":" + z; }
        public String chunkKey() { return world + ":" + (x >> 4) + ":" + (z >> 4); }
        public static String locKey(String world, int x, int y, int z) { return world + ":" + x + ":" + y + ":" + z; }
        public static String chunkKey(String world, int chunkX, int chunkZ) { return world + ":" + chunkX + ":" + chunkZ; }
    }

    private final Database db;

    public CropPlacementStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V1401CropPlacement());
    }

    static final class V1401CropPlacement implements Migration {
        @Override public int version() { return 1401; }
        @Override public String description() { return "Cultures : table mooncore_crop_placement"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mooncore_crop_placement (
                            loc_key    TEXT    NOT NULL PRIMARY KEY,
                            world      TEXT    NOT NULL,
                            x          INTEGER NOT NULL,
                            y          INTEGER NOT NULL,
                            z          INTEGER NOT NULL,
                            chunk_key  TEXT    NOT NULL,
                            crop_id    TEXT    NOT NULL,
                            stage      INTEGER NOT NULL DEFAULT 0,
                            planted_at INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_crop_chunk ON mooncore_crop_placement(chunk_key)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_crop_id ON mooncore_crop_placement(crop_id)");
            }
        }
    }

    /** Chargement synchrone de toutes les emplantations (démarrage uniquement). */
    public List<Placement> loadAll() throws SQLException {
        return db.query(c -> {
            List<Placement> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT world,x,y,z,crop_id,stage,planted_at FROM mooncore_crop_placement")) {
                while (rs.next()) out.add(map(rs));
            }
            return out;
        });
    }

    public CompletableFuture<List<Placement>> loadInChunk(String world, int chunkX, int chunkZ) {
        return db.queryAsync(c -> {
            List<Placement> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT world,x,y,z,crop_id,stage,planted_at FROM mooncore_crop_placement WHERE chunk_key=?")) {
                ps.setString(1, Placement.chunkKey(world, chunkX, chunkZ));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
            return out;
        });
    }

    public CompletableFuture<Void> save(Placement p) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_crop_placement (loc_key, world, x, y, z, chunk_key, crop_id, stage, planted_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(loc_key) DO UPDATE SET
                        crop_id=excluded.crop_id, stage=excluded.stage, planted_at=excluded.planted_at
                    """)) {
                ps.setString(1, p.locKey());
                ps.setString(2, p.world());
                ps.setInt(3, p.x()); ps.setInt(4, p.y()); ps.setInt(5, p.z());
                ps.setString(6, p.chunkKey());
                ps.setString(7, p.cropId());
                ps.setInt(8, p.stage());
                ps.setLong(9, p.plantedAt());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> updateStage(String locKey, int stage) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE mooncore_crop_placement SET stage=? WHERE loc_key=?")) {
                ps.setInt(1, stage);
                ps.setString(2, locKey);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(String locKey) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM mooncore_crop_placement WHERE loc_key=?")) {
                ps.setString(1, locKey);
                ps.executeUpdate();
            }
        });
    }

    private static Placement map(ResultSet rs) throws SQLException {
        return new Placement(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                rs.getString("crop_id"), rs.getInt("stage"), rs.getLong("planted_at"));
    }
}
