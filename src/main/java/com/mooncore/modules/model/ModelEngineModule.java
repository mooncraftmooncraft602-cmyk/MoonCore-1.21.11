package com.mooncore.modules.model;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Moteur de modèles/animations <b>maison</b> (aucune dépendance externe type ModelEngine/BetterModel).
 * Convertit un {@link RigModel} en rig de display-entities animé par interpolation de
 * transformations — qualité « display-rig » (≈70-80 % d'un mob moddé) côté client vanilla ;
 * le mod compagnon (phase ultérieure) montera en qualité type GeckoLib.
 *
 * <p>Tick global toutes les {@link #PERIOD} ticks : chaque {@link RigInstance} avance son
 * horloge d'animation et pousse la nouvelle pose. Les rigs orphelins (entités retirées) sont purgés.
 */
@ModuleInfo(id = "model-engine", name = "ModelEngine")
public final class ModelEngineModule extends AbstractModule {

    /** Intervalle (ticks) entre deux mises à jour de pose ; le client interpole entre-temps. */
    private static final int PERIOD = 2;

    /** Rigs actifs, indexés par UUID du propriétaire (joueur pour la démo, entité pour un boss). */
    private final Map<UUID, RigInstance> rigs = new java.util.LinkedHashMap<>(); // ordre d'insertion → éviction du plus ancien au cap
    /** Entité « hôte » suivie par un rig (le mob/boss rendu invisible que le rig remplace). */
    private final Map<UUID, org.bukkit.entity.Entity> hosts = new HashMap<>();
    /** Association persistée bossId → nom de rig (golem ou fichier .bbmodel). */
    private final Map<String, String> bossRig = new HashMap<>();
    private java.io.File bossRigFile;
    private BukkitTask ticker;
    /** Cap de rigs concurrents (anti-runaway) ; 0 = illimité ; éviction du plus ancien au-delà. */
    private int maxRigs = 64;
    /** Distance² (blocs²) au-delà de laquelle un rig n'est plus animé (0 = jamais culler). */
    private double cullDistanceSq = 48 * 48;

    @Override
    protected void onEnable() {
        this.ticker = schedulers().syncTimer(this::tickAll, PERIOD, PERIOD);
        this.bossRigFile = new java.io.File(new java.io.File(plugin().getDataFolder(), "models"), "boss-rigs.yml");
        loadBossRigs();
        this.maxRigs = Math.max(0, moduleConfig().getInt("max-rigs", 64));
        double cull = moduleConfig().getDouble("cull-distance", 48.0);
        this.cullDistanceSq = cull <= 0 ? 0 : cull * cull;
        log().info("ModelEngine prêt (moteur maison) — " + bossRig.size() + " boss riggé(s) ; cap="
                + (maxRigs == 0 ? "∞" : maxRigs) + ", cull=" + (cullDistanceSq == 0 ? "off" : (int) Math.sqrt(cullDistanceSq) + "b") + ".");
    }

    @Override
    protected void onDisable() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (UUID owner : new java.util.ArrayList<>(rigs.keySet())) clear(owner);
    }

    private void tickAll() {
        double dt = PERIOD / 20.0;
        for (UUID owner : new java.util.ArrayList<>(rigs.keySet())) {
            RigInstance r = rigs.get(owner);
            if (r == null) continue;
            org.bukkit.entity.Entity host = hosts.get(owner);
            if (host != null) {
                if (!host.isValid() || host.isDead()) { clear(owner); continue; }
                r.reanchor(host.getLocation(), PERIOD); // suit le mob/boss invisible
            }
            if (!r.alive()) { clear(owner); continue; }
            if (cullDistanceSq > 0 && !hasNearbyPlayer(r, host)) continue; // culling : pas d'anim si aucun joueur proche
            r.tick(dt, PERIOD);
        }
    }

    /** Applique le cap : retire les rigs les plus anciens tant qu'on est au plafond (anti-runaway). */
    private void enforceCap() {
        while (maxRigs > 0 && rigs.size() >= maxRigs && !rigs.isEmpty()) {
            UUID oldest = rigs.keySet().iterator().next();
            log().warn("[ModelEngine] Cap de " + maxRigs + " rigs atteint — retrait du plus ancien.");
            clear(oldest);
        }
    }

    /** Vrai si un joueur est assez proche du rig pour justifier son animation (culling par distance). */
    private boolean hasNearbyPlayer(RigInstance r, org.bukkit.entity.Entity host) {
        Location at = host != null ? host.getLocation() : r.anchor();
        if (at == null || at.getWorld() == null) return true; // position inconnue → on anime par sûreté
        for (Player p : plugin().getServer().getOnlinePlayers()) {
            if (p.getWorld() != at.getWorld()) continue;
            if (p.getLocation().distanceSquared(at) <= cullDistanceSq) return true;
        }
        return false;
    }

    /** Fait apparaître (ou remplace) un rig pour un propriétaire ; lance {@code animation} si non null. */
    public RigInstance spawn(UUID owner, RigModel model, Location at, String animation) {
        RigInstance prev = rigs.remove(owner);
        if (prev != null) prev.remove();
        enforceCap();
        RigInstance r = new RigInstance(model);
        r.spawn(at);
        if (animation != null) r.play(animation);
        rigs.put(owner, r);
        return r;
    }

    public void clear(UUID owner) {
        org.bukkit.entity.Entity host = hosts.remove(owner);
        if (host != null && host.isValid() && !host.isDead()) host.setInvisible(false); // rend l'hôte visible
        RigInstance r = rigs.remove(owner);
        if (r != null) r.remove();
    }

    /** Fait SUIVRE un rig à une entité hôte (que l'appelant a rendue invisible). */
    public RigInstance spawnFollowing(UUID owner, org.bukkit.entity.Entity host, RigModel model, String anim) {
        clear(owner);
        enforceCap();
        RigInstance r = new RigInstance(model);
        r.spawn(host.getLocation());
        if (anim != null) r.play(anim);
        rigs.put(owner, r);
        hosts.put(owner, host);
        pushToCompanions(host, model, anim, r); // tier ultra : rendu GeckoLib-like pour les joueurs avec le mod
        return r;
    }

    /**
     * Pousse le rig + animations aux joueurs qui ont le mod compagnon v2 (rendu haute qualité,
     * masquage du fallback BlockDisplay). No-op pour les joueurs vanilla. Cf. docs/HANDOFF-AI3.md.
     */
    private void pushToCompanions(org.bukkit.entity.Entity host, RigModel model, String anim, RigInstance rig) {
        var comp = plugin().moduleManager().get(com.mooncore.modules.companion.CompanionModule.class);
        if (comp == null) return;
        String play = anim != null ? anim : defaultAnim(model);
        java.util.List<UUID> bones = rig.displayUuids();
        for (Player pl : plugin().getServer().getOnlinePlayers()) {
            if (!comp.hasProtocolV2(pl)) continue;
            try {
                comp.sendRig(pl, model);
                for (Animation a : model.animations.values()) comp.sendAnim(pl, model.id, a);
                comp.playAnim(pl, host.getUniqueId(), model.id, play, true, bones);
            } catch (Throwable t) {
                log().warn("[ModelEngine] Envoi compagnon échoué pour " + pl.getName() + " : " + t.getMessage());
            }
        }
    }

    /**
     * Démo « mob custom animé » : attache le golem animé au mob vivant le plus proche du joueur
     * (≤ 12 blocs) et rend ce mob invisible → on voit le rig custom à la place de la créature
     * vanilla, qui se déplace/combat normalement. {@code /moon studio rig clear} restaure le mob.
     */
    public boolean attachNearestMob(Player p) {
        org.bukkit.entity.LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity e : p.getNearbyEntities(12, 12, 12)) {
            if (e instanceof Player || !(e instanceof org.bukkit.entity.LivingEntity le)) continue;
            double d = e.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; target = le; }
        }
        if (target == null) return false;
        target.setInvisible(true);
        spawnFollowing(p.getUniqueId(), target, RigModel.golem(), "walk");
        return true;
    }

    public boolean has(UUID owner) { return rigs.containsKey(owner); }
    public int count() { return rigs.size(); }

    /** Joue une animation de FOND (boucle) sur le rig d'un propriétaire. */
    public void playFor(UUID owner, String anim) {
        RigInstance r = rigs.get(owner);
        if (r != null) r.play(anim);
    }

    /** Joue une animation PONCTUELLE (one-shot, ex. attaque) sur le rig d'un propriétaire. */
    public void playOnceFor(UUID owner, String anim) {
        RigInstance r = rigs.get(owner);
        if (r != null) r.playOnce(anim);
    }

    /** Démo : un golem articulé devant le joueur, animation « walk » en boucle. */
    public RigInstance spawnDemo(Player p) {
        return spawn(p.getUniqueId(), RigModel.golem(), frontOf(p), "walk");
    }

    /** Charge un rig par nom et l'affiche devant le joueur. @return false si introuvable. */
    public boolean spawnModelFile(Player p, String name) {
        RigModel model = resolveRig(name);
        if (model == null) return false;
        spawn(p.getUniqueId(), model, frontOf(p), defaultAnim(model));
        return true;
    }

    /** Noms de rigs disponibles : {@code golem} (intégré) + chaque {@code models/<x>.bbmodel}. */
    public java.util.List<String> availableRigs() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("golem");
        java.io.File dir = new java.io.File(plugin().getDataFolder(), "models");
        java.io.File[] files = dir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".bbmodel"));
        if (files != null) for (java.io.File f : files) {
            out.add(f.getName().substring(0, f.getName().length() - ".bbmodel".length()));
        }
        return out;
    }

    /** Résout un rig par nom : {@code golem} (intégré) ou {@code models/<name>.bbmodel}. null si introuvable. */
    public RigModel resolveRig(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.equalsIgnoreCase("golem")) return RigModel.golem();
        return loadModelFile(name);
    }

    /** Charge {@code models/<name>.bbmodel} (géométrie + animations) en {@link RigModel}. null si absent/illisible. */
    public RigModel loadModelFile(String name) {
        String safe = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        java.io.File f = new java.io.File(new java.io.File(plugin().getDataFolder(), "models"), safe + ".bbmodel");
        if (!f.isFile()) return null;
        try {
            String json = java.nio.file.Files.readString(f.toPath());
            BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(json, safe);
            if (raw.bones().isEmpty()) return null;
            RigModel model = BlockBenchImporter.toRigModel(raw, org.bukkit.Material.IRON_BLOCK);
            log().info("Modèle BlockBench chargé : " + safe + " (" + raw.bones().size() + " os, "
                    + model.animations.size() + " anim).");
            return model;
        } catch (Exception e) {
            log().warn("Import BlockBench échoué (" + safe + ") : " + e.getMessage());
            return null;
        }
    }

    /** Animation par défaut d'un rig : walk &gt; idle &gt; première disponible. */
    public static String defaultAnim(RigModel model) {
        if (model.animations.containsKey("walk")) return "walk";
        if (model.animations.containsKey("idle")) return "idle";
        return model.animations.keySet().stream().findFirst().orElse(null);
    }

    // ---- Association boss → rig (persistée dans models/boss-rigs.yml) ----

    public String bossRigName(String bossId) {
        return bossId == null ? null : bossRig.get(bossId.toLowerCase(java.util.Locale.ROOT));
    }

    public void setBossRig(String bossId, String rigName) {
        bossRig.put(bossId.toLowerCase(java.util.Locale.ROOT), rigName);
        saveBossRigs();
    }

    public void removeBossRig(String bossId) {
        bossRig.remove(bossId.toLowerCase(java.util.Locale.ROOT));
        saveBossRigs();
    }

    private void loadBossRigs() {
        if (bossRigFile == null || !bossRigFile.isFile()) return;
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(bossRigFile);
        for (String k : y.getKeys(false)) bossRig.put(k.toLowerCase(java.util.Locale.ROOT), y.getString(k));
    }

    private void saveBossRigs() {
        try {
            if (bossRigFile.getParentFile() != null) bossRigFile.getParentFile().mkdirs();
            var y = new org.bukkit.configuration.file.YamlConfiguration();
            bossRig.forEach(y::set);
            y.save(bossRigFile);
        } catch (Exception e) {
            log().warn("Sauvegarde boss-rigs.yml échouée : " + e.getMessage());
        }
    }

    /** Emplacement ~2,5 blocs devant le joueur, orienté neutre. */
    private static Location frontOf(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vector(0, 0, 1);
        Location at = p.getLocation().add(dir.normalize().multiply(2.5));
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }
}
