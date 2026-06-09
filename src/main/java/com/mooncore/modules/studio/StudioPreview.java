package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aperçu live d'un objet custom : fait apparaître un {@link ItemDisplay} agrandi qui
 * tourne lentement devant l'admin, puis disparaît automatiquement (~15 s). Permet de voir
 * le rendu réel (modèle/texture du pack) sans se donner l'objet ni recharger quoi que ce soit.
 *
 * <p>Un seul aperçu par joueur : relancer la commande remplace le précédent.
 */
public final class StudioPreview {

    private StudioPreview() {}

    private record Active(UUID displayId, BukkitTask task) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();

    private static final int STEP_TICKS = 3;     // cadence de rotation
    private static final int LIFETIME_TICKS = 300; // ~15 s
    private static final float SCALE = 1.6f;

    public static void show(MoonCore plugin, Player p, String id) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) { p.sendMessage(Text.mm("<red>Module objets custom inactif.")); return; }
        ItemStack item = ci.create(id);
        if (item == null) { p.sendMessage(Text.mm("<red>Objet introuvable : <white>" + id)); return; }

        clear(p.getUniqueId()); // retire un aperçu précédent

        Location loc = p.getEyeLocation().add(p.getEyeLocation().getDirection().normalize().multiply(2.2));
        ItemDisplay display = p.getWorld().spawn(loc, ItemDisplay.class, e -> {
            e.setItemStack(item);
            e.setPersistent(false);                 // jamais sauvegardé sur disque
            e.setBillboard(org.bukkit.entity.Display.Billboard.FIXED); // notre rotation reste visible
            e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); // pleine lumière
            e.setTransformation(transform(0f));
        });

        BukkitTask task = new RotationTask(plugin, display.getUniqueId(), p.getUniqueId())
                .runTaskTimer(plugin, STEP_TICKS, STEP_TICKS);
        ACTIVE.put(p.getUniqueId(), new Active(display.getUniqueId(), task));

        p.sendMessage(Text.mm("<green>Aperçu de <white>" + id + "</white> — il tourne devant toi (~15 s)."));
    }

    private static Transformation transform(float angleRad) {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(angleRad, 0f, 1f, 0f),
                new Vector3f(SCALE, SCALE, SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f));
    }

    /** Retire l'aperçu d'un joueur (entité + tâche) s'il existe. */
    public static void clear(UUID player) {
        Active a = ACTIVE.remove(player);
        if (a == null) return;
        if (a.task() != null) a.task().cancel();
        org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(a.displayId());
        if (e != null) e.remove();
    }

    /** Tourne le display et le supprime au bout de {@link #LIFETIME_TICKS}. */
    private static final class RotationTask implements Runnable {
        private final MoonCore plugin;
        private final UUID displayId;
        private final UUID player;
        private int elapsed = 0;
        private float angle = 0f;
        private BukkitTask self;

        RotationTask(MoonCore plugin, UUID displayId, UUID player) {
            this.plugin = plugin;
            this.displayId = displayId;
            this.player = player;
        }

        BukkitTask runTaskTimer(MoonCore plugin, long delay, long period) {
            this.self = plugin.getServer().getScheduler().runTaskTimer(plugin, this, delay, period);
            return self;
        }

        @Override
        public void run() {
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(displayId);
            elapsed += STEP_TICKS;
            if (!(e instanceof ItemDisplay display) || elapsed >= LIFETIME_TICKS) {
                clear(player);
                return;
            }
            angle += 0.30f; // ~17°/step → tour complet en ~2 s
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(STEP_TICKS);
            display.setTransformation(transform(angle));
        }
    }
}
