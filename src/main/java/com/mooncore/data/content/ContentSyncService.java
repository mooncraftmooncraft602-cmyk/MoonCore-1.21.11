package com.mooncore.data.content;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;
import com.mooncore.util.MoonLogger;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Service de synchronisation du contenu entre le stockage <b>YAML</b> (édition humaine,
 * historique) et le store <b>SQL</b> requêtable ({@link UniversalContentStore}), Étape A3.
 * <p>
 * Le mode est piloté par un flag config {@code content.storage-mode} :
 * <ul>
 *   <li>{@code yaml} (défaut) — comportement historique, aucune écriture SQL ;</li>
 *   <li>{@code both} — double-écriture YAML + miroir SQL ;</li>
 *   <li>{@code sql} — SQL fait autorité (le YAML n'est plus réécrit, mais reste lisible).</li>
 * </ul>
 * Aucune suppression d'API : par défaut ({@code yaml}) rien ne change. Le miroir SQL est
 * « fire-and-forget » journalisé (aucune I/O DB sur le thread principal).
 */
public final class ContentSyncService {

    public enum Mode {
        YAML, SQL, BOTH;

        public static Mode parse(String raw) {
            if (raw == null) return YAML;
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "sql" -> SQL;
                case "both" -> BOTH;
                default -> YAML;
            };
        }

        /** Le YAML doit-il (encore) être écrit comme source canonique ? */
        public boolean writesYaml() { return this == YAML || this == BOTH; }
        /** Le miroir SQL doit-il être maintenu ? */
        public boolean writesSql() { return this == SQL || this == BOTH; }
    }

    private final UniversalContentStore store;
    private final Supplier<String> modeSupplier;
    private final MoonLogger log;

    public ContentSyncService(Database db, Supplier<String> modeSupplier, MoonLogger log) {
        this.store = new UniversalContentStore(db);
        this.modeSupplier = modeSupplier;
        this.log = log;
    }

    /** Migrations de la couche contenu universel (plage 1400+). */
    public static List<Migration> migrations() {
        return UniversalContentStore.migrations();
    }

    public Mode mode() { return Mode.parse(modeSupplier.get()); }
    public boolean writesYaml() { return mode().writesYaml(); }
    public boolean writesSql() { return mode().writesSql(); }
    public UniversalContentStore store() { return store; }

    /**
     * Écrit le miroir SQL d'une définition (no-op si le mode n'écrit pas en SQL).
     * Fire-and-forget : toute erreur est journalisée, jamais propagée à l'appelant.
     */
    public void mirror(String contentType, String id, String json, int schemaVersion, long nowMs) {
        if (!writesSql()) return;
        store.upsert(contentType, id, json, schemaVersion, nowMs).exceptionally(t -> {
            log.error("Miroir SQL échoué pour " + contentType + ":" + id, t);
            return null;
        });
    }

    /** Retire l'entrée du miroir SQL (no-op si le mode n'écrit pas en SQL). */
    public void remove(String contentType, String id) {
        if (!writesSql()) return;
        store.delete(contentType, id).exceptionally(t -> {
            log.error("Suppression SQL échouée pour " + contentType + ":" + id, t);
            return false;
        });
    }
}
