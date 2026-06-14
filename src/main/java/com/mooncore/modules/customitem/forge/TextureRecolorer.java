package com.mooncore.modules.customitem.forge;

import java.awt.image.BufferedImage;

/**
 * Recolore une texture en remappant chaque pixel <b>existant</b> sur une {@link ThemePalette}, selon sa
 * luminance. Aucun pixel n'est ajouté, retiré ni déplacé : l'alpha (forme) et l'ordre des luminances
 * (ombrage, volume) sont conservés ; seules les teintes changent. C'est exactement « recolorer les pixels
 * déjà présents en fonction du thème de l'arme/minerai/armure ».
 *
 * <p>{@code strength} ∈ [0,1] dose le remplacement : 1 = thème pur (recolor total), valeurs plus basses
 * mélangent avec la couleur d'origine (garde une part du matériau de base). Pur (opère sur des entiers
 * ARGB) → testable sans serveur d'affichage.</p>
 */
public final class TextureRecolorer {

    private TextureRecolorer() {}

    /** Luminance perçue d'un RGB (0xRRGGBB), normalisée [0,1] (Rec. 601). */
    public static double luminance(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    /**
     * Recolore un pixel ARGB : conserve l'alpha, remappe la couleur sur la palette par luminance, puis
     * mélange avec l'original selon {@code strength}. Les pixels totalement transparents sont laissés tels quels.
     */
    public static int recolorPixel(int argb, ThemePalette palette, double strength) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return argb;                       // transparent : intouché (forme préservée)
        double s = strength < 0 ? 0 : (strength > 1 ? 1 : strength);
        int original = argb & 0xFFFFFF;
        int themed = palette.colorAt(luminance(original));
        int blended = ThemePalette.lerpRgb(original, themed, s);
        return (a << 24) | blended;
    }

    /**
     * Renvoie une <b>nouvelle</b> image recolorée de mêmes dimensions (l'originale n'est pas modifiée).
     * Chaque pixel passe par {@link #recolorPixel}.
     */
    public static BufferedImage recolor(BufferedImage base, ThemePalette palette, double strength) {
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                out.setRGB(x, y, recolorPixel(base.getRGB(x, y), palette, strength));
            }
        }
        return out;
    }
}
