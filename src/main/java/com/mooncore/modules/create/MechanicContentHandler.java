package com.mooncore.modules.create;

import com.mooncore.modules.mechanic.MechanicDef;
import com.mooncore.modules.mechanic.MechanicModule;

import java.util.Collection;

/** Handler de type {@code mechanic} pour la commande de création unifiée (Étape E). */
public final class MechanicContentHandler implements ContentTypeHandler {

    private final MechanicModule module;
    private com.mooncore.modules.ai.AiPrompts prompts;
    private com.mooncore.modules.ai.AiActionValidator validator;

    public MechanicContentHandler(MechanicModule module) {
        this.module = module;
    }

    /** Branche la génération IA (optionnel). */
    public void withAi(com.mooncore.modules.ai.AiPrompts prompts, com.mooncore.modules.ai.AiActionValidator validator) {
        this.prompts = prompts;
        this.validator = validator;
    }

    @Override public String type() { return "mechanic"; }

    @Override public String aiSystemPrompt() { return prompts == null ? null : prompts.mechanicSchemaSystem(); }

    @Override
    public String createFromAi(String aiText, String forcedId) {
        if (validator == null) return null;
        MechanicDef d = validator.validateMechanic(aiText, ContentIds.norm(forcedId));
        if (d == null) return null;
        module.put(d);
        return d.id();
    }

    @Override
    public String validateAi(String aiText, String forcedId) {
        if (validator == null) return null;
        MechanicDef d = validator.validateMechanic(aiText, ContentIds.norm(forcedId));
        if (d == null) return null;
        return d.displayName() + " (" + d.trigger().name().toLowerCase(java.util.Locale.ROOT)
                + ", " + d.actions().size() + " action(s)" + (d.isRunnable() ? "" : ", inactive") + ")";
    }

    @Override
    public boolean create(String id) {
        String n = ContentIds.norm(id);
        if (!ContentIds.valid(n) || module.def(n) != null) return false;
        module.put(new MechanicDef(n));
        return true;
    }

    @Override public boolean exists(String id) { return module.def(ContentIds.norm(id)) != null; }
    @Override public boolean delete(String id) { return module.removeDef(ContentIds.norm(id)); }
    @Override public Collection<String> ids() { return module.ids(); }

    @Override
    public String describe(String id) {
        MechanicDef d = module.def(ContentIds.norm(id));
        if (d == null) return id;
        return d.displayName() + " (" + d.trigger().name().toLowerCase(java.util.Locale.ROOT)
                + ", " + d.actions().size() + " action(s)" + (d.isRunnable() ? "" : ", inactive") + ")";
    }

    @Override
    public boolean cloneEntry(String sourceId, String newId) {
        String src = ContentIds.norm(sourceId);
        String dst = ContentIds.norm(newId);
        MechanicDef source = module.def(src);
        if (source == null || !ContentIds.valid(dst) || module.def(dst) != null) return false;
        org.bukkit.configuration.MemoryConfiguration cfg = new org.bukkit.configuration.MemoryConfiguration();
        source.save(cfg);
        module.put(MechanicDef.load(dst, cfg));
        return true;
    }
}
