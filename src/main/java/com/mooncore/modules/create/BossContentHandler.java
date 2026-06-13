package com.mooncore.modules.create;

import com.mooncore.modules.boss.BossManagerModule;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Handler de type {@code boss} pour la commande unifiée (Étape E). */
public final class BossContentHandler implements ContentTypeHandler {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private final BossManagerModule module;
    private com.mooncore.modules.ai.AiPrompts prompts;

    public BossContentHandler(BossManagerModule module) {
        this.module = module;
    }

    /** Branche le schéma IA (le bornage anti-cheat est fait par {@code createBoss}). */
    public void withAi(com.mooncore.modules.ai.AiPrompts prompts) {
        this.prompts = prompts;
    }

    @Override public String type() { return "boss"; }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.exists(n)) return false;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("display-name", n);
        fields.put("entity", "ZOMBIE");
        fields.put("max-health", 200);
        return module.createBoss(n, fields);
    }

    @Override public boolean exists(String id) { return module.exists(ContentIds.norm(id)); }
    @Override public boolean delete(String id) { return module.removeBoss(ContentIds.norm(id)); }
    @Override public Collection<String> ids() { return module.bossIds(); }

    /** Pas d'objet à donner : un boss se fait apparaître via {@code /moon boss spawn <id>}. */
    @Override public boolean give(Player player, String id, int amount) { return false; }

    @Override public String aiSystemPrompt() { return prompts == null ? null : prompts.bossSchemaSystem(); }

    @Override
    @SuppressWarnings("unchecked")
    public String createFromAi(String aiText, String forcedId) {
        Map<String, Object> fields = parse(aiText);
        if (fields == null) return null;
        String id = forcedId != null ? ContentIds.norm(forcedId) : bossIdFrom(fields);
        if (id == null) return null;
        return module.createBoss(id, fields) ? id : null;
    }

    @Override
    public String validateAi(String aiText, String forcedId) {
        Map<String, Object> fields = parse(aiText);
        if (fields == null) return null;
        Object dn = fields.getOrDefault("display-name", forcedId);
        Object ent = fields.getOrDefault("entity", "ZOMBIE");
        Object hp = fields.getOrDefault("max-health", "?");
        return dn + " (" + ent + ", " + hp + " PV)";
    }

    @Override
    public String describe(String id) {
        var def = module.definition(ContentIds.norm(id));
        return def == null ? id : def.displayName();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        try {
            Object m = GSON.fromJson(json, Map.class);
            return m instanceof Map ? (Map<String, Object>) m : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String bossIdFrom(Map<String, Object> fields) {
        Object dn = fields.get("display-name");
        String base = dn != null ? dn.toString() : "ai_boss";
        String id = base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return id.isBlank() ? "ai_boss" : id;
    }
}
