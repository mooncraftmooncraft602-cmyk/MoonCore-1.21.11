package com.mooncore.modules.mechanic;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Commande admin {@code /moon mechanic ...} : mécaniques génériques trigger→action data-driven. */
public final class MechanicSubCommand implements SubCommand {

    private final MechanicModule module;

    public MechanicSubCommand(MechanicModule module) { this.module = module; }

    @Override public String name() { return "mechanic"; }
    @Override public List<String> aliases() { return List.of("mechanics", "mech"); }
    @Override public String permission() { return "mooncore.admin.mechanic"; }
    @Override public String description() { return "Gestion des mécaniques custom"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(s, a);
                case "delete", "remove" -> delete(s, a);
                case "list" -> list(s);
                case "info" -> info(s, a);
                case "trigger" -> setTrigger(s, a);
                case "match" -> setMatch(s, a);
                case "cooldown" -> setCooldown(s, a);
                case "interval" -> setInterval(s, a);
                case "enable" -> setEnabled(s, a);
                case "addaction" -> addAction(s, a);
                case "clearactions" -> clearActions(s, a);
                case "test" -> test(s, a);
                case "reload" -> { module.reloadDefinitions(); msg(s, "<green>Mécaniques rechargées."); }
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            msg(s, "<red>Nombre invalide.");
        }
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon mechanic create <id>"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!MechanicStore.isValidId(id)) { msg(s, "<red>Id invalide (a-z 0-9 _ -, max 48)."); return; }
        if (module.def(id) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        module.put(new MechanicDef(id));
        msg(s, "<green>Mécanique <white>" + id + "<green> créée. Définis son déclencheur : <white>/moon mechanic trigger "
                + id + " <USE_ITEM|INTERACT_BLOCK|BREAK_BLOCK|KILL_ENTITY|PLAYER_JOIN|INTERVAL>");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon mechanic delete <id>"); return; }
        msg(s, module.removeDef(a[1]) ? "<green>Supprimée : " + a[1] : "<red>Id inconnu.");
    }

    private void list(CommandSender s) {
        var defs = module.definitions();
        if (defs.isEmpty()) { msg(s, "<gray>Aucune mécanique. <white>/moon mechanic create <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>Mécaniques custom</gradient> <dark_gray>(" + defs.size() + ")");
        for (MechanicDef d : defs) {
            msg(s, " <dark_gray>▸ <white>" + d.id() + " <gray>(" + d.trigger().name().toLowerCase(Locale.ROOT)
                    + ", " + d.actions().size() + " action(s)" + (d.isRunnable() ? "" : ", <red>inactive<gray>") + ")");
        }
    }

    private void info(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        msg(s, "<gradient:#8a2be2:#c77dff>" + d.id() + "</gradient> <gray>— " + d.displayName());
        msg(s, " <gray>Déclencheur : <white>" + d.trigger().name()
                + (d.matchKey() != null ? " <gray>match <white>" + d.matchKey() : "")
                + " <gray>· cooldown <white>" + d.cooldownTicks() + "t"
                + (d.trigger() == TriggerType.INTERVAL ? " <gray>· intervalle <white>" + d.intervalTicks() + "t" : ""));
        msg(s, " <gray>Active : <white>" + d.enabled() + " <gray>· exécutable : <white>" + d.isRunnable());
        for (int i = 0; i < d.actions().size(); i++) {
            MechanicAction ac = d.actions().get(i);
            msg(s, "   <dark_gray>" + i + ". <white>" + ac.type().name().toLowerCase(Locale.ROOT)
                    + " <gray>" + ac.params());
        }
    }

    private void setTrigger(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon mechanic trigger <id> <type>"); return; }
        TriggerType t = TriggerType.fromText(a[2]);
        if (t == TriggerType.NONE) { msg(s, "<red>Déclencheur inconnu : " + a[2]); return; }
        d.setTrigger(t); module.put(d);
        msg(s, "<green>Déclencheur de " + d.id() + " = <white>" + t.name());
    }

    private void setMatch(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon mechanic match <id> <Material|custom:id|EntityType|none>"); return; }
        String v = a[2].equalsIgnoreCase("none") ? null : a[2];
        d.setMatchKey(v); module.put(d);
        msg(s, "<green>Filtre de " + d.id() + " = <white>" + (d.matchKey() == null ? "(tout)" : d.matchKey()));
    }

    private void setCooldown(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon mechanic cooldown <id> <ticks>"); return; }
        d.setCooldownTicks(Integer.parseInt(a[2])); module.put(d);
        msg(s, "<green>Cooldown de " + d.id() + " = <white>" + d.cooldownTicks() + "t");
    }

    private void setInterval(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon mechanic interval <id> <ticks>"); return; }
        d.setIntervalTicks(Integer.parseInt(a[2])); module.put(d);
        msg(s, "<green>Intervalle de " + d.id() + " = <white>" + d.intervalTicks() + "t");
    }

    private void setEnabled(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon mechanic enable <id> <on|off>"); return; }
        d.setEnabled(on(a[2])); module.put(d);
        msg(s, "<green>" + d.id() + " active = <white>" + d.enabled());
    }

    private void addAction(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (a.length < 3) {
            msg(s, "<red>/moon mechanic addaction <id> <type> [clé=valeur ...]");
            msg(s, "<gray>ex: addaction heal_wand message text=<aqua>Soigné!</aqua>");
            return;
        }
        ActionType type = ActionType.fromText(a[2]);
        if (type == ActionType.NONE) { msg(s, "<red>Type d'action inconnu : " + a[2]); return; }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 3; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            if (eq > 0) params.put(a[i].substring(0, eq), a[i].substring(eq + 1));
        }
        d.addAction(new MechanicAction(type, params)); module.put(d);
        msg(s, "<green>Action <white>" + type.name().toLowerCase(Locale.ROOT) + "<green> ajoutée à " + d.id()
                + " <gray>(" + d.actions().size() + " au total). <white>" + params);
    }

