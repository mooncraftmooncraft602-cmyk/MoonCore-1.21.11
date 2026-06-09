package com.mooncore.modules.customitem;

import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Génère un resource pack <b>moderne (Minecraft 1.21.4+)</b> à partir des définitions qui
 * déclarent un {@code model-key}. Utilise le système <b>item-model</b> : chaque objet a son
 * propre fichier {@code assets/mooncore/items/<key>.json} sélectionné par le composant
 * {@code minecraft:item_model} posé sur l'ItemStack ({@link CustomItemFactory}). Plus de
 * {@code custom_model_data} numérique : clés string, par-objet, <b>zéro collision</b>.
 * <p>
 * Le plugin <b>n'invente pas</b> de PNG : il copie les textures existantes depuis un dossier
 * source ({@code items-textures/}) et journalise un warning pour chaque texture manquante
 * (fallback : rendu vanilla du matériau de base).
 * <p>
 * Compat Bedrock : les item-models Java ne sont pas rendus via Geyser → l'objet s'affiche
 * avec sa texture vanilla. Le lore (toujours présent) reste la source de vérité visuelle.
 */
public final class ResourcePackBuilder {

    public record Result(int models, int copied, List<String> warnings) {}

    /** Namespace dédié du pack : isole nos assets de {@code minecraft} (aucune collision). */
    public static final String NS = "mooncore";

    private final MoonLogger log;

    public ResourcePackBuilder(MoonLogger log) {
        this.log = log;
    }

    /**
     * @param defs           définitions chargées
     * @param outputDir      dossier de sortie du pack (sera créé/écrasé)
     * @param textureSource  dossier contenant les PNG ({@code <model-key>.png}), peut être null
     */
    public Result build(Map<String, CustomItemDef> defs, File outputDir, File textureSource) {
        List<String> warnings = new ArrayList<>();
        writePackMeta(outputDir, warnings);

        File assets = new File(outputDir, "assets/" + NS);
        File itemsDef = new File(assets, "items");          // définitions item-model (1.21.4+)
        File modelsItem = new File(assets, "models/item");  // modèles plats/3D
        File texturesItem = new File(assets, "textures/item");
        itemsDef.mkdirs();
        modelsItem.mkdirs();
        texturesItem.mkdirs();

        int models = 0, copied = 0;
        Set<String> done = new HashSet<>();
        for (CustomItemDef def : defs.values()) {
            String key = def.modelKey() == null ? null : def.modelKey().toLowerCase(Locale.ROOT);
            if (key == null || key.isBlank() || !done.add(key)) continue;

            boolean handheld = isHandheld(def.material().name().toLowerCase(Locale.ROOT));
            writeItemDefinition(itemsDef, key, warnings);
            writeModel(modelsItem, key, handheld, warnings);
            models++;

            if (textureSource != null) {
                File png = new File(textureSource, key + ".png");
                if (png.isFile()) {
                    try {
                        Files.copy(png.toPath(), new File(texturesItem, key + ".png").toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        // Animation : copie le .png.mcmeta s'il existe (bande de frames).
                        File mcmeta = new File(textureSource, key + ".png.mcmeta");
                        if (mcmeta.isFile()) Files.copy(mcmeta.toPath(),
                                new File(texturesItem, key + ".png.mcmeta").toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    } catch (IOException io) {
                        warnings.add("Copie texture échouée : " + png.getName() + " (" + io.getMessage() + ")");
                    }
                } else {
                    warnings.add("Texture manquante : " + key + ".png — rendu vanilla utilisé.");
                }
            }
        }
        if (models == 0) warnings.add("Aucune définition ne déclare de modèle (model-key).");
        warnings.add("Bedrock : les item-models ne sont pas rendus via Geyser ; le lore reste la source visuelle.");
        for (String w : warnings) log.warn("[ResourcePack] " + w);
        return new Result(models, copied, warnings);
    }

    private void writePackMeta(File outputDir, List<String> warnings) {
        outputDir.mkdirs();
        // Build 1.21.11 : pack_format 75 (les versions 1.21.9+ utilisent une notation décimale,
        // l'entier 75 reste valide dans pack.mcmeta). supported_formats large = tolérant aux
        // patchs voisins de la branche 1.21.x.
        String meta = """
                {
                  "pack": {
                    "pack_format": 75,
                    "supported_formats": { "min_inclusive": 64, "max_inclusive": 99 },
                    "description": "MoonCore - objets custom"
                  }
                }
                """;
        write(new File(outputDir, "pack.mcmeta"), meta, warnings);
    }

    /** Outils/armes = tenus en main (item/handheld) ; le reste = icône plate (item/generated). */
    private static boolean isHandheld(String matName) {
        return matName.endsWith("_sword") || matName.endsWith("_axe") || matName.endsWith("_pickaxe")
                || matName.endsWith("_shovel") || matName.endsWith("_hoe")
                || matName.equals("trident") || matName.equals("mace") || matName.equals("stick")
                || matName.equals("fishing_rod") || matName.equals("carrot_on_a_stick")
                || matName.equals("warped_fungus_on_a_stick") || matName.equals("brush")
                || matName.equals("flint_and_steel") || matName.equals("bow")
                || matName.equals("crossbow") || matName.equals("blaze_rod") || matName.equals("debug_stick");
    }

    /** Définition item-model {@code assets/mooncore/items/<key>.json} (sélectionnée par item_model). */
    private void writeItemDefinition(File itemsDir, String key, List<String> warnings) {
        String json = """
                {
                  "model": { "type": "minecraft:model", "model": "%s:item/%s" }
                }
                """.formatted(NS, key);
        write(new File(itemsDir, key + ".json"), json, warnings);
    }

    /** Modèle référencé par la définition : icône plate ou tenue en main, texture du namespace. */
    private void writeModel(File modelsDir, String key, boolean handheld, List<String> warnings) {
        String parent = handheld ? "item/handheld" : "item/generated";
        String json = """
                {
                  "parent": "%s",
                  "textures": { "layer0": "%s:item/%s" }
                }
                """.formatted(parent, NS, key);
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
