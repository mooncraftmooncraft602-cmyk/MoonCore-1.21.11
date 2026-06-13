package com.mooncore.modules.model.editor;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sélection d'un cube de l'éditeur 3D par <b>raytrace AABB</b> (Étape D2) : projette le regard du
 * joueur sur les boîtes des os (en pose de repos) et retourne le plus proche touché. Extension AABB
 * du principe de {@code PaintRaytracer} (qui projette sur un plan d'item frame).
 * <p>Les coordonnées des os sont en blocs, relatives à l'ancre du rig ; la boîte monde d'un os est
 * {@code [anchor + from*scale, anchor + to*scale]} (pose de repos, sans rotation d'animation).
 */
public final class RigRaytracer {

    private RigRaytracer() {}

    /** @return nom de l'os touché le plus proche, ou {@code null} si le regard ne croise aucun cube. */
    public static String pick(Player p, EditableRig rig, Location anchor, float scale, double maxDistance) {
        Vector origin = p.getEyeLocation().toVector();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Vector base = anchor.toVector();

        String best = null;
        double bestT = maxDistance;
        for (EditableBone b : rig.bones) {
            Vector min = base.clone().add(new Vector(b.from.x * scale, b.from.y * scale, b.from.z * scale));
            Vector max = base.clone().add(new Vector(b.to.x * scale, b.to.y * scale, b.to.z * scale));
            double t = rayAabb(origin, dir, min, max);
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = b.name;
            }
        }
        return best;
    }

    /**
     * Intersection rayon→AABB par la méthode des « slabs ». Retourne la distance d'entrée {@code t}
     * (≥ 0) le long du rayon, ou {@code -1} si pas d'intersection devant l'origine.
     */
    private static double rayAabb(Vector o, Vector d, Vector min, Vector max) {
        double tmin = Double.NEGATIVE_INFINITY, tmax = Double.POSITIVE_INFINITY;
        double[] od = {o.getX(), o.getY(), o.getZ()};
        double[] dd = {d.getX(), d.getY(), d.getZ()};
        double[] mn = {min.getX(), min.getY(), min.getZ()};
        double[] mx = {max.getX(), max.getY(), max.getZ()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1e-8) {
                if (od[i] < mn[i] || od[i] > mx[i]) return -1; // parallèle et hors de la slab
            } else {
                double t1 = (mn[i] - od[i]) / dd[i];
                double t2 = (mx[i] - od[i]) / dd[i];
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1;
            }
        }
        if (tmax < 0) return -1;          // boîte derrière l'origine
        return tmin >= 0 ? tmin : tmax;   // entrée, ou origine déjà dans la boîte
    }
}
