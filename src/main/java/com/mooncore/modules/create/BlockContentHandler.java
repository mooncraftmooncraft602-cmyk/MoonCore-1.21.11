package com.mooncore.modules.create;

import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Handler de type {@code block} pour la commande unifiée (Étape E). */
public final class BlockContentHandler implements ContentTypeHandler {

    private final CustomBlockManagerModule module;
    private com.mooncore.modules.ai.AiPrompts prompts;

    public BlockContentHandler(CustomBlockManagerModule module) {
        this.module = module;
    }

    /** Branche le schéma IA des blocs (la validation/bornage est faite ici au mapping). */
    public void withAi(com.mooncore.modules.ai.AiPrompts prompts) {
        this.prompts = prompts;
    }

    @Override public String type() { return "block"; }

    @Override public String aiSystemPrompt() { return prompts == null ? null : prompts.blockSchemaSystem(); }

    @Override
    public String createFromAi(String aiText, String forcedId) {
        CustomBlockDef def = fromJson(aiText, forcedId);
        if (def == null) return null;
        module.put(def);
        return def.id();
    }

    @Override
    public String validateAi(String aiText, String forcedId) {
        CustomBlockDef def = fromJson(aiText, forcedId);
        if (def == null) return null;
        return def.displayName() + (def.generate()
                ? " (minerai, remplace " + def.replace().name().toLowerCase(java.util.Locale.ROOT) + ")" : "");
    }

    /** Mappe une sortie IA (schéma block) vers un {@link CustomBlockDef} borné. {@code null} si invalide. */
    private static CustomBlockDef fromJson(String aiText, String forcedId) {
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(aiText).getAsJsonObject();
            String id = ContentIds.norm(forcedId != null ? forcedId
                    : (o.has("id") ? o.get("id").getAsString() : slug(str(o, "display-name", "ai_block"))));
            if (!ContentIds.valid(id)) return null;
            CustomBlockDef def = new CustomBlockDef(id);
            if (o.has("display-name")) def.setDisplayName(o.get("display-name").getAsString());
            if (o.has("drop-xp")) def.setDropXp(Math.max(0, Math.min(100, o.get("drop-xp").getAsInt())));
            if (o.has("requires-pickaxe")) def.setRequiresPickaxe(o.get("requires-pickaxe").getAsBoolean());
            if (o.has("worldgen") && o.get("worldgen").isJsonObject()) {
                com.google.gson.JsonObject w = o.getAsJsonObject("worldgen");
                def.setGenerate(w.has("generate") && w.get("generate").getAsBoolean());
                org.bukkit.Material rep = org.bukkit.Material.matchMaterial(
                        str(w, "replace", "STONE").toUpperCase(java.util.Locale.ROOT));
                if (rep != null && rep.isBlock()) def.setReplace(rep);
                int minY = w.has("min-y") ? w.get("min-y").getAsInt() : -16;
                int maxY = w.has("max-y") ? w.get("max-y").getAsInt() : 64;
                def.setYRange(minY, maxY);
                if (w.has("veins-per-chunk")) def.setVeinsPerChunk(Math.max(1, Math.min(8, w.get("veins-per-chunk").getAsInt())));
                if (w.has("vein-size")) def.setVeinSize(Math.max(2, Math.min(12, w.get("vein-size").getAsInt())));
            }
            return def;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(com.google.gson.JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static String slug(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return s.isBlank() ? "ai_block" : (s.length() > 48 ? s.substring(0, 48) : s);
    }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.rawDef(n) != null) return false;
        module.put(new CustomBlockDef(n));
        return true;
    }

    @Override public boolean exists(String id) { return module.rawDef(ContentIds.norm(id)) != null; }
    @Override public boolean delete(String id) { return module.removeDef(ContentIds.norm(id)); }
    @Override public Collection<String> ids() { return module.ids(); }

    @Override
    public boolean give(Player player, String id, int amount) {
        return module.give(player, ContentIds.norm(id), Math.max(1, amount));
    }

    @Override
    public String describe(String id) {
        CustomBlockDef d = module.rawDef(ContentIds.norm(id));
        return d == null ? id : d.displayName();
    }
}
