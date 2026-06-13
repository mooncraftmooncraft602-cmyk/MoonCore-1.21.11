package com.mooncore.modules.create;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.editor.ItemEditorMenu;
import org.bukkit.entity.Player;

import java.util.Collection;

/** Handler de type {@code item} pour la commande unifiée (Étape E). */
public final class ItemContentHandler implements ContentTypeHandler {

    private final CustomItemManagerModule module;
    private com.mooncore.modules.ai.AiPrompts prompts;
    private com.mooncore.modules.ai.AiActionValidator validator;

    public ItemContentHandler(CustomItemManagerModule module) {
        this.module = module;
    }

    /** Branche la génération IA (optionnel ; sans cela, aiSystemPrompt/createFromAi renvoient null). */
    public void withAi(com.mooncore.modules.ai.AiPrompts prompts, com.mooncore.modules.ai.AiActionValidator validator) {
        this.prompts = prompts;
        this.validator = validator;
    }

    @Override public String type() { return "item"; }

    @Override public String aiSystemPrompt() { return prompts == null ? null : prompts.itemSchemaSystem(); }

    @Override
    public String createFromAi(String aiText, String forcedId) {
        if (validator == null) return null;
        var r = validator.validateItem(aiText, ContentIds.norm(forcedId), -1);
        if (r == null || !r.ok() || r.def() == null) return null;
        module.put(r.def());
        return r.def().id();
    }

    @Override
    public String validateAi(String aiText, String forcedId) {
        if (validator == null) return null;
        var r = validator.validateItem(aiText, ContentIds.norm(forcedId), -1);
        if (r == null || !r.ok() || r.def() == null) return null;
        return r.def().displayName() + " (" + r.def().material().name().toLowerCase(java.util.Locale.ROOT)
                + ", " + r.def().rarity().id() + ", " + r.def().stats().size() + " stat(s))";
    }

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
