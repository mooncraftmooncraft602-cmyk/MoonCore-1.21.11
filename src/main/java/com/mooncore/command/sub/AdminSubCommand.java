package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.afk.AntiAfkService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.SubCommand;
import com.mooncore.data.content.ContentMigrator;
import com.mooncore.data.content.ContentSyncService;
import com.mooncore.data.content.UniversalContentStore;
import com.mooncore.modules.antifarm.AntiFarmModule;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.event.EventManagerModule;
import com.mooncore.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /moon admin <inspect|debug|info>} — outils d'administration transverses : agrège
 * les infos des différents modules pour un diagnostic rapide.
 */
public final class AdminSubCommand implements SubCommand {

    @Override public String name() { return "admin"; }
    @Override public String permission() { return "mooncore.admin.debug"; }
    @Override public String description() { return "Outils d'administration (inspect/debug)"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "inspect" -> inspect(plugin, sender, args);
            case "migrate-content" -> migrateContent(plugin, sender);
            case "timings" -> timings(plugin, sender, args);
            case "debug" -> debug(plugin, sender);
            case "info" -> sender.sendMessage(cm.prefixed("version",
                    "version", plugin.getPluginMeta().getVersion(),
                    "season", plugin.getConfig().getString("core.season-id", "?"),
                    "modules", String.valueOf(plugin.moduleManager().enableOrder().size())));
            default -> sender.sendMessage(cm.prefixed("admin-usage"));
        }
    }

    private void inspect(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 2) {
            sender.sendMessage(cm.prefixed("admin-inspect-usage"));
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            sender.sendMessage(cm.prefixed("admin-inspect-offline", "player", args[1]));
            return;
        }
        var id = t.getUniqueId();
        var s = plugin.services();

        sender.sendMessage(cm.message("admin-inspect-header", "player", t.getName()));

        s.get(ProgressionService.class).ifPresent(pr -> sender.sendMessage(cm.message("admin-inspect-prog",
                "tier", String.valueOf(pr.tier(id)), "xp", String.valueOf(pr.xp(id)))));

        s.get(EconomyService.class).ifPresent(eco -> sender.sendMessage(cm.message("admin-inspect-eco",
                "balance", String.format(Locale.ROOT, "%.2f", eco.balance(id)))));

        s.get(AntiAfkService.class).ifPresent(afk -> sender.sendMessage(cm.message("admin-inspect-afk",
                "afk", afk.isAfk(id) ? "oui" : "non",
                "idle", TimeFormat.shortDuration(afk.idleMillis(id) / 1000))));

        s.get(StatisticsService.class).ifPresent(st -> sender.sendMessage(cm.message("admin-inspect-stats",
                "kills", String.valueOf(st.get(id, StatKeys.MOB_KILLS)),
                "pvp", String.valueOf(st.get(id, StatKeys.PLAYER_KILLS)),
                "deaths", String.valueOf(st.get(id, StatKeys.DEATHS)),
                "bosses", String.valueOf(st.get(id, StatKeys.BOSS_KILLS)))));

        AntiFarmModule af = plugin.moduleManager().get(AntiFarmModule.class);
        if (af != null) {
            sender.sendMessage(cm.message("admin-inspect-spawners",
                    "count", String.valueOf(af.registry().ownerCount(id))));
        }
    }

    /**
     * {@code /moon admin migrate-content} — importe (idempotent) le contenu YAML
     * (items/blocks/bosses) dans le store SQL universel {@code mooncore_content} (Étape A4).
     * Exécuté en asynchrone ; le YAML reste intact.
     */
    private void migrateContent(MoonCore plugin, CommandSender sender) {
        var dm = plugin.dataManager();
        String prefix = plugin.configManager().prefix();
        if (dm == null || !dm.isReady()) {
            sender.sendMessage(com.mooncore.util.Text.mm(prefix
                    + "<red>Base de données indisponible : migration impossible.</red>"));
            return;
        }
        sender.sendMessage(com.mooncore.util.Text.mm(prefix
                + "<gray>Migration du contenu YAML → SQL en cours…</gray>"));
        plugin.schedulers().async(() -> {
            try {
                dm.applyMigrations(ContentSyncService.migrations());
                UniversalContentStore store = new UniversalContentStore(dm.database());
                ContentMigrator.Result r = ContentMigrator.migrate(
                        plugin.getDataFolder(), store, System.currentTimeMillis());
                plugin.schedulers().sync(() -> sender.sendMessage(com.mooncore.util.Text.mm(prefix
                        + "<green>Migration terminée :</green> <white>" + r.items() + "</white> item(s), <white>"
                        + r.blocks() + "</white> bloc(s), <white>" + r.bosses() + "</white> boss, <white>"
                        + r.crops() + "</white> culture(s), <white>" + r.loot() + "</white> table(s) de loot, <white>"
                        + r.mechanics() + "</white> mécanique(s)"
                        + (r.errors() > 0 ? " <red>(" + r.errors() + " erreur(s) ignorée(s))</red>" : "")
                        + " <gray>→ mooncore_content.</gray>")));
            } catch (Exception e) {
                plugin.logger().error("migrate-content échoué", e);
                plugin.schedulers().sync(() -> sender.sendMessage(com.mooncore.util.Text.mm(prefix
                        + "<red>Échec de la migration (voir console).</red>")));
            }
        });
    }

    /**
     * {@code /moon admin timings [on|off|reset]} — mini-profiler des chemins chauds. Désactivé par
     * défaut (coût nul) ; {@code on} l'active le temps d'une mesure, {@code off}/{@code reset} le coupe/vide.
     * Sans argument : affiche le top des points de mesure (temps moyen / max / total).
     */
    private void timings(MoonCore plugin, CommandSender sender, String[] args) {
        String prefix = plugin.configManager().prefix();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "show";
        switch (sub) {
            case "on" -> {
                com.mooncore.util.Timings.setEnabled(true);
                sender.sendMessage(com.mooncore.util.Text.mm(prefix + "<green>Profiler activé.</green> "
                        + "<gray>Laissez tourner puis relancez</gray> <white>/moon admin timings</white><gray>.</gray>"));
            }
            case "off" -> {
                com.mooncore.util.Timings.setEnabled(false);
                sender.sendMessage(com.mooncore.util.Text.mm(prefix + "<yellow>Profiler désactivé.</yellow>"));
            }
            case "reset" -> {
                com.mooncore.util.Timings.reset();
                sender.sendMessage(com.mooncore.util.Text.mm(prefix + "<gray>Mesures réinitialisées.</gray>"));
            }
            default -> {
                List<com.mooncore.util.Timings.Snapshot> snaps = com.mooncore.util.Timings.snapshot();
                String state = com.mooncore.util.Timings.isEnabled() ? "<green>actif</green>" : "<red>inactif</red>";
                sender.sendMessage(com.mooncore.util.Text.mm(prefix + "<gray>Profiler :</gray> " + state
                        + " <dark_gray>(on/off/reset)</dark_gray>"));
                if (snaps.isEmpty()) {
                    sender.sendMessage(com.mooncore.util.Text.mm(prefix + "<gray>Aucune mesure.</gray>"));
                    return;
                }
                int shown = 0;
                for (com.mooncore.util.Timings.Snapshot s : snaps) {
                    if (shown++ >= 15) break;
                    sender.sendMessage(com.mooncore.util.Text.mm(prefix + String.format(Locale.ROOT,
                            "<white>%s</white> <gray>×%d</gray> <yellow>moy %.1fµs</yellow> "
                                    + "<gold>max %.1fµs</gold> <aqua>tot %.1fms</aqua>",
                            s.name(), s.count(), s.avgMicros(), s.maxMicros(), s.totalMillis())));
                }
            }
        }
    }

    private void debug(MoonCore plugin, CommandSender sender) {
        var cm = plugin.configManager();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        long activeModules = plugin.moduleManager().all().stream()
                .filter(com.mooncore.core.module.MoonModule::isEnabled).count();

        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);
        EventManagerModule event = plugin.moduleManager().get(EventManagerModule.class);

        sender.sendMessage(cm.message("admin-debug-header"));
        sender.sendMessage(cm.message("admin-debug-modules",
                "active", String.valueOf(activeModules),
                "total", String.valueOf(plugin.moduleManager().all().size())));
        sender.sendMessage(cm.message("admin-debug-memory",
                "used", String.valueOf(usedMb), "max", String.valueOf(maxMb)));
        sender.sendMessage(cm.message("admin-debug-runtime",
                "bosses", String.valueOf(boss != null ? boss.activeCount() : 0),
                "events", String.valueOf(event != null ? event.activeIds().size() : 0),
                "players", String.valueOf(Bukkit.getOnlinePlayers().size())));
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new java.util.ArrayList<>();
            for (String s : List.of("inspect", "migrate-content", "timings", "debug", "info")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("timings")) {
            List<String> out = new java.util.ArrayList<>();
            for (String s : List.of("on", "off", "reset")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