    private void clearActions(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        d.actions().clear(); module.put(d);
        msg(s, "<green>Actions de " + d.id() + " effacées.");
    }

    private void test(CommandSender s, String[] a) {
        MechanicDef d = need(s, a); if (d == null) return;
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs (les actions ciblent un joueur)."); return; }
        module.runActions(d, p);
        msg(s, "<green>Actions de " + d.id() + " exécutées sur toi (cooldown/filtre ignorés).");
    }

    private MechanicDef need(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>Id manquant."); return null; }
        MechanicDef d = module.def(a[1]);
        if (d == null) msg(s, "<red>Id inconnu : " + a[1]);
        return d;
    }

    private static boolean on(String v) {
        return v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("oui");
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon mechanic</gradient> <gray>— mécaniques trigger→action");
        String[] l = {
                "create <id> / delete <id> / list / info <id> / reload",
                "trigger <id> <type>  ·  match <id> <Material|custom:id|EntityType|none>",
                "cooldown <id> <ticks>  ·  interval <id> <ticks>  ·  enable <id> <on|off>",
                "addaction <id> <type> [clé=valeur ...]  ·  clearactions <id>",
                "test <id>  (exécute les actions sur toi, ignore cooldown/filtre)"
        };
        for (String x : l) msg(s, " <dark_gray>▸ <gray>" + x);
        msg(s, "<gray>Types action : message, command, sound, potion, give_item, money, damage, heal, xp, teleport.");
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "delete", "list", "info", "trigger", "match", "cooldown",
                    "interval", "enable", "addaction", "clearactions", "test", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "delete", "info", "trigger", "match", "cooldown", "interval", "enable",
                     "addaction", "clearactions", "test" -> filter(new ArrayList<>(module.ids()), a[1]);
                default -> List.of();
            };
        }
        if (a.length == 3) {
            return switch (sub) {
                case "trigger" -> filter(Arrays.stream(TriggerType.values()).filter(t -> t != TriggerType.NONE)
                        .map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()), a[2]);
                case "addaction" -> filter(Arrays.stream(ActionType.values()).filter(t -> t != ActionType.NONE)
                        .map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.toList()), a[2]);
                case "enable" -> filter(List.of("on", "off"), a[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
