package com.mooncore.modules.model.editor;

import com.mooncore.modules.customitem.ResourcePackBuilder;

import java.util.Locale;

/**
 * Génère le <b>modèle d'item JSON</b> (format Minecraft {@code elements}) d'un {@link EditableBone}
 * texturé (Étape D4) : la boîte {@code from}→{@code to} (en blocs) devient un cube {@code elements}
 * (en pixels, ×16) avec un mapping UV par face. Référencé par le composant {@code item_model}
 * (namespace {@code mooncore}), ce modèle permet de rendre l'os via un ItemDisplay texturé plutôt
 * qu'un BlockDisplay, et sert de brique à l'export (Étape D6 {@code RigToItemModel}).
 */
public final class BoneItemModelBuilder {

    private BoneItemModelBuilder() {}

    private static final CubeFace[] FACES = CubeFace.values();

    /** UV par défaut (toute la texture) si la face n'a pas d'UV explicite. */
    private static final float[] DEFAULT_UV = {0f, 0f, 16f, 16f};

    /**
     * Construit le JSON de modèle d'item pour un os texturé.
     *
     * @param bone       os à convertir (géométrie + UV par face)
     * @param textureKey clé de texture dans {@code mooncore:item/<textureKey>}
     */
    public static String modelJson(EditableBone bone, String textureKey) {
        String tex = ResourcePackBuilder.NS + ":item/" + textureKey.toLowerCase(Locale.ROOT);
        float[] from = {bone.from.x * 16f, bone.from.y * 16f, bone.from.z * 16f};
        float[] to = {bone.to.x * 16f, bone.to.y * 16f, bone.to.z * 16f};

        StringBuilder faces = new StringBuilder();
        for (int i = 0; i < FACES.length; i++) {
            CubeFace f = FACES[i];
            float[] uv = bone.uv.getOrDefault(f, DEFAULT_UV);
            if (i > 0) faces.append(",\n");
            faces.append("        \"").append(mcFace(f)).append("\": { \"uv\": ")
                    .append(arr(uv)).append(", \"texture\": \"#0\" }");
        }

        return ""
                + "{\n"
                + "  \"textures\": { \"0\": \"" + tex + "\", \"particle\": \"" + tex + "\" },\n"
                + "  \"elements\": [\n"
                + "    {\n"
                + "      \"from\": " + arr(from) + ",\n"
                + "      \"to\": " + arr(to) + ",\n"
                + "      \"faces\": {\n"
                + faces + "\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    /** Définition item-model (1.21.4+) pointant vers le modèle de l'os. */
    public static String itemDefinitionJson(String modelKey) {
        return "{\n  \"model\": { \"type\": \"minecraft:model\", \"model\": \""
                + ResourcePackBuilder.NS + ":item/" + modelKey.toLowerCase(Locale.ROOT) + "\" }\n}\n";
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
            sb.append(fmt(v[i]));
        }
        return sb.append("]").toString();
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
