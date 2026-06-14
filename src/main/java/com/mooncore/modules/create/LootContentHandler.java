package com.mooncore.modules.create;

import com.mooncore.modules.loot.LootTableDef;
import com.mooncore.modules.loot.LootManagerModule;

import java.util.Collection;

/** Handler de type {@code loot} pour la commande de création unifiée (Étape E). */
public final class LootContentHandler implements ContentTypeHandler {

    private final LootManagerModule module;
    private com.mooncore.modules.ai.AiPrompts prompts;
    private com.mooncore.modules.ai.AiActionValidator validator;

    public LootContentHandler(LootManagerModule module) {
        this.module = module;
    }

    /** Branche la génération IA (optionnel). */
    public void withAi(com.mooncore.modules.ai.AiPrompts prompts, com.mooncore.modules.ai.AiActionValidator validator) {
        this.prompts = prompts;
        this.validator = validator;
    }

    @Override public String type() { return "loot"; }

    @Override public String aiSystemPrompt() { return prompts == null ? null : prompts.lootSchemaSystem(); }

    @Override
    public String createFromAi(String aiText, String forcedId) {
        if (validator == null) return null;
        LootTableDef d = validator.validateLoot(aiText, ContentIds.norm(forcedId));
        if (d == null) return null;
        module.put(d);
        return d.id();
    }

    @Override
    public String validateAi(String aiText, String forcedId) {
        if (validator == null) return null;
        LootTableDef d = validator.validateLoot(aiText, ContentIds.norm(forcedId));
        if (d == null) return null;
        int entries = d.pools().stream().mapToInt(p -> p.entries().size()).sum();
        return d.displayName() + " (" + d.pools().size() + " pool(s), " + entries + " entrée(s))";
    }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.def(n) != null) return false;
        module.put(new LootTableDef(n));
        return true;
    }

    /** Donne au joueur le butin tiré de la table (la table est tirée {@code amount} fois). */
    @Override
    public boolean give(org.bukkit.entity.Player player, String id, int amount) {
        String n = ContentIds.norm(id);
        if (module.def(n) == null) return false;
        int times = Math.max(1, amount);
        for (int i = 0; i < times; i++) module.give(player, n, java.util.concurrent.ThreadLocalRandom.current());
        return true;
    }

    @Override public boolean exists(String id) { return module.def(ContentIds.norm(id)) != null; }
    @Override public boolean delete(String id) { return module.removeDef(ContentIds.norm(id)); }
    @Override public Collection<String> ids() { return module.ids(); }

    @Override
    public String describe(String id) {
        LootTableDef d = module.def(ContentIds.norm(id));
        if (d == null) return id;
        return d.displayName() + " (" + d.pools().size() + " pool(s))";
    }

    @Override
    public boolean cloneEntry(String sourceId, String newId) {
        String src = ContentIds.norm(sourceId);
        String dst = ContentIds.norm(newId);
        LootTableDef source = module.def(src);
        if (source == null || !ContentIds.valid(dst) || module.def(dst) != null) return false;
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        source.save(cfg);
        module.put(LootTableDef.load(dst, cfg));
        return true;
    }
}
