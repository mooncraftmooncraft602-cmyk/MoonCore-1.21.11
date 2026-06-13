package com.mooncore.modules.create;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.crop.CropManagerModule;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;

/**
 * Commande de création/gestion de contenu unifiée (Étape E). Construit le {@link ContentTypeRegistry},
 * y enregistre les handlers concrets des types disponibles, et expose {@code /moon content …}.
 * S'active après les modules de contenu (softDepends) pour qu'ils soient présents.
 */
@ModuleInfo(id = "create", name = "CreateCommand", softDepends = {"custom-item", "custom-block", "crop"})
public final class CreateModule extends AbstractModule {

    private final ContentTypeRegistry registry = new ContentTypeRegistry();

    @Override
    protected void onEnable() {
        CustomItemManagerModule item = plugin().moduleManager().get(CustomItemManagerModule.class);
        if (item != null) registry.register(new ItemContentHandler(item));

        CustomBlockManagerModule block = plugin().moduleManager().get(CustomBlockManagerModule.class);
        if (block != null) registry.register(new BlockContentHandler(block));

        CropManagerModule crop = plugin().moduleManager().get(CropManagerModule.class);
        if (crop != null) registry.register(new CropContentHandler(crop));

        plugin().rootCommand().register(new CreateSubCommand(registry));
        log().info("CreateCommand : " + registry.size() + " type(s) de contenu (" + String.join(", ", registry.types()) + ").");
    }

    @Override
    protected void onDisable() { }

    /** Registre des types (accessible aux extensions futures). */
    public ContentTypeRegistry registry() { return registry; }
}
