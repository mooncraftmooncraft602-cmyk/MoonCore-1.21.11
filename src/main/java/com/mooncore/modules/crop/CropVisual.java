package com.mooncore.modules.crop;

import com.mooncore.modules.customitem.ResourcePackBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Rendu des étapes de croissance d'une {@link CropDef} (Étape C2).
 *
 * <h2>Choix de représentation — ItemDisplay texturé (et pas note_block)</h2>
 * Chaque plant posé est matérialisé par un <b>{@link ItemDisplay}</b> dont l'item porte le composant
 * <b>item_model</b> de l'étape ({@code mooncore:<modelKey>_stage<n>}, généré dans le resource pack).
 * Raisons de ne PAS réutiliser la technique note_block des blocs custom :
 * <ul>
 *   <li><b>Budget d'états</b> : les blocs custom plafonnent à 800 états note_block (partagés) ; une
 *       culture à N étapes × M variétés épuiserait vite ce budget. Les Display entities sont illimités.</li>
 *   <li><b>Texturable & moderne</b> : item_model permet n'importe quelle texture par étape, cohérent
 *       avec le reste de la ligne 1.21.11.</li>
 *   <li><b>Mise à jour d'étape triviale</b> : changer l'item du display ({@link #setStage}) sans
 *       toucher au monde ni recalculer un état de bloc.</li>
 *   <li><b>Transitoire</b> : le display n'est <b>pas</b> persistant ; la vérité vit dans la table
 *       {@code mooncore_crop_placement} (Étape C3) et le display est reconstruit au ChunkLoad (C4).</li>
 * </ul>
 * Fallback sans resource pack : l'item de base ({@link Material#WHEAT}) reste visible (plante générique).
 */
public final class CropVisual {

    private CropVisual() {}

    /** Échelle du plant (un peu plus petit qu'un bloc plein). */
    private static final float SCALE = 0.8f;

    /** Fait apparaître le display d'un plant à l'étape donnée, au-dessus de son bloc support. */
    public static ItemDisplay spawn(World world, Location blockLoc, CropDef def, int stage) {
        Location at = center(blockLoc);
        return world.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(stageItem(def, stage));
            e.setPersistent(false);           // transitoire : reconstruit depuis la table au ChunkLoad
            e.setBillboard(Display.Billboard.FIXED);
            e.setTransformation(transform());
        });
    }

    /** Met à jour l'étape affichée d'un display existant. */
    public static void setStage(ItemDisplay display, CropDef def, int stage) {
        if (display != null && display.isValid()) {
            display.setItemStack(stageItem(def, stage));
        }
    }

    /** Construit l'item texturé (item_model) représentant une étape de la culture. */
    public static ItemStack stageItem(CropDef def, int stage) {
        ItemStack item = new ItemStack(Material.WHEAT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(ResourcePackBuilder.NS, def.stageModelKey(stage)));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Centre horizontal du bloc support, posé sur sa face supérieure. */
    private static Location center(Location blockLoc) {
        return new Location(blockLoc.getWorld(),
                blockLoc.getBlockX() + 0.5, blockLoc.getBlockY() + 0.05, blockLoc.getBlockZ() + 0.5);
    }

    private static Transformation transform() {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 1f, 0f),
                new Vector3f(SCALE, SCALE, SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f));
    }
}
