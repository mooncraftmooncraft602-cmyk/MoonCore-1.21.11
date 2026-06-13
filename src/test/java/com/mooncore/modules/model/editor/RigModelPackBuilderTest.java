package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Émission des modèles d'item par os texturé (Étape D4). Écrit dans un dossier temporaire,
 * sans serveur Bukkit.
 */
class RigModelPackBuilderTest {

    @Test
    void emitsModelsOnlyForTexturedBones(@TempDir Path dir) throws Exception {
        EditableRig rig = new EditableRig("golem");
        EditableBone head = rig.addCube("head", new Vector3f(0, 0, 0), new Vector3f(1, 1, 1), null);
        head.itemModelKey = "golem_head";
        rig.addCube("body", new Vector3f(0, 0, 0), new Vector3f(1, 2, 1), null); // sans texture → ignoré

        int models = new RigModelPackBuilder(null).build(rig, dir.toFile(), null, new ArrayList<>());

        assertEquals(1, models);
        File items = new File(dir.toFile(), "assets/mooncore/items/golem_head.json");
        File model = new File(dir.toFile(), "assets/mooncore/models/item/golem_head.json");
        assertTrue(items.isFile(), "item-definition écrite");
        assertTrue(model.isFile(), "modèle écrit");
        String modelJson = Files.readString(model.toPath());
        assertTrue(modelJson.contains("\"elements\""), modelJson);
        assertTrue(modelJson.contains("mooncore:item/golem_head"), modelJson);
        // l'os 'body' n'a pas de modèle
        assertFalse(new File(dir.toFile(), "assets/mooncore/models/item/body.json").exists());
    }
}
