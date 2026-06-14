package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapping UV de {@link RigToItemModel#toItemModel} : UV explicite par face conservé, défaut sinon ;
 * coordonnées blocs → pixels (×16). Pur, sans serveur.
 */
class RigToItemModelTest {

    @Test
    void explicitUvAndDefaults() {
        EditableRig rig = new EditableRig("r");
        EditableBone b = rig.addCube("cube", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        b.setUv(CubeFace.UP, 1, 2, 3, 4);

        String json = RigToItemModel.toItemModel(rig, "tex");

        assertTrue(json.contains("mooncore:item/tex"), json);
        assertTrue(json.contains("\"to\": [16, 16, 16]"), json);            // 1 bloc → 16 px
        assertTrue(json.contains("\"up\": { \"uv\": [1, 2, 3, 4]"), json);  // UV explicite
        assertTrue(json.contains("\"north\": { \"uv\": [0, 0, 16, 16]"), json); // face sans UV → défaut
    }

    @Test
    void multipleBonesEmitMultipleElements() {
        EditableRig rig = new EditableRig("r");
        rig.addCube("a", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        rig.addCube("b", new Vector3f(0, 1, 0), new Vector3f(1, 2, 1), null);
        String json = RigToItemModel.toItemModel(rig, "t");
        assertTrue(json.contains("\"name\": \"a\""), json);
        assertTrue(json.contains("\"name\": \"b\""), json);
    }
}
