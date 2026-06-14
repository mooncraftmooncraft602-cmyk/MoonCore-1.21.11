package com.mooncore.modules.mechanic;

import com.mooncore.MoonCore;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Exécute les {@link MechanicAction} d'une {@link MechanicDef} pour un joueur (partie LIVE). Chaque type
 * d'action est dispatché de manière <b>défensive</b> : paramètres invalides ou services absents sont
 * ignorés silencieusement (jamais d'exception remontée au listener appelant). Doit tourner sur le thread
 * principal (appels Bukkit). La logique de cooldown/parsing pure vit ailleurs ({@link MechanicCooldowns}).
 */
public final class MechanicExecutor {

    private final MoonCore plugin;

    public MechanicExecutor(MoonCore plugin) { this.plugin = plugin; }

    /** Exécute en séquence toutes les actions valides de la mécanique pour ce joueur. */
    public void run(MechanicDef def, Player player) {
        if (def == null || player == null) return;
        for (MechanicAction a : def.actions()) {
            if (!a.isValid()) continue;
            try {
                dispatch(a, player);
            } catch (Exception e) {
                plugin.logger().warn("Action de mécanique '" + def.id() + "' (" + a.type() + ") échouée : " + e.getMessage());
            }
        }
    }

    private void dispatch(MechanicAction a, Player p) {
        switch (a.type()) {
            case MESSAGE -> p.sendMessage(Text.mm(placeholders(a.param("text", ""), p)));
            case COMMAND -> {
                String cmd = placeholders(a.param("command", ""), p).trim();
                if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            case SOUND -> playSound(a, p);
            case POTION -> applyPotion(a, p);
            case GIVE_ITEM -> giveItem(a, p);
            case MONEY -> {
                double amt = a.doubleParam("amount", 0);
                if (amt > 0) plugin.services().get(com.mooncore.api.economy.EconomyService.class)
                        .ifPresent(eco -> eco.deposit(p.getUniqueId(), amt, "mechanic"));
            }
            case DAMAGE -> {
                double amt = a.doubleParam("amount", 0);
                if (amt > 0) p.damage(amt);
            }
            case HEAL -> heal(a, p);
            case XP -> {
                long amt = (long) a.doubleParam("amount", 0);
                if (amt > 0) plugin.services().get(com.mooncore.api.progression.ProgressionService.class)
                        .ifPresent(pr -> pr.addXp(p.getUniqueId(), amt, "mechanic"));
            }
            case TELEPORT -> teleport(a, p);
            case NONE -> { /* ignoré */ }
        }
    }

    private void playSound(MechanicAction a, Player p) {
        String key = a.param("sound", "").toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) return;
        float vol = (float) a.doubleParam("volume", 1.0);
        float pitch = (float) a.doubleParam("pitch", 1.0);
        try {
            NamespacedKey nk = key.contains(":") ? NamespacedKey.fromString(key) : NamespacedKey.minecraft(key);
            Sound sound = nk == null ? null : Registry.SOUNDS.get(nk);
            if (sound != null) p.playSound(p.getLocation(), sound, vol, pitch);
        } catch (Exception ignored) { /* clé de son inconnue */ }
    }

    @SuppressWarnings("deprecation")
    private void applyPotion(MechanicAction a, Player p) {
        String name = a.param("effect", "").toUpperCase(Locale.ROOT).trim();
        if (name.isEmpty()) return;
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (type == null) type = PotionEffectType.getByName(name);   // repli noms historiques
        if (type == null) return;
        int duration = Math.max(1, a.intParam("duration", 100));
        int amplifier = Math.max(0, a.intParam("amplifier", 0));
        p.addPotionEffect(new PotionEffect(type, duration, amplifier));
    }

    private void giveItem(MechanicAction a, Player p) {
        String item = a.param("item", "").trim();
        int amount = Math.max(1, a.intParam("amount", 1));
        if (item.isEmpty()) return;
        ItemStack stack;
        if (item.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            String id = item.substring("custom:".length());
            stack = plugin.services().get(com.mooncore.api.customitem.CustomItemManagerService.class)
                    .map(ci -> ci.create(id, amount)).orElse(null);
        } else {
            Material m = Material.matchMaterial(item.toUpperCase(Locale.ROOT));
            stack = (m == null || !m.isItem()) ? null : new ItemStack(m, amount);
        }
        if (stack != null) p.getInventory().addItem(stack);
    }

    private void heal(MechanicAction a, Player p) {
        double amt = a.doubleParam("amount", 0);
        if (amt <= 0) return;
        var maxAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        p.setHealth(Math.min(max, p.getHealth() + amt));
    }

    private void teleport(MechanicAction a, Player p) {
        String target = a.param("target", "").toLowerCase(Locale.ROOT).trim();
        if (target.equals("spawn")) {
            Location spawn = p.getWorld().getSpawnLocation();
            p.teleport(spawn);
            return;
        }
        if (!a.params().containsKey("x") || !a.params().containsKey("y") || !a.params().containsKey("z")) return;
        double x = a.doubleParam("x", p.getX());
        double y = a.doubleParam("y", p.getY());
        double z = a.doubleParam("z", p.getZ());
        String worldName = a.param("world", null);
        var world = worldName != null ? Bukkit.getWorld(worldName) : p.getWorld();
        if (world != null) p.teleport(new Location(world, x, y, z, p.getYaw(), p.getPitch()));
    }

    /** Remplace les placeholders simples dans un texte d'action. */
    private static String placeholders(String s, Player p) {
        return s == null ? "" : s.replace("%player%", p.getName());
    }
}
