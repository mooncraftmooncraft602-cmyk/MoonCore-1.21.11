package com.mooncore.modules.create;

import com.mooncore.modules.loot.LootTableDef;
import com.mooncore.modules.loot.LootManagerModule;

import java.util.Collection;

/** Handler de type {@code loot} pour la commande de création unifiée (Étape E). */
public final class LootContentHandler implements ContentTypeHandler {

    private final LootManagerModule module;

    public LootContentHandler(LootManagerModule module) {
        this.module = module;
    }

    @Override public String type() { return "loot"; }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.def(n) != null) return false;
        module.put(new LootTableDef(n));
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
