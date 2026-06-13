package com.mooncore.modules.model.editor;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistance .bbmodel (D7) : save → load round-trip via le système de fichiers. Sans serveur Bukkit.
 */
class RigModelStoreTest {

    @Test
    void saveLoadRoundTrip(@TempDir Path dir) {
        RigModelStore store = new RigModelStore(dir.toFile(), null);

        EditableRig rig = new EditableRig("golem");
        EditableBone body = rig.addCube("body", new Vector3f(0, 0, 0), new Vector3f(1, 2, 1), null);
        body.pivot.set(0.5f, 0f, 0.5f);
        EditableBone head = rig.addCube("head", new Vector3f(0, 2, 0), new Vector3f(1, 3, 1), null);
        head.parent = "body";
        head.pivot.set(0.5f, 2f, 0.5f);

        assertTrue(store.save(rig));
        assertTrue(store.exists("golem"));
        assertTrue(store.list().contains("golem"));

        EditableRig loaded = store.load("golem");
        assertNotNull(loaded);
        assertEquals(2, loaded.bones.size());

        EditableBone lHead = loaded.bone("head");
        assertNotNull(lHead);
        assertEquals("body", lHead.parent);
        assertEquals(new Vector3f(0, 2, 0), lHead.from);
        assertEquals(new Vector3f(1, 3, 1), lHead.to);
        assertEquals(2f, lHead.pivot.y, 1e-4);

        assertTrue(store.delete("golem"));
        assertFalse(store.exists("golem"));
    }

    @Test
    void rejectsInvalidId(@TempDir Path dir) {
        RigModelStore store = new RigModelStore(dir.toFile(), null);
        EditableRig bad = new EditableRig("../evil");
        assertFalse(store.save(bad));
        assertFalse(store.exists("../evil"));
    }
}
