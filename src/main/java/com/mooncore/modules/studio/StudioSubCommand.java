package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.ChatInput;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Ouvre le studio admin unifié de création/édition de contenu. */
public final class StudioSubCommand implements SubCommand {

    private final ChatInput chat;

    public StudioSubCommand(ChatInput chat) {
        this.chat = chat;
    }

    @Override public String name() { return "studio"; }
    @Override public List<String> aliases() { return List.of("creator", "createhub", "atelier"); }
    @Override public String permission() { return "mooncore.admin.studio"; }
    @Override public String description() { return "Studio de création admin"; }
    @Override public String category() { return "admin"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        Player p = (Player) sender;
        if (args.length > 0) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "import" -> { StudioImport.run(plugin, p); return; }
                case "preview" -> {
                    if (args.length < 2) {
                        p.sendMessage(com.mooncore.util.Text.mm("<red>Usage : /moon studio preview <id-objet>"));
                    } else {
                        StudioPreview.show(plugin, p, args[1]);
                    }
                    return;
                }
                case "rig" -> {
                    var me = plugin.moduleManager().get(com.mooncore.modules.model.ModelEngineModule.class);
                    if (me == null) { p.sendMessage(com.mooncore.util.Text.mm("<red>Module model-engine inactif.")); return; }
                    if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                        me.clear(p.getUniqueId());
                        p.sendMessage(com.mooncore.util.Text.mm("<yellow>Rig retiré (mob restauré)."));
                    } else if (args.length >= 2 && args[1].equalsIgnoreCase("attach")) {
                        if (me.attachNearestMob(p)) {
                            p.sendMessage(com.mooncore.util.Text.mm("<green>Golem animé attaché au mob le plus proche (rendu invisible). <dark_gray>Il suit le mob ; rig clear pour restaurer."));
                        } else {
                            p.sendMessage(com.mooncore.util.Text.mm("<red>Aucun mob vivant à ≤12 blocs. Fais apparaître un mob d'abord."));
                        }
                    } else if (args.length >= 2) {
                        if (me.spawnModelFile(p, args[1])) {
                            p.sendMessage(com.mooncore.util.Text.mm("<green>Modèle <white>" + args[1]
                                    + "</white> importé et affiché (animations BlockBench jouées si présentes). <dark_gray>rig clear pour retirer"));
                        } else {
                            p.sendMessage(com.mooncore.util.Text.mm("<red>Modèle introuvable : <white>models/" + args[1]
                                    + ".bbmodel</white> <dark_gray>(dépose ton .bbmodel dans plugins/MoonCore/models/)"));
                        }
                    } else {
                        me.spawnDemo(p);
                        p.sendMessage(com.mooncore.util.Text.mm("<green>Golem articulé animé spawné (démo moteur). <dark_gray>/moon studio rig clear pour retirer."));
                    }
                    return;
                }
                case "bossrig" -> {
                    var me = plugin.moduleManager().get(com.mooncore.modules.model.ModelEngineModule.class);
                    if (me == null) { p.sendMessage(com.mooncore.util.Text.mm("<red>Module model-engine inactif.")); return; }
                    if (args.length < 3) {
                        p.sendMessage(com.mooncore.util.Text.mm("<red>Usage : /moon studio bossrig <bossId> <golem|nomModele|clear>"));
                        return;
                    }
                    String bossId = args[1].toLowerCase(java.util.Locale.ROOT);
                    if (args[2].equalsIgnoreCase("clear")) {
                        me.removeBossRig(bossId);
                        p.sendMessage(com.mooncore.util.Text.mm("<yellow>Rig retiré du boss <white>" + bossId + "</white> (apparence vanilla au prochain spawn)."));
                    } else {
                        me.setBossRig(bossId, args[2]);
                        p.sendMessage(com.mooncore.util.Text.mm("<green>Boss <white>" + bossId + "</white> → modèle <white>" + args[2]
                                + "</white>. <dark_gray>Spawn-le pour le voir animé (l'entité devient invisible, le rig la remplace)."));
                    }
                    return;
                }
                default -> { /* arg inconnu → ouvre le hub */ }
            }
        }
        StudioHubMenu.open(plugin, chat, p);
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return java.util.stream.Stream.of("import", "preview", "rig", "bossrig")
                    .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bossrig")) {
            var boss = plugin.moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
            if (boss != null) {
                String pre = args[1].toLowerCase(java.util.Locale.ROOT);
                return boss.bossIds().stream().filter(id -> id.startsWith(pre)).sorted().limit(50).toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bossrig")) {
            return java.util.stream.Stream.of("golem", "clear")
                    .filter(s -> s.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            var ci = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
            if (ci != null) {
                String pre = args[1].toLowerCase(java.util.Locale.ROOT);
                return ci.ids().stream().filter(id -> id.startsWith(pre)).sorted().limit(50).toList();
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rig")) {
            return java.util.stream.Stream.of("clear", "attach")
                    .filter(s -> s.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
