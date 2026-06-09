package com.mooncore.modules.customitem;

import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Génère les assets d'<b>ARMURE PORTÉE custom</b> (Minecraft 1.21.2+ : composant
 * {@code minecraft:equippable} + equipment models). Pour chaque définition avec un
 * {@code equipment-key}, écrit :
 * <pre>
 *   assets/mooncore/models/equipment/&lt;key&gt;.json          (déclare les couches humanoid / humanoid_leggings)
 *   assets/mooncore/textures/entity/equipment/humanoid/&lt;key&gt;.png           (casque/plastron/bottes)
 *   assets/mooncore/textures/entity/equipment/humanoid_leggings/&lt;key&gt;.png   (jambières)
 * </pre>
 * Les PNG sources viennent de {@code armor-textures/} : {@code <key>_body.png} (couche humanoïde),
 * {@code <key>_legs.png} (couche jambières), ou un simple {@code <key>.png} = couche corps.
 * <p>
 * Une couche n'est déclarée dans le JSON que si sa texture existe → pas de couche fantôme.
 * L'objet porte ensuite le composant equippable pointant {@code mooncore:<key>}
 * (cf. {@link CustomItemFactory}). C'est ce qui rend l'armure visible <b>sur le corps</b> du
 * joueur (3e personne) au lieu de la texture vanilla.
 */
public final class EquipmentPackBuilder {

    public record Result(int models, int copied) {}

    private final MoonLogger log;

    public EquipmentPackBuilder(MoonLogger log) {
        this.log = log;
    }

    /**
     * @param defs        définitions (seules celles avec {@code equipment-key} sont traitées)
     * @param outputDir   racine du pack (assets/mooncore/… créé dessous)
     * @param armorSource dossier {@code armor-textures/} contenant les PNG, peut être null
     * @param warnings    liste partagée pour journaliser les soucis (textures manquantes…)
     */
    public Result build(Map<String, CustomItemDef> defs, File outputDir, File armorSource, List<String> warnings) {
        File assets = new File(outputDir, "assets/" + ResourcePackBuilder.NS);
        File modelsEquip = new File(assets, "models/equipment");
        File texBody = new File(assets, "textures/entity/equipment/humanoid");
        File texLegs = new File(assets, "textures/entity/equipment/humanoid_leggings");

        int models = 0, copied = 0;
        Set<String> done = new HashSet<>();
        for (CustomItemDef def : defs.values()) {
            String key = def.equipmentKey();
            if (key == null || key.isBlank() || !done.add(key)) continue;

            File body = resolve(armorSource, key, "_body", "");   // <key>_body.png ou <key>.png
            File legs = resolve(armorSource, key, "_legs");        // <key>_legs.png
            boolean hasBody = body != null, hasLegs = legs != null;
            if (!hasBody && !hasLegs) {
                warnings.add("Armure « " + key + " » : aucune texture trouvée dans armor-textures/ "
                        + "(" + key + "_body.png / " + key + "_legs.png / " + key + ".png).");
                continue;
            }

            modelsEquip.mkdirs();
            writeEquipmentModel(modelsEquip, key, hasBody, hasLegs, warnings);
            models++;

            if (hasBody) { texBody.mkdirs(); copied += copy(body, new File(texBody, key + ".png"), warnings); }
            if (hasLegs) { texLegs.mkdirs(); copied += copy(legs, new File(texLegs, key + ".png"), warnings); }
        }
        if (models > 0) log.info("[ResourcePack] Armures portées custom : " + models + " asset(s), " + copied + " texture(s).");
        return new Result(models, copied);
    }

    /** Modèle d'équipement : déclare uniquement les couches dont la texture existe. */
    private void writeEquipmentModel(File dir, String key, boolean body, boolean legs, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"layers\": {\n");
        List<String> layers = new java.util.ArrayList<>();
        if (body) layers.add("    \"humanoid\": [ { \"texture\": \"" + ResourcePackBuilder.NS + ":" + key + "\" } ]");
        if (legs) layers.add("    \"humanoid_leggings\": [ { \"texture\": \"" + ResourcePackBuilder.NS + ":" + key + "\" } ]");
        sb.append(String.join(",\n", layers)).append("\n  }\n}\n");
        write(new File(dir, key + ".json"), sb.toString(), warnings);
    }

    /** Premier suffixe dont {@code <key><suffix>.png} existe, ou null. */
    private static File resolve(File dir, String key, String... suffixes) {
        if (dir == null) return null;
        for (String suf : suffixes) {
            File f = new File(dir, key + suf + ".png");
            if (f.isFile()) return f;
        }
        return null;
    }

    private int copy(File src, File dst, List<String> warnings) {
        try {
            Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return 1;
        } catch (IOException e) {
            warnings.add("Copie texture armure échouée : " + src.getName() + " (" + e.getMessage() + ")");
            return 0;
        }
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture équipement échouée : " + file.getName() + " (" + e.getMessage() + ")");
        }
    }
}
