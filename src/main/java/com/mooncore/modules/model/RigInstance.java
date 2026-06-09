package com.mooncore.modules.model;

import com.mooncore.MoonCore;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Instance VIVANTE d'un {@link RigModel} dans le monde : un BlockDisplay par os, articulés
 * chaque tick par composition matricielle de la chaîne parent→enfant. Le client interpole
 * entre deux poses envoyées (mouvement fluide à faible coût réseau).
 *
 * <p>Composition (en blocs, repère relatif à la base) :
 * <pre>
 *   jointLocal(os) = T(translAnim) · T(pivot) · R(rotAnim) · S(scaleAnim) · T(-pivot)
 *   jointWorld(os) = jointWorld(parent) · jointLocal(os)            ← propagé aux enfants
 *   meshMatrix(os) = jointWorld(os) · T(from) · S(to-from)         ← remplissage de la boîte (local au display)
 * </pre>
 * La boîte (T(from)·S(size)) n'est PAS propagée aux enfants → seules les articulations le sont.
 */
public final class RigInstance {

    private final MoonCore plugin;
    private final RigModel model;
    private final Map<String, BlockDisplay> displays = new LinkedHashMap<>();

    private Animation base;        // animation de fond (en boucle : idle/walk)
    private double baseClock;
    private Animation override;    // animation ponctuelle (one-shot : attaque…) qui prime puis rend la main
    private double overrideClock;
    private boolean overriding;

    public RigInstance(MoonCore plugin, RigModel model) {
        this.plugin = plugin;
        this.model = model;
    }

    public RigModel model() { return model; }
    public boolean alive() { return displays.values().stream().anyMatch(d -> d != null && d.isValid()); }

    /** Fait apparaître un BlockDisplay par os à {@code base} et applique la pose de repos. */
    public void spawn(Location base) {
        if (base.getWorld() == null) return;
        for (RigBone bone : model.bones) {
            if (bone.block == null) continue; // os « articulation pure » (groupe sans cube) : pas de display
            BlockDisplay d = base.getWorld().spawn(base, BlockDisplay.class, e -> {
                e.setBlock(bone.block);
                e.setPersistent(false);
                e.setBillboard(Display.Billboard.FIXED);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setViewRange(2.0f);
            });
            displays.put(bone.name, d);
        }
        applyPose(0);
    }

    /** Lance l'animation de FOND (en boucle) par nom ; ignore silencieusement si absente. */
    public void play(String animation) {
        Animation a = model.animation(animation);
        if (a != null) { this.base = a; this.baseClock = 0; }
    }

    /**
     * Joue une animation PONCTUELLE (one-shot, ex. attaque) qui prime sur l'animation de fond
     * pendant sa durée, puis rend la main. No-op si l'animation est absente.
     */
    public void playOnce(String animation) {
        Animation a = model.animation(animation);
        if (a != null) { this.override = a; this.overrideClock = 0; this.overriding = true; }
    }

    /** Avance les horloges de {@code dtSeconds} et pousse la nouvelle pose (lissée sur {@code interpTicks}). */
    public void tick(double dtSeconds, int interpTicks) {
        baseClock += dtSeconds;
        if (overriding) {
            overrideClock += dtSeconds;
            if (override == null || overrideClock >= override.length()) overriding = false;
        }
        applyPose(interpTicks);
    }

    /**
     * Déplace tout le rig vers {@code base} (téléporte chaque display, lissé sur {@code teleportTicks}).
     * Utilisé pour faire SUIVRE une entité (mob/boss rendu invisible) : le rig custom remplace
     * visuellement la créature vanilla.
     */
    public void reanchor(Location base, int teleportTicks) {
        for (BlockDisplay d : displays.values()) {
            if (d == null || !d.isValid()) continue;
            d.setTeleportDuration(Math.max(0, teleportTicks));
            d.teleport(base);
        }
    }

    /** UUID des BlockDisplay (bones) — fournis au mod compagnon pour masquer le fallback vanilla. */
    public java.util.List<java.util.UUID> displayUuids() {
        java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
        for (BlockDisplay d : displays.values()) if (d != null) out.add(d.getUniqueId());
        return out;
    }

    /** Retire toutes les entités du rig. */
    public void remove() {
        for (BlockDisplay d : displays.values()) if (d != null && d.isValid()) d.remove();
        displays.clear();
    }

    // ---- interne ----

    private void applyPose(int interpTicks) {
        Animation active = overriding ? override : base;
        double t = overriding ? overrideClock : baseClock;
        Map<String, Matrix4f> world = new HashMap<>();
        for (RigBone bone : model.bones) {
            Animation.Pose p = active != null ? active.sample(bone.name, t) : Animation.Pose.rest();

            Matrix4f joint = new Matrix4f()
                    .translate(p.translation())
                    .translate(bone.pivot)
                    .rotateXYZ(rad(p.rotationDeg().x), rad(p.rotationDeg().y), rad(p.rotationDeg().z))
                    .scale(p.scale())
                    .translate(-bone.pivot.x, -bone.pivot.y, -bone.pivot.z);

            Matrix4f parentWorld = (bone.parent != null && world.containsKey(bone.parent))
                    ? world.get(bone.parent) : new Matrix4f();
            Matrix4f boneWorld = new Matrix4f(parentWorld).mul(joint);
            world.put(bone.name, boneWorld);

            Vector3f size = bone.size();
            Matrix4f mesh = new Matrix4f(boneWorld).translate(bone.from).scale(size.x, size.y, size.z);

            BlockDisplay d = displays.get(bone.name);
            if (d != null && d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(interpTicks);
                d.setTransformationMatrix(mesh);
            }
        }
    }

    private static float rad(float deg) { return (float) Math.toRadians(deg); }
}
