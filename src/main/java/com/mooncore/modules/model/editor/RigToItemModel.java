package com.mooncore.modules.model.editor;

import com.mooncore.modules.customitem.ResourcePackBuilder;

import java.util.Locale;

/**
 * Convertit un {@link EditableRig} entier en un <b>seul modèle d'item JSON</b> ({@code elements}
 * agrégés de tous les os) référencé par {@code item_model} (Étape D6). Permet de poser un modèle 3D
 * complet sur un item tenu en main (au-delà du rig multi-display). Pur, sans serveur.
 * <p>Coordonnées blocs → pixels (×16) ; UV par face via {@link BoneItemModelBuilder} (défaut sinon).
 */
public final class RigToItemModel {

    private RigToItemModel() {}

    private static final CubeFace[] FACES = CubeFace.values();
    private static final float[] DEFAULT_UV = {0f, 0f, 16f, 16f};

    public static String toItemModel(EditableRig rig, String textureKey) {
        String tex = ResourcePackBuilder.NS + ":item/" + textureKey.toLowerCase(Locale.ROOT);

        StringBuilder elements = new StringBuilder();
        boolean first = true;
        for (EditableBone b : rig.bones) {
            if (!first) elements.append(",\n");
            first = false;
            elements.append(element(b));
        }

        return ""
                + "{\n"
                + "  \"textures\": { \"0\": \"" + tex + "\", \"particle\": \"" + tex + "\" },\n"
                + "  \"elements\": [\n"
                + elements + "\n"
                + "  ]\n"
                + "}\n";
    }

    private static String element(EditableBone b) {
        float[] from = {b.from.x * 16f, b.from.y * 16f, b.from.z * 16f};
        float[] to = {b.to.x * 16f, b.to.y * 16f, b.to.z * 16f};
        StringBuilder faces = new StringBuilder();
        for (int i = 0; i < FACES.length; i++) {
            CubeFace f = FACES[i];
            float[] uv = b.uv.getOrDefault(f, DEFAULT_UV);
            if (i > 0) faces.append(",\n");
            faces.append("          \"").append(mcFace(f)).append("\": { \"uv\": ")
                    .append(arr(uv)).append(", \"texture\": \"#0\" }");
        }
        return "    {\n"
                + "      \"name\": \"" + b.name + "\",\n"
                + "      \"from\": " + arr(from) + ",\n"
                + "      \"to\": " + arr(to) + ",\n"
                + "      \"faces\": {\n" + faces + "\n      }\n"
                + "    }";
    }

    private static String mcFace(CubeFace f) {
        return switch (f) {
            case NORTH -> "north";
            case EAST -> "east";
            case SOUTH -> "south";
            case WEST -> "west";
            case UP -> "up";
            case DOWN -> "down";
        };
    }

    private static String arr(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(v[i] == Math.floor(v[i]) ? String.valueOf((long) v[i]) : String.valueOf(v[i]));
        }
        return sb.append("]").toString();
    }
}
