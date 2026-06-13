package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.RigBone;
import com.mooncore.modules.model.RigInstance;
import com.mooncore.modules.model.RigModel;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Session d'édition 3D in-game (Étape D2) : matérialise un {@link EditableRig} via un
 * {@link RigInstance} (BlockDisplay par os) à une ancre du monde, et le <b>re-pousse</b> à chaque
 * édition. La sélection d'un cube se fait au regard ({@link RigRaytracer}).
 * <p>Les outils d'édition (Étape D3) passent par {@link #edit(Runnable)} : ils mutent l'EditableRig
 * puis la session re-matérialise. Un os sans bloc reçoit un bloc témoin pour rester visible.
 */
public final class ModelEditorSession {

    private final EditableRig rig;
    private final RigHistory history = new RigHistory();
    private Location anchor;
    private float scale = 1f;
    private RigInstance instance;
    private String selected;          // nom du cube sélectionné
    private BlockData placeholder;    // bloc témoin pour les os sans bloc

    public ModelEditorSession(EditableRig rig, Location anchor) {
        this.rig = rig;
        this.anchor = anchor.clone();
    }

    public EditableRig rig() { return rig; }
    public Location anchor() { return anchor.clone(); }
    public float scale() { return scale; }
    public void setScale(float scale) { this.scale = Math.max(0.05f, Math.min(8f, scale)); }
    public String selected() { return selected; }
    public EditableBone selectedBone() { return selected == null ? null : rig.bone(selected); }
    public void setSelected(String bone) { this.selected = bone; }

    /** (Re)matérialise le rig à l'ancre : retire l'instance courante et en fait apparaître une fraîche. */
    public void materialize() {
        if (instance != null) instance.remove();
        instance = new RigInstance(withPlaceholders(rig.toRigModel()));
        instance.spawn(anchor);
    }

    /** Applique une édition sur le rig (avec snapshot undo) puis re-pousse l'affichage. */
    public void edit(Runnable mutation) {
        history.push(rig);
        mutation.run();
        if (selected != null && rig.bone(selected) == null) selected = null; // cube supprimé
        materialize();
    }

    // ---- Outils hotbar (Étape D3) — opèrent sur le cube sélectionné ----

    /** Déplace le cube sélectionné de {@code (dx,dy,dz)} (sneak = pas fin côté appelant). */
    public boolean translateSelected(float dx, float dy, float dz) {
        return withSelected(b -> ModelEditorTools.translate(b, dx, dy, dz));
    }

    public boolean movePivotSelected(float dx, float dy, float dz) {
        return withSelected(b -> ModelEditorTools.movePivot(b, dx, dy, dz));
    }

    public boolean resizeSelected(float dx, float dy, float dz) {
        return withSelected(b -> ModelEditorTools.resize(b, dx, dy, dz));
    }

    public boolean scaleSelected(float factor) {
        return withSelected(b -> ModelEditorTools.scaleAroundPivot(b, factor));
    }

    public boolean deleteSelected() {
        if (selected == null) return false;
        edit(() -> rig.removeCube(selected));
        return true;
    }

    /** Duplique le cube sélectionné (décalé d'un bloc) et sélectionne la copie. */
    public boolean duplicateSelected() {
        if (selected == null) return false;
        history.push(rig);
        EditableBone copy = ModelEditorTools.duplicate(rig, selected, 1f, 0f, 0f);
        if (copy != null) selected = copy.name;
        materialize();
        return copy != null;
    }

    private boolean withSelected(java.util.function.Consumer<EditableBone> op) {
        EditableBone b = selectedBone();
        if (b == null) return false;
        history.push(rig);
        op.accept(b);
        materialize();
        return true;
    }

    public boolean undo() {
        if (!history.undo(rig)) return false;
        if (selected != null && rig.bone(selected) == null) selected = null;
        materialize();
        return true;
    }

    public boolean redo() {
        if (!history.redo(rig)) return false;
        if (selected != null && rig.bone(selected) == null) selected = null;
        materialize();
        return true;
    }

    public boolean canUndo() { return history.canUndo(); }
    public boolean canRedo() { return history.canRedo(); }

    /** Déplace l'ancre (et le rig matérialisé) vers une nouvelle position. */
    public void moveTo(Location newAnchor) {
        this.anchor = newAnchor.clone();
        materialize();
    }

    /** Sélectionne le cube visé par le regard du joueur ; retourne son nom ou {@code null}. */
    public String selectFromLook(org.bukkit.entity.Player p, double maxDistance) {
        String hit = RigRaytracer.pick(p, rig, anchor, scale, maxDistance);
        if (hit != null) this.selected = hit;
        return hit;
    }

    public void close() {
        if (instance != null) { instance.remove(); instance = null; }
        selected = null;
    }

    public boolean isOpen() { return instance != null; }

    /** Remplace les blocs nuls par un bloc témoin pour que chaque cube reste visible dans l'éditeur. */
    private RigModel withPlaceholders(RigModel model) {
        if (placeholder == null) placeholder = Material.WHITE_STAINED_GLASS.createBlockData();
        List<RigBone> bones = new ArrayList<>(model.bones.size());
        for (RigBone b : model.bones) {
            BlockData block = b.block != null ? b.block : placeholder;
            bones.add(new RigBone(b.name, b.parent, new Vector3f(b.pivot), new Vector3f(b.from),
                    new Vector3f(b.to), block, b.itemModelKey));
        }
        return new RigModel(model.id, bones, model.animations);
    }
}
