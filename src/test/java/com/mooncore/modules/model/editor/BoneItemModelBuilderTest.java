package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Génération du modèle d'item JSON d'un os texturé (Étape D4). Pur, sans serveur.
 */
class BoneItemModelBuilderTest {

    @Test
    void buildsElementsWithScaledCoordsAndFaces() {
        EditableBone b = new EditableBone("head");
        b.from.set(0, 0, 0);
        b.to.set(1, 1, 1);                 // 1 bloc → 16 px
        b.setUv(CubeFace.UP, 0, 0, 8, 8);

        String json = BoneItemModelBuilder.modelJson(b, "golem_head");

        assertTrue(json.contains("mooncore:item/golem_head"), json);
        assertTrue(json.contains("\"from\": [0, 0, 0]"), json);
        assertTrue(json.contains("\"to\": [16, 16, 16]"), json);   // ×16
        assertTrue(json.contains("\"north\""), json);
        assertTrue(json.contains("\"up\": { \"uv\": [0, 0, 8, 8]"), json); // UV explicite
        assertTrue(json.contains("\"texture\": \"#0\""), json);
    }

    @Test
    void itemDefinitionPointsToModel() {
        String def = BoneItemModelBuilder.itemDefinitionJson("golem_head");
        assertTrue(def.contains("minecraft:model"), def);
        assertTrue(def.contains("mooncore:item/golem_head"), def);
    }

    @Test
    void defaultUvWhenFaceUnset() {
        EditableBone b = new EditableBone("c");
        b.from.set(0, 0, 0);
        b.to.set(0.5f, 0.5f, 0.5f);
        String json = BoneItemModelBuilder.modelJson(b, "c");
        assertTrue(json.contains("\"to\": [8, 8, 8]"), json);
        // faces sans UV explicite → UV par défaut 0..16
        assertTrue(json.contains("\"south\": { \"uv\": [0, 0, 16, 16]"), json);
    }
}
