package com.mooncore.modules.model.editor;

import com.mooncore.modules.customitem.ResourcePackBuilder;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Émet dans le resource pack les <b>modèles d'item par os texturé</b> d'un {@link EditableRig}
 * (Étape D4) : pour chaque os portant un {@code itemModelKey}, écrit l'item-définition et le modèle
 * {@code elements} ({@link BoneItemModelBuilder}) et copie la texture depuis {@code <textureSource>/<key>.png}.
 * Ces modèles permettent de rendre un os via un ItemDisplay texturé (le calibrage de l'entité reste à
 * faire en jeu) et alimentent l'export (Étape D6).
 */
public final class RigModelPackBuilder {

    private final MoonLogger log;

    public RigModelPackBuilder(MoonLogger log) {
        this.log = log;
    }

    /** @return nombre de modèles d'os émis. */
    public int build(EditableRig rig, File outputDir, File textureSource, List<String> warnings) {
        if (rig == null) return 0;
        if (warnings == null) warnings = new ArrayList<>();
        File assets = new File(outputDir, "assets/" + ResourcePackBuilder.NS);
        File itemsDef = new File(assets, "items");
        File modelsItem = new File(assets, "models/item");
        File texturesItem = new File(assets, "textures/item");
        itemsDef.mkdirs();
        modelsItem.mkdirs();
        texturesItem.mkdirs();

        int models = 0;
        for (EditableBone bone : rig.bones) {
            if (bone.itemModelKey == null || bone.itemModelKey.isBlank()) continue;
            String key = bone.itemModelKey.toLowerCase(Locale.ROOT);
            write(new File(itemsDef, key + ".json"), BoneItemModelBuilder.itemDefinitionJson(key), warnings);
            write(new File(modelsItem, key + ".json"), BoneItemModelBuilder.modelJson(bone, key), warnings);
            models++;

            if (textureSource != null) {
                File png = new File(textureSource, key + ".png");
                if (png.isFile()) {
                    try {
                        Files.copy(png.toPath(), new File(texturesItem, key + ".png").toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException io) {
                        warnings.add("Copie texture os échouée : " + png.getName() + " (" + io.getMessage() + ")");
                    }
                } else {
                    warnings.add("Texture d'os manquante : " + key + ".png — rendu par défaut.");
                }
            }
        }
        for (String w : warnings) if (log != null) log.warn("[RigModelPack] " + w);
        return models;
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture échouée : " + file.getName() + " (" + e.getMessage() + ")");
        }
    }
}
