package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Import « glisser-déposer » de textures : l'admin dépose des PNG dans
 * {@code plugins/MoonCore/items-textures/<id>.png} puis lance {@code /moon studio import}.
 * Chaque PNG dont le nom correspond à un objet custom existant est <b>lié</b> à cet objet
 * (assignation du {@code model-key} et d'un {@code custom-model-data} libre si besoin),
 * puis le pack est reconstruit <b>une seule fois</b> via {@link ResourcePackService#requestRebuild()}.
 *
 * <p>Objectif « création facile » : plus besoin de connaître les commandes de binding —
 * on dépose l'image, on tape une commande, l'objet porte sa texture.
 */
public final class StudioImport {

    private StudioImport() {}

    /** Résultat agrégé d'un import (pour l'affichage). */
    public record Result(int bound, int alreadyOk, List<String> unmatched) {}

    public static void run(MoonCore plugin, Player p) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) {
            p.sendMessage(Text.mm("<red>Module objets custom inactif."));
            return;
        }
        Result r = importItems(ci);
        int armorBound = importArmor(ci);

        ResourcePackService rp = plugin.services().get(ResourcePackService.class).orElse(null);
        if ((r.bound() > 0 || armorBound > 0) && rp != null) rp.requestRebuild();

        p.sendMessage(Text.mm("<gradient:#8a2be2:#c77dff>Import textures</gradient> <dark_gray>—"));
        p.sendMessage(Text.mm(" <green>" + r.bound() + "</green> <gray>texture(s) d'objet liée(s)"
                + (r.bound() > 0 || armorBound > 0 ? " <dark_gray>(pack reconstruit)" : "")));
        if (armorBound > 0) p.sendMessage(Text.mm(" <green>" + armorBound + "</green> <gray>armure(s) portée(s) custom activée(s)"));
        if (r.alreadyOk() > 0) p.sendMessage(Text.mm(" <dark_gray>" + r.alreadyOk() + " déjà à jour"));
        if (!r.unmatched().isEmpty()) {
            p.sendMessage(Text.mm(" <yellow>" + r.unmatched().size() + " PNG sans objet correspondant :"));
            String preview = String.join(", ", r.unmatched().subList(0, Math.min(8, r.unmatched().size())));
            p.sendMessage(Text.mm(" <dark_gray>" + preview + (r.unmatched().size() > 8 ? "…" : "")));
            p.sendMessage(Text.mm(" <dark_gray>crée d'abord l'objet : <white>/moon item create <id></white> (le nom du PNG = l'id)"));
        }
    }

    /**
     * Lie chaque {@code <id>.png} de {@code items-textures/} à l'objet {@code <id>} : pose
     * {@code model-key} et un {@code custom-model-data} libre si absent. Renvoie le bilan.
     */
    public static Result importItems(CustomItemManagerModule ci) {
        int bound = 0, alreadyOk = 0;
        List<String> unmatched = new ArrayList<>();

        File dir = ci.texturesFolder();
        File[] pngs = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (pngs == null) return new Result(0, 0, unmatched);

        for (File png : pngs) {
            String id = png.getName().substring(0, png.getName().length() - ".png".length())
                    .toLowerCase(Locale.ROOT);
            CustomItemDef def = ci.rawDef(id);
            if (def == null) { unmatched.add(id); continue; }

            boolean changed = false;
            if (def.modelKey() == null || def.modelKey().isBlank()) { def.setModelKey(id); changed = true; }
            if (def.customModelData() <= 0) { def.setCustomModelData(ci.nextCustomModelData()); changed = true; }

            if (changed) { ci.put(def); bound++; }
            else alreadyOk++;
        }
        return new Result(bound, alreadyOk, unmatched);
    }

    /**
     * Active l'armure portée custom pour chaque objet dont une texture d'armure existe dans
     * {@code armor-textures/} : {@code <id>_body.png}, {@code <id>_legs.png} ou {@code <id>.png}.
     * Pose {@code equipment-key = id} sur la définition si absent. Renvoie le nombre d'objets activés.
     */
    public static int importArmor(CustomItemManagerModule ci) {
        File dir = ci.armorTexturesFolder();
        File[] pngs = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (pngs == null) return 0;

        java.util.Set<String> ids = new java.util.HashSet<>();
        for (File png : pngs) {
            String base = png.getName().substring(0, png.getName().length() - ".png".length())
                    .toLowerCase(Locale.ROOT);
            if (base.endsWith("_body") || base.endsWith("_legs")) base = base.substring(0, base.length() - 5);
            ids.add(base);
        }
        int bound = 0;
        for (String id : ids) {
            CustomItemDef def = ci.rawDef(id);
            if (def == null) continue;
            if (def.equipmentKey() == null) { def.setEquipmentKey(id); ci.put(def); bound++; }
        }
        return bound;
    }
}
