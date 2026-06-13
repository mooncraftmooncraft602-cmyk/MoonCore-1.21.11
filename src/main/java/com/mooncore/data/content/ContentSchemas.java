package com.mooncore.data.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre des {@link ContentSchema} par type de contenu (Étape A5). Centralise la version
 * courante de chaque type et applique les mises à niveau de forme JSON au chargement SQL.
 * <p>
 * Un type non enregistré utilise le schéma <b>identité</b> (version 1, aucune transformation),
 * de sorte que tout fonctionne sans configuration tant qu'aucune évolution de forme n'est requise.
 */
public final class ContentSchemas {

    /** Schéma par défaut : version 1, jamais de transformation. */
    public static final ContentSchema IDENTITY = new ContentSchema() {
        @Override public int currentVersion() { return 1; }
        @Override public JsonObject upgrade(JsonObject data, int fromVersion) { return data; }
    };

    private static final Map<String, ContentSchema> REGISTRY = new ConcurrentHashMap<>();

    private ContentSchemas() {}

    /** Enregistre (ou remplace) le schéma d'un type. */
    public static void register(String contentType, ContentSchema schema) {
        if (contentType != null && schema != null) {
            REGISTRY.put(contentType.toLowerCase(java.util.Locale.ROOT), schema);
        }
    }

    /** Schéma d'un type, ou {@link #IDENTITY} si non enregistré. */
    public static ContentSchema get(String contentType) {
        if (contentType == null) return IDENTITY;
        return REGISTRY.getOrDefault(contentType.toLowerCase(java.util.Locale.ROOT), IDENTITY);
    }

    /** Version courante de la forme JSON pour ce type (raccourci). */
    public static int currentVersion(String contentType) {
        return get(contentType).currentVersion();
    }

    /**
     * Met à niveau un JSON {@code fromVersion} vers la version courante du type. Retourne le JSON
     * inchangé si déjà à jour ou si le parse échoue (robustesse : on ne casse jamais le chargement).
     */
    public static String upgradeToCurrent(String contentType, String json, int fromVersion) {
        ContentSchema schema = get(contentType);
        if (fromVersion >= schema.currentVersion()) return json;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            JsonObject upgraded = schema.upgrade(obj, fromVersion);
            return UniversalContentStore.GSON.toJson(upgraded);
        } catch (Exception e) {
            return json; // forme inattendue : on laisse le chargement YAML/erreur en aval décider
        }
    }
}
