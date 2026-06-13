package com.mooncore.modules.crop;

import com.mooncore.modules.customitem.ResourcePackBuilder;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Génère les assets de resource pack des cultures (Étape C6c) : pour chaque {@link CropDef} et chaque
 * étape, émet un item-model {@code <modelKey>_stage<n>} (définition + modèle plat) et copie sa texture
 * depuis {@code crops-textures/<modelKey>_stage<n>.png}. Réutilise le namespace et le format item-model
 * (1.21.4+) de {@link ResourcePackBuilder}. Les {@link CropVisual} affichent ces modèles par étape.
 * <p>Le plugin n'invente pas de PNG : texture manquante → warning + rendu vanilla (item de base).
 */
public final class CropPackBuilder {

    private final MoonLogger log;

    public CropPackBuilder(MoonLogger log) {
        this.log = log;
    }

    /** @return nombre de modèles d'étape émis. */
    public int build(Map<String, CropDef> defs, File outputDir, File textureSource, List<String> warnings) {
        if (defs == null || defs.isEmpty()) return 0;
        File assets = new File(outputDir, "assets/" + ResourcePackBuilder.NS);
        File itemsDef = new File(assets, "items");
        File modelsItem = new File(assets, "models/item");
        File texturesItem = new File(assets, "textures/item");
        itemsDef.mkdirs();
        modelsItem.mkdirs();
        texturesItem.mkdirs();

        int models = 0;
        for (CropDef def : defs.values()) {
            String base = def.modelKey().toLowerCase(Locale.ROOT);
            for (int stage = 0; stage < def.stages(); stage++) {
                String key = base + "_stage" + stage;
                writeItemDefinition(itemsDef, key, warnings);
                writeModel(modelsItem, key, warnings);
                models++;

                if (textureSource != null) {
                    File png = new File(textureSource, key + ".png");
                    if (png.isFile()) {
                        try {
                            Files.copy(png.toPath(), new File(texturesItem, key + ".png").toPath(),
                                    StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException io) {
                            warnings.add("Copie texture culture échouée : " + png.getName() + " (" + io.getMessage() + ")");
                        }
                    } else {
                        warnings.add("Texture de culture manquante : " + key + ".png — rendu vanilla utilisé.");
                    }
                }
            }
        }
        for (String w : warnings) log.warn("[CropPack] " + w);
        return models;
    }

    private void writeItemDefinition(File itemsDir, String key, List<String> warnings) {
        String json = """
                {
                  "model": { "type": "minecraft:model", "model": "%s:item/%s" }
                }
                """.formatted(ResourcePackBuilder.NS, key);
        write(new File(itemsDir, key + ".json"), json, warnings);
    }

    private void writeModel(File modelsDir, String key, List<String> warnings) {
        String json = """
                {
                  "parent": "item/generated",
                  "textures": { "layer0": "%s:item/%s" }
                }
                """.formatted(ResourcePackBuilder.NS, key);
        write(new File(modelsDir, key + ".json"), json, warnings);
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture échouée : " + file.getName() + " (" + e.getMessage() + ")");
        }
    }
}
