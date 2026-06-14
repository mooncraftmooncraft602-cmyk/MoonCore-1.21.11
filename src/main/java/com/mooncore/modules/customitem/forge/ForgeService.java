package com.mooncore.modules.customitem.forge;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.paint.PaintManager;
import com.mooncore.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Forge intelligente : à partir d'une <b>texture de base vanilla</b> (ou custom) et d'un <b>nom</b>, produit
 * un item custom dont la texture est la base <b>recolorée selon le thème du nom</b> (ex. « épée du vent » →
 * la diamond_sword recolorée blanc/vert), sans rien dessiner — seuls les pixels existants changent de teinte
 * ({@link TextureRecolorer} + {@link PaletteResolver}). Tourne entièrement côté serveur. Le matériau est
 * déduit du nom de base (DIAMOND_SWORD → arme), donc ça marche pour armes, outils, armures, minerais…
 */
public final class ForgeService {

    private final MoonCore plugin;
    private final CustomItemManagerModule module;

    public ForgeService(MoonCore plugin, CustomItemManagerModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    /** Résultat de forge (pour message/erreur). */
    public record Result(boolean ok, String id, String message) {}

    /** Identifiant d'item depuis un nom libre : sans accent, minuscule, espaces→_, [a-z0-9_-]. */
    public static String slug(String name) {
        String n = PaletteResolver.normalize(name).replace(' ', '_').replaceAll("[^a-z0-9_-]", "");
        n = n.replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isBlank() ? "item_forge" : n;
    }

    /** Devine le {@link Material} de l'item depuis le nom de la texture de base (sinon repli). */
    public static Material guessMaterial(String baseTextureName, Material fallback) {
        if (baseTextureName == null) return fallback;
        Material m = Material.matchMaterial(baseTextureName.toUpperCase(Locale.ROOT).replace("minecraft:", ""));
        return (m != null && m.isItem()) ? m : fallback;
    }

    /**
     * Forge un item : recolore {@code baseTexture} selon {@code displayName} et crée/équipe l'item custom,
     * puis le donne à {@code player} et rafraîchit le resource pack. {@code palette} null = déduite du nom.
     */
    public Result forge(Player player, String baseTexture, String displayName, ThemePalette palette, double strength) {
        if (displayName == null || displayName.isBlank()) return new Result(false, null, "<red>Nom manquant.");
        if (baseTexture == null || baseTexture.isBlank()) return new Result(false, null, "<red>Texture de base manquante (ex. diamond_sword).");

        // 1) résoudre la texture de base (custom → vanilla extrait du client.jar)
        File baseFile = PaintManager.resolveTexture(plugin, baseTexture);
        if (baseFile == null || !baseFile.isFile()) {
            return new Result(false, null, "<red>Texture de base introuvable : <white>" + baseTexture
                    + "<red>. (Le jar client vanilla est-il détecté ? cf. <white>textures.vanilla-jar<red>.)");
        }
        BufferedImage base;
        try {
            base = ImageIO.read(baseFile);
        } catch (Exception e) {
            return new Result(false, null, "<red>Lecture de la texture de base échouée : " + e.getMessage());
        }
        if (base == null) return new Result(false, null, "<red>Texture de base illisible (PNG attendu).");

        // 2) palette (déduite du nom si non fournie) + GÉNÉRATION d'une texture détaillée selon l'archetype
        //    (minerai/gemme/lingot = pixel-art procédural ; outil/armure = silhouette vanilla détaillée).
        ThemePalette pal = palette != null ? palette : PaletteResolver.fromName(displayName);
        TextureSynth.Archetype arch = TextureSynth.archetypeOf(baseTexture);
        int size = Math.max(16, Math.min(64, Math.max(base.getWidth(), base.getHeight())));
        long seed = (displayName + "|" + pal.name()).hashCode() & 0xffffffffL;
        // outils/armes/armures = DESSINÉS depuis zéro (épée, pioche, hache, casque, plastron) ;
        // minerai/gemme/lingot = pixel-art procédural. Plus jamais un simple recolorage plat.
        BufferedImage themed = arch == TextureSynth.Archetype.ITEM
                ? TextureSynth.drawTool(baseTexture, base, pal, seed)
                : TextureSynth.synthesize(arch, base, pal, seed, size);

        // 3) item custom : id depuis le nom, matériau déduit de la base
        String id = slug(displayName);
        CustomItemDef def = module.rawDef(id);
        if (def == null) def = new CustomItemDef(id);
        def.setMaterial(guessMaterial(baseTexture, Material.PAPER));
        def.setDisplayName(displayName);
        def.setModelKey(id);
        if (def.customModelData() <= 0) def.setCustomModelData(module.nextCustomModelData());

        // 4) écrire la texture recolorée dans le dossier sources du pack
        File out = new File(module.texturesFolder(), id + ".png");
        try {
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            ImageIO.write(themed, "png", out);
        } catch (Exception e) {
            return new Result(false, null, "<red>Écriture de la texture forgée échouée : " + e.getMessage());
        }

        // 5) persister + reconstruire le pack + renvoyer aux joueurs
        module.put(def);
        plugin.services().get(ResourcePackService.class).ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });

        // 6) donner l'item au forgeron
        ItemStack stack = module.create(id, 1);
        if (stack != null) {
            for (ItemStack overflow : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        String kind = switch (arch) {
            case ORE -> "minerai"; case GEM -> "gemme"; case INGOT -> "lingot"; case ITEM -> "objet";
        };
        return new Result(true, id, "<green>✦ Forgé <white>" + displayName + "<green> <gray>(" + kind
                + " généré, thème <white>" + pal.name() + "<gray>, cmd " + def.customModelData()
                + "). Pack mis à jour — récupère ton item !");
    }
}
