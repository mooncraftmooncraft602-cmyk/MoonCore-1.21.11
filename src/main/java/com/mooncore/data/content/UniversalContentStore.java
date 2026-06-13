package com.mooncore.data.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Store universel requêtable (Étape A du master brain). Stocke <b>n'importe quel</b>
 * type de contenu (item, block, boss, crop, model…) en une seule table SQL
 * {@code mooncore_content(content_type, id, data_json, schema_version, created_at, updated_at)},
 * sérialisé en JSON via {@link Gson}.
 * <p>
 * Le YAML par-type existant reste la source d'édition humaine ; ce store ajoute une
 * couche <b>requêtable</b> (filtres, comptages, exports) sans remplacer les stores YAML.
 * Plage de migration réservée : <b>1400+</b>.
 * <p>
 * Toutes les écritures/lectures passent par les variantes asynchrones de {@link Database}
 * (aucune I/O DB sur le thread principal).
 */
public final class UniversalContentStore {

    /** Id sûr : slug minuscule, pas de séparateur de chemin (cohérent avec les autres stores). */
    private static final java.util.regex.Pattern ID_PATTERN = java.util.regex.Pattern.compile("[a-z0-9_-]{1,48}");
    /** Type de contenu sûr : minuscule, court, sans séparateur. */
    private static final java.util.regex.Pattern TYPE_PATTERN = java.util.regex.Pattern.compile("[a-z0-9_]{1,32}");

    /** Gson partagé pour le round-trip des définitions (Étape A2). */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Une ligne de contenu telle que persistée en base. */
    public record Row(String contentType, String id, String dataJson,
                      int schemaVersion, long createdAt, long updatedAt) {}

    private final Database db;

    public UniversalContentStore(Database db) {
        this.db = db;
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public static boolean isValidType(String type) {
        return type != null && TYPE_PATTERN.matcher(type).matches();
    }

    /** Migrations de la couche contenu universel (plage 1400+). */
    public static List<Migration> migrations() {
        return List.of(new V1400ContentTable());
    }

    /** v1400 : table générique de contenu requêtable + index par type. */
    static final class V1400ContentTable implements Migration {
        @Override public int version() { return 1400; }
        @Override public String description() { return "Contenu universel : table mooncore_content"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mooncore_content (
                            content_type   TEXT    NOT NULL,
                            id             TEXT    NOT NULL,
                            data_json      TEXT    NOT NULL,
                            schema_version INTEGER NOT NULL DEFAULT 1,
                            created_at     INTEGER NOT NULL DEFAULT 0,
                            updated_at     INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (content_type, id)
                        )
                        """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_content_type ON mooncore_content(content_type)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_content_updated ON mooncore_content(content_type, updated_at)");
            }
        }
    }

    // ---- Écriture ----

    /**
     * Insère ou met à jour une entrée (upsert). {@code created_at} est préservé lors d'une
     * mise à jour ; {@code updated_at} est toujours rafraîchi.
     *
     * @param nowMs horodatage fourni par l'appelant (testable, pas d'appel horloge caché)
     */
    public CompletableFuture<Void> upsert(String contentType, String id, String dataJson,
                                          int schemaVersion, long nowMs) {
        String type = normalize(contentType);
        String key = normalize(id);
        if (!isValidType(type) || !isValidId(key)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Type/id de contenu invalide : " + contentType + "/" + id));
        }
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_content (content_type, id, data_json, schema_version, created_at, updated_at)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT(content_type, id) DO UPDATE SET
                        data_json = excluded.data_json,
                        schema_version = excluded.schema_version,
                        updated_at = excluded.updated_at
                    """)) {
                ps.setString(1, type);
                ps.setString(2, key);
                ps.setString(3, dataJson);
                ps.setInt(4, schemaVersion);
                ps.setLong(5, nowMs);
                ps.setLong(6, nowMs);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Boolean> delete(String contentType, String id) {
        String type = normalize(contentType);
        String key = normalize(id);
        if (!isValidType(type) || !isValidId(key)) {
            return CompletableFuture.completedFuture(false);
        }
        return db.queryAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM mooncore_content WHERE content_type = ? AND id = ?")) {
                ps.setString(1, type);
                ps.setString(2, key);
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ---- Lecture ----

    public CompletableFuture<Row> load(String contentType, String id) {
        String type = normalize(contentType);
        String key = normalize(id);
        if (!isValidType(type) || !isValidId(key)) {
            return CompletableFuture.completedFuture(null);
        }
        return db.queryAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT content_type, id, data_json, schema_version, created_at, updated_at "
                    + "FROM mooncore_content WHERE content_type = ? AND id = ?")) {
                ps.setString(1, type);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        });
    }

    /** Charge toutes les entrées d'un type, indexées par id (ordre stable par id). */
    public CompletableFuture<Map<String, Row>> loadAll(String contentType) {
        String type = normalize(contentType);
        if (!isValidType(type)) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }
        return db.queryAsync(c -> {
            Map<String, Row> out = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT content_type, id, data_json, schema_version, created_at, updated_at "
                    + "FROM mooncore_content WHERE content_type = ? ORDER BY id")) {
                ps.setString(1, type);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Row row = mapRow(rs);
                        out.put(row.id(), row);
                    }
                }
            }
            return out;
        });
    }

    /** Liste les types de contenu présents en base. */
    public CompletableFuture<List<String>> listTypes() {
        return db.queryAsync(c -> {
            List<String> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT DISTINCT content_type FROM mooncore_content ORDER BY content_type")) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        });
    }

    public CompletableFuture<Integer> count(String contentType) {
        String type = normalize(contentType);
        if (!isValidType(type)) {
            return CompletableFuture.completedFuture(0);
        }
        return db.queryAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM mooncore_content WHERE content_type = ?")) {
                ps.setString(1, type);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private static Row mapRow(ResultSet rs) throws SQLException {
        return new Row(
                rs.getString("content_type"),
                rs.getString("id"),
                rs.getString("data_json"),
                rs.getInt("schema_version"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    private static String normalize(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}
