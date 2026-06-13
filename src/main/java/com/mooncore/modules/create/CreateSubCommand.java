package com.mooncore.modules.create;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Commande de gestion de contenu unifiée {@code /moon content <action> <type> …} (Étape E1/E3),
 * adossée au {@link ContentTypeRegistry}. Un seul point d'entrée pour créer/éditer/lister/supprimer/
 * donner n'importe quel type enregistré (item, block, crop…).
 */
public final class CreateSubCommand implements SubCommand {

    private final ContentTypeRegistry registry;

    public CreateSubCommand(ContentTypeRegistry registry) {
        this.registry = registry;
    }

    @Override public String name() { return "content"; }
    @Override public List<String> aliases() { return List.of("ct"); }
    @Override public String permission() { return "mooncore.admin.create"; }
    @Override public String description() { return "Création/gestion de contenu unifiée"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        String action = a[0].toLowerCase(Locale.ROOT);
        if (action.equals("types")) { types(s); return; }

        if (a.length < 2) { help(s); return; }
        ContentTypeHandler h = registry.get(a[1]);
        if (h == null) { msg(s, "<red>Type inconnu : <white>" + a[1] + "</white>. Voir <white>/moon content types"); return; }

        switch (action) {
            case "create" -> create(s, h, a);
            case "delete", "remove" -> delete(s, h, a);
            case "list" -> list(s, h);
            case "info" -> info(s, h, a);
            case "clone" -> clone(s, h, a);
            case "give" -> give(s, h, a);
            case "edit" -> edit(s, h, a);
            default -> help(s);
        }
    }

    private void create(CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon content create " + h.type() + " <id>"); return; }
        String id = a[2];
        if (h.create(id)) msg(s, "<green>" + h.type() + " <white>" + id + "<green> créé. <gray>Édite-le avec <white>/moon content edit " + h.type() + " " + id);
        else msg(s, "<red>Création impossible (id invalide ou déjà existant) : <white>" + id);
    }

    private void delete(CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon content delete " + h.type() + " <id>"); return; }
        msg(s, h.delete(a[2]) ? "<green>Supprimé : <white>" + a[2] : "<red>Id inconnu : <white>" + a[2]);
    }

    private void list(CommandSender s, ContentTypeHandler h) {
        var ids = h.ids();
        if (ids.isEmpty()) { msg(s, "<gray>Aucun " + h.type() + ". <white>/moon content create " + h.type() + " <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>" + h.type() + "</gradient> <dark_gray>(" + ids.size() + ")");
        for (String id : ids) msg(s, " <dark_gray>▸ <white>" + id);
    }

    private void info(CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon content info " + h.type() + " <id>"); return; }
        if (!h.exists(a[2])) { msg(s, "<red>Id inconnu : <white>" + a[2]); return; }
        msg(s, "<gray>" + h.type() + " <white>" + a[2] + " <dark_gray>: <reset>" + h.describe(a[2]));
    }

    private void clone(CommandSender s, ContentTypeHandler h, String[] a) {
        // Clonage générique minimal : crée une nouvelle entrée vide (la copie profonde par type
        // viendra via un hook dédié). Sert surtout d'amorce d'édition.
        if (a.length < 4) { msg(s, "<red>/moon content clone " + h.type() + " <source> <nouvelId>"); return; }
        if (!h.exists(a[2])) { msg(s, "<red>Source inconnue : <white>" + a[2]); return; }
        if (h.create(a[3])) msg(s, "<green>Nouvel " + h.type() + " <white>" + a[3] + "<green> créé (à éditer). "
                + "<gray>Copie profonde par type à venir.");
        else msg(s, "<red>Création impossible : <white>" + a[3]);
    }

    private void give(CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 4) { msg(s, "<red>/moon content give " + h.type() + " <joueur> <id> [n]"); return; }
        Player t = Bukkit.getPlayerExact(a[2]);
        if (t == null) { msg(s, "<red>Joueur hors-ligne : <white>" + a[2]); return; }
        int n = a.length >= 5 ? parseInt(a[4], 1) : 1;
        if (h.give(t, a[3], n)) msg(s, "<green>Donné " + n + "× " + h.type() + " <white>" + a[3] + "<green> à " + t.getName());
        else msg(s, "<red>Don impossible (type non donnable ou id inconnu).");
    }

    private void edit(CommandSender s, ContentTypeHandler h, String[] a) {
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 3) { msg(s, "<red>/moon content edit " + h.type() + " <id>"); return; }
        if (!h.exists(a[2])) { msg(s, "<red>Id inconnu : <white>" + a[2]); return; }
        if (!h.openEditor(p, a[2])) msg(s, "<yellow>Pas d'éditeur GUI pour " + h.type()
                + ". <gray>Utilise <white>/moon " + h.type() + " …</white> ou la commande dédiée.");
    }

    private void types(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>Types de contenu</gradient> <dark_gray>(" + registry.size() + ")");
        msg(s, " <white>" + String.join("<gray>, <white>", registry.types()));
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon content</gradient> <gray>— gestion de contenu unifiée");
        String[] l = {
                "create <type> <id> · edit <type> <id> · delete <type> <id>",
                "list <type> · info <type> <id> · clone <type> <src> <nouvel>",
                "give <type> <joueur> <id> [n] · types",
        };
        for (String x : l) msg(s, " <dark_gray>▸ <gray>" + x);
        msg(s, " <dark_gray>types : <white>" + String.join("<gray>, <white>", registry.types()));
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "edit", "delete", "list", "info", "clone", "give", "types"), a[0]);
        }
        if (a.length == 2) {
            return filter(new ArrayList<>(registry.types()), a[1]);
        }
        String action = a[0].toLowerCase(Locale.ROOT);
        ContentTypeHandler h = registry.get(a[1]);
        if (h == null) return List.of();
        if (a.length == 3 && (action.equals("edit") || action.equals("delete") || action.equals("info") || action.equals("clone"))) {
            return filter(new ArrayList<>(h.ids()), a[2]);
        }
        if (a.length == 3 && action.equals("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), a[2]);
        }
        if (a.length == 4 && action.equals("give")) {
            return filter(new ArrayList<>(h.ids()), a[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
