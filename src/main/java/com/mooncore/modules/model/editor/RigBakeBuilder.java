package com.mooncore.modules.model.editor;

import com.mooncore.modules.customitem.ResourcePackBuilder;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * « Cuit » un {@link EditableRig} entier en un <b>seul item-model</b> du resource pack (Étape D6) :
 * agrège tous les os via {@link RigToItemModel} et écrit l'item-définition + le modèle {@code elements}
 * sous une clé {@code key}, référençable par le composant {@code item_model} d'un objet tenu en main.
 * Copie la texture {@code <textureSource>/<key>.png} si présente. Permet « voir le modèle 3D sur un item ».
 */
public final class RigBakeBuilder {

    private final MoonLogger log;

    public RigBakeBuilder(MoonLogger log) {
        this.log = log;
    }

    /**
     * Écrit l'item-model agrégé du rig sous {@code key}.
     *
     * @return {@code true} si l'item-définition et le modèle ont été écrits.
     */
    public boolean bake(EditableRig rig, File outputDir, String key, File textureSource, List<String> warnings) {
        if (rig == null || key == null || key.isBlank()) return false;
        String k = key.toLowerCase(Locale.ROOT);
        File assets = new File(outputDir, "assets/" + ResourcePackBuilder.NS);
        File itemsDef = new File(assets, "items");
        File modelsItem = new File(assets, "models/item");
        File texturesItem = new File(assets, "textures/item");
        itemsDef.mkdirs();
        modelsItem.mkdirs();
        texturesItem.mkdirs();

        boolean ok = write(new File(itemsDef, k + ".json"), BoneItemModelBuilder.itemDefinitionJson(k), warnings)
                & write(new File(modelsItem, k + ".json"), RigToItemModel.toItemModel(rig, k), warnings);

        if (textureSource != null) {
            File png = new File(textureSource, k + ".png");
            if (png.isFile()) {
                try {
                    Files.copy(png.toPath(), new File(texturesItem, k + ".png").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException io) {
                    if (warnings != null) warnings.add("Copie texture rig échouée : " + png.getName());
                }
            } else if (warnings != null) {
                warnings.add("Texture du rig cuit manquante : " + k + ".png");
            }
        }
        if (log != null && warnings != null) for (String w : warnings) log.warn("[RigBake] " + w);
        return ok;
    }

    private boolean write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            if (warnings != null) warnings.add("Écriture échouée : " + file.getName() + " (" + e.getMessage() + ")");
            return false;
        }
    }
}
