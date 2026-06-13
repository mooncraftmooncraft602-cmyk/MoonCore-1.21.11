package com.mooncore.modules.create;

import com.mooncore.modules.crop.CropDef;
import com.mooncore.modules.crop.CropManagerModule;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/** Handler de type {@code crop} pour la commande unifiée (Étape E). */
public final class CropContentHandler implements ContentTypeHandler {

    private final CropManagerModule module;

    public CropContentHandler(CropManagerModule module) {
        this.module = module;
    }

    @Override public String type() { return "crop"; }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.def(n) != null) return false;
        module.put(new CropDef(n));
        return true;
    }

    @Override public boolean exists(String id) { return module.def(ContentIds.norm(id)) != null; }
    @Override public boolean delete(String id) { return module.removeDef(ContentIds.norm(id)); }
    @Override public Collection<String> ids() { return module.ids(); }

    /** Donne la graine de la culture (Material ou item custom). */
    @Override
    public boolean give(Player player, String id, int amount) {
        CropDef d = module.def(ContentIds.norm(id));
        if (d == null) return false;
        ItemStack seed = module.seedItem(d, Math.max(1, amount));
        if (seed == null) return false;
        player.getInventory().addItem(seed);
        return true;
    }

    @Override
    public String describe(String id) {
        CropDef d = module.def(ContentIds.norm(id));
        return d == null ? id : d.displayName();
    }
}
