package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cuisson d'un rig en un seul item-model (Étape D6). Écrit dans un dossier temporaire, sans serveur.
 */
class RigBakeBuilderTest {

    @Test
    void bakesAggregatedItemModel(@TempDir Path dir) throws Exception {
        EditableRig rig = new EditableRig("golem");
        rig.addCube("body", new Vector3f(0, 0, 0), new Vector3f(1, 2, 1), null);
        rig.addCube("head", new Vector3f(0, 2, 0), new Vector3f(1, 3, 1), null);

        boolean ok = new RigBakeBuilder(null).bake(rig, dir.toFile(), "golem_statue", null, new ArrayList<>());
        assertTrue(ok);

        File def = new File(dir.toFile(), "assets/mooncore/items/golem_statue.json");
        File model = new File(dir.toFile(), "assets/mooncore/models/item/golem_statue.json");
        assertTrue(def.isFile(), "item-definition écrite");
        assertTrue(model.isFile(), "modèle écrit");

        String json = Files.readString(model.toPath());
        assertTrue(json.contains("\"elements\""), json);
        assertTrue(json.contains("\"name\": \"body\""), json);
        assertTrue(json.contains("\"name\": \"head\""), json);
        assertTrue(json.contains("mooncore:item/golem_statue"), json);
    }
}
