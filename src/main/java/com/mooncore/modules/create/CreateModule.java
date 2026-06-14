package com.mooncore.modules.create;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.crop.CropManagerModule;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.loot.LootManagerModule;

/**
 * Commande de création/gestion de contenu unifiée (Étape E). Construit le {@link ContentTypeRegistry},
 * y enregistre les handlers concrets des types disponibles, et expose {@code /moon content …}.
 * S'active après les modules de contenu (softDepends) pour qu'ils soient présents.
 */
@ModuleInfo(id = "create", name = "CreateCommand",
        softDepends = {"custom-item", "custom-block", "crop", "boss", "loot", "ai-assistant"})
public final class CreateModule extends AbstractModule {

    private final ContentTypeRegistry registry = new ContentTypeRegistry();

    @Override
    protected void onEnable() {
        var ai = plugin().moduleManager().get(com.mooncore.modules.ai.AiAdminModule.class);

        CustomItemManagerModule item = plugin().moduleManager().get(CustomItemManagerModule.class);
        if (item != null) {
            ItemContentHandler h = new ItemContentHandler(item);
            if (ai != null) h.withAi(ai.prompts(), ai.validator());
            registry.register(h);
        }

        CustomBlockManagerModule block = plugin().moduleManager().get(CustomBlockManagerModule.class);
        if (block != null) {
            BlockContentHandler h = new BlockContentHandler(block);
            if (ai != null) h.withAi(ai.prompts());
            registry.register(h);
        }

        CropManagerModule crop = plugin().moduleManager().get(CropManagerModule.class);
        if (crop != null) {
            CropContentHandler h = new CropContentHandler(crop);
            if (ai != null) h.withAi(ai.prompts(), ai.validator());
            registry.register(h);
        }

        var boss = plugin().moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
        if (boss != null) {
            BossContentHandler h = new BossContentHandler(boss);
            if (ai != null) h.withAi(ai.prompts());
            registry.register(h);
        }

        LootManagerModule loot = plugin().moduleManager().get(LootManagerModule.class);
        if (loot != null) {
            LootContentHandler h = new LootContentHandler(loot);
            if (ai != null) h.withAi(ai.prompts(), ai.validator());
            registry.register(h);
        }

        plugin().rootCommand().register(new CreateSubCommand(registry));
        log().info("CreateCommand : " + registry.size() + " type(s) de contenu (" + String.join(", ", registry.types()) + ").");
    }

    @Override
    protected void onDisable() { }

    /** Registre des types (accessible aux extensions futures). */
    public ContentTypeRegistry registry() { return registry; }
}
