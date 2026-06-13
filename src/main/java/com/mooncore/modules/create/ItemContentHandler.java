package com.mooncore.modules.create;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.editor.ItemEditorMenu;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Handler de type {@code item} pour la commande unifiée (Étape E). */
public final class ItemContentHandler implements ContentTypeHandler {

    private final CustomItemManagerModule module;

    public ItemContentHandler(CustomItemManagerModule module) {
        this.module = module;
    }

    @Override public String type() { return "item"; }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.rawDef(n) != null) return false;
        module.put(new CustomItemDef(n));
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
    public boolean openEditor(Player player, String id) {
        String n = ContentIds.norm(id);
        if (module.rawDef(n) == null) return false;
        ItemEditorMenu.open(module, module.chatInput(), player, n);
        return true;
    }

    @Override
    public String describe(String id) {
        CustomItemDef d = module.rawDef(ContentIds.norm(id));
        return d == null ? id : d.displayName();
    }
}
