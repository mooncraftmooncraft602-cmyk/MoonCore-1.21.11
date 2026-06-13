package com.mooncore.modules.create;

import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Handler de type {@code block} pour la commande unifiée (Étape E). */
public final class BlockContentHandler implements ContentTypeHandler {

    private final CustomBlockManagerModule module;

    public BlockContentHandler(CustomBlockManagerModule module) {
        this.module = module;
    }

    @Override public String type() { return "block"; }

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
