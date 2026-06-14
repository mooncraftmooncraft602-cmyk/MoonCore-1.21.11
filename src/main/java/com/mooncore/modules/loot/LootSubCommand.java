package com.mooncore.modules.loot;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.Text;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Commande admin {@code /moon loot ...} : tables de loot génériques (pools pondérés) data-driven. */
public final class LootSubCommand implements SubCommand {

    private final LootManagerModule module;

    public LootSubCommand(LootManagerModule module) { this.module = module; }

    @Override public String name() { return "loot"; }
    @Override public List<String> aliases() { return List.of("loottable", "loottables"); }
    @Override public String permission() { return "mooncore.admin.loot"; }
    @Override public String description() { return "Gestion des tables de loot custom"; }
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
                case "addpool" -> addPool(s, a);
                case "addentry" -> addEntry(s, a);
                case "clearpools" -> clearPools(s, a);
                case "removepool" -> removePool(s, a);
                case "removeentry" -> removeEntry(s, a);
                case "rolls", "setrolls" -> setRolls(s, a);
                case "setweight", "weight" -> setWeight(s, a);
                case "test", "roll" -> test(s, a);
                case "give" -> give(s, a);
                case "stats" -> stats(s, a);
                case "fill" -> fill(s, a);
                case "validate" -> validate(s, a);
                case "reload" -> { module.reloadDefinitions(); msg(s, "<green>Tables de loot rechargées."); }
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            msg(s, "<red>Nombre invalide.");
        }
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon loot create <id>"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!LootTableStore.isValidId(id)) { msg(s, "<red>Id invalide (a-z 0-9 _ -, max 48)."); return; }
        if (module.def(id) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        module.put(new LootTableDef(id));
        msg(s, "<green>Table de loot <white>" + id + "<green> créée. Ajoute un pool : <white>/moon loot addpool "
                + id + " [rollsMin] [rollsMax]");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon loot delete <id>"); return; }
        java.util.Set<String> referencing = LootManagerModule.referencingTables(module.definitions(), a[1]);
        java.util.List<String> external = module.externalReferencesTo(a[1]);
        boolean removed = module.removeDef(a[1]);
        if (!removed) { msg(s, "<red>Id inconnu."); return; }
        msg(s, "<green>Supprimée : " + a[1]);
        if (!referencing.isEmpty()) {
            msg(s, "<yellow>⚠ Table(s) qui la référençai(en)t (désormais pendante(s)) : <white>" + String.join(", ", referencing));
        }
        if (!external.isEmpty()) {
            msg(s, "<yellow>⚠ Contenu(s) qui l'utilisai(en)t (repli sur le drop fixe) : <white>" + String.join(", ", external));
        }
    }

    private void list(CommandSender s) {
        var defs = module.definitions();
        if (defs.isEmpty()) { msg(s, "<gray>Aucune table de loot. <white>/moon loot create <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>Tables de loot</gradient> <dark_gray>(" + defs.size() + ")");
        for (LootTableDef d : defs) {
            int entries = d.pools().stream().mapToInt(p -> p.entries().size()).sum();
            boolean dangling = !d.danglingReferences(id -> module.def(id) != null).isEmpty();
            msg(s, " <dark_gray>▸ <white>" + d.id() + " <gray>(" + d.pools().size() + " pool(s), " + entries + " entrée(s))"
                    + (dangling ? " <red>⚠ réf. pendante" : ""));
        }
    }

    private void info(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        msg(s, "<gradient:#8a2be2:#c77dff>" + d.id() + "</gradient> <gray>— " + d.displayName());
        java.util.Set<String> parents = LootManagerModule.referencingTables(module.definitions(), d.id());
        if (!parents.isEmpty()) {
            msg(s, " <gray>Référencée par : <white>" + String.join(", ", parents) + " <dark_gray>(imbrication)");
        }
        if (d.pools().isEmpty()) { msg(s, " <gray>(aucun pool — <white>/moon loot addpool " + d.id() + "<gray>)"); return; }
        for (int i = 0; i < d.pools().size(); i++) {
            LootPool p = d.pools().get(i);
            msg(s, " <dark_gray>Pool <white>" + i + " <gray>· rolls <white>" + p.rollsMin() + "–" + p.rollsMax()
                    + " <gray>· poids total <white>" + p.totalWeight());
            for (LootEntry e : p.entries()) {
                long pct = Math.round(p.chanceOf(e) * 100);
                String label = e.isReference() ? "↪ table " + e.tableRef()
                        : (e.isCustom() ? "✦ " + e.itemId() : e.material().name());
                msg(s, "   <dark_gray>▸ <white>" + label
                        + " <gray>×" + e.countMin() + "–" + e.countMax() + " <gray>poids <white>" + e.weight()
                        + " <dark_gray>(<yellow>" + pct + "%<dark_gray>/tirage)");
            }
        }
    }

    private void addPool(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        int rMin = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        int rMax = a.length >= 4 ? Integer.parseInt(a[3]) : rMin;
        d.add(new LootPool(rMin, rMax));
        module.put(d);
        msg(s, "<green>Pool <white>" + (d.pools().size() - 1) + "<green> ajouté à " + d.id()
                + " <gray>(rolls " + rMin + "–" + rMax + "). Ajoute une entrée : <white>/moon loot addentry "
                + d.id() + " " + (d.pools().size() - 1) + " <Material|custom:id> [poids] [min] [max]");
    }

    private void addEntry(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        if (a.length < 4) { msg(s, "<red>/moon loot addentry <id> <poolIndex> <Material|custom:itemId|table:tableId> [poids] [min] [max]"); return; }
        int idx = Integer.parseInt(a[2]);
        if (idx < 0 || idx >= d.pools().size()) { msg(s, "<red>Pool inexistant : " + idx + " (0–" + (d.pools().size() - 1) + ")"); return; }
        String v = a[3];
        String customId = null;
        String tableRef = null;
        Material mat = Material.AIR;
        String lv = v.toLowerCase(Locale.ROOT);
        if (lv.startsWith("custom:")) {
            customId = v.substring("custom:".length());
        } else if (lv.startsWith("table:")) {
            tableRef = v.substring("table:".length());
            if (tableRef.equalsIgnoreCase(d.id())) { msg(s, "<red>Une table ne peut pas se référencer elle-même."); return; }
        } else {
            Material m = Material.matchMaterial(v.toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem()) { msg(s, "<red>Matériau invalide : " + v); return; }
            mat = m;
        }
        int weight = a.length >= 5 ? Integer.parseInt(a[4]) : 1;
        int cMin = a.length >= 6 ? Integer.parseInt(a[5]) : 1;
        int cMax = a.length >= 7 ? Integer.parseInt(a[6]) : cMin;
        d.pools().get(idx).add(new LootEntry(customId, mat, weight, cMin, cMax, tableRef));
        module.put(d);
        String label = tableRef != null ? "↪ table " + tableRef : (customId != null ? "✦ " + customId : mat.name());
        msg(s, "<green>Entrée ajoutée au pool " + idx + " de " + d.id() + " : <white>"
                + label + " <gray>×" + cMin + "–" + cMax + " poids " + weight);
        // Avertit (sans bloquer) si la référence ne résout pas — typo fréquente.
        if (customId != null && !module.customItemExists(customId)) {
            msg(s, "<yellow>⚠ Item custom inconnu : <white>" + customId + "<yellow> (rien ne sortira pour cette entrée).");
        }
        if (tableRef != null && module.def(tableRef) == null) {
            msg(s, "<yellow>⚠ Table imbriquée inconnue : <white>" + tableRef + "<yellow> (crée-la ou corrige l'id).");
        }
    }

    private void removePool(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon loot removepool <id> <poolIndex>"); return; }
        int idx = Integer.parseInt(a[2]);
        if (idx < 0 || idx >= d.pools().size()) { msg(s, "<red>Pool inexistant : " + idx + " (0–" + (d.pools().size() - 1) + ")"); return; }
        d.pools().remove(idx);
        module.put(d);
        msg(s, "<green>Pool <white>" + idx + "<green> retiré de " + d.id() + " <gray>(" + d.pools().size() + " restant(s)).");
    }

    private void removeEntry(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        if (a.length < 4) { msg(s, "<red>/moon loot removeentry <id> <poolIndex> <entryIndex>"); return; }
        int pi = Integer.parseInt(a[2]);
        if (pi < 0 || pi >= d.pools().size()) { msg(s, "<red>Pool inexistant : " + pi + " (0–" + (d.pools().size() - 1) + ")"); return; }
        LootPool pool = d.pools().get(pi);
        int ei = Integer.parseInt(a[3]);
        if (ei < 0 || ei >= pool.entries().size()) { msg(s, "<red>Entrée inexistante : " + ei + " (0–" + (pool.entries().size() - 1) + ")"); return; }
        pool.entries().remove(ei);
        module.put(d);
        msg(s, "<green>Entrée <white>" + ei + "<green> retirée du pool " + pi + " de " + d.id()
                + " <gray>(" + pool.entries().size() + " restante(s)).");
    }

    private void setWeight(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        if (a.length < 5) { msg(s, "<red>/moon loot setweight <id> <poolIndex> <entryIndex> <poids>"); return; }
        int pi = Integer.parseInt(a[2]);
        if (pi < 0 || pi >= d.pools().size()) { msg(s, "<red>Pool inexistant : " + pi + " (0–" + (d.pools().size() - 1) + ")"); return; }
        LootPool pool = d.pools().get(pi);
        int ei = Integer.parseInt(a[3]);
        if (ei < 0 || ei >= pool.entries().size()) { msg(s, "<red>Entrée inexistante : " + ei + " (0–" + (pool.entries().size() - 1) + ")"); return; }
        int weight = Integer.parseInt(a[4]);
        LootEntry updated = pool.entries().get(ei).withWeight(weight);   // entrée immuable → remplacement
        pool.entries().set(ei, updated);
        module.put(d);
        msg(s, "<green>Poids de l'entrée <white>" + ei + "<green> (pool " + pi + " de " + d.id()
                + ") = <white>" + updated.weight() + " <gray>(" + Math.round(pool.chanceOf(updated) * 1000) / 10.0 + "%/tirage).");
    }

    private void setRolls(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        if (a.length < 4) { msg(s, "<red>/moon loot rolls <id> <poolIndex> <min> [max]"); return; }
        int pi = Integer.parseInt(a[2]);
        if (pi < 0 || pi >= d.pools().size()) { msg(s, "<red>Pool inexistant : " + pi + " (0–" + (d.pools().size() - 1) + ")"); return; }
        int min = Integer.parseInt(a[3]);
        int max = a.length >= 5 ? Integer.parseInt(a[4]) : min;
        LootPool pool = d.pools().get(pi);
        pool.setRolls(min, max);   // entrées conservées
        module.put(d);
        msg(s, "<green>Rolls du pool <white>" + pi + "<green> de " + d.id() + " = <white>"
                + pool.rollsMin() + "–" + pool.rollsMax() + " <gray>(" + pool.entries().size() + " entrée(s) conservée(s)).");
    }

    private void clearPools(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        d.pools().clear();
        module.put(d);
        msg(s, "<green>Tous les pools de " + d.id() + " ont été retirés.");
    }

    private void test(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        int n = a.length >= 3 ? Math.max(1, Math.min(20, Integer.parseInt(a[2]))) : 1;
        msg(s, "<gradient:#8a2be2:#c77dff>Test de tirage</gradient> <gray>" + d.id() + " <dark_gray>×" + n);
        for (int i = 0; i < n; i++) {
            List<LootResult> roll = module.rollFlat(d.id(), ThreadLocalRandom.current());  // tables imbriquées développées
            if (roll.isEmpty()) { msg(s, " <dark_gray>#" + (i + 1) + " <gray>(rien)"); continue; }
            String line = roll.stream()
                    .map(r -> (r.isCustom() ? "✦" + r.itemId() : r.material().name().toLowerCase(Locale.ROOT)) + "×" + r.count())
                    .collect(Collectors.joining(", "));
            msg(s, " <dark_gray>#" + (i + 1) + " <white>" + line);
        }
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon loot give <joueur|@a> <id>"); return; }
        LootTableDef d = module.def(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu : " + a[2]); return; }
        String sel = a[1].toLowerCase(java.util.Locale.ROOT);
        if (sel.equals("@a") || sel.equals("all") || sel.equals("*")) {
            var online = org.bukkit.Bukkit.getOnlinePlayers();
            if (online.isEmpty()) { msg(s, "<yellow>Aucun joueur en ligne."); return; }
            int totalStacks = 0, players = 0;
            for (org.bukkit.entity.Player p : online) {
                totalStacks += module.give(p, d.id(), ThreadLocalRandom.current());  // tirage indépendant par joueur
                players++;
            }
            msg(s, "<green>Donné la table " + d.id() + " à <white>" + players
                    + "<green> joueur(s) <gray>(" + totalStacks + " pile(s) au total).");
            return;
        }
        org.bukkit.entity.Player t = org.bukkit.Bukkit.getPlayerExact(a[1]);
        if (t == null) { msg(s, "<red>Joueur hors-ligne."); return; }
        int n = module.give(t, d.id(), ThreadLocalRandom.current());
        msg(s, "<green>Donné <white>" + n + "<green> pile(s) de la table " + d.id() + " à " + t.getName() + ".");
    }

    private void stats(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        int n = a.length >= 3 ? Math.max(10, Math.min(100_000, Integer.parseInt(a[2]))) : 1000;
        List<List<LootResult>> samples = new ArrayList<>(n);
        for (int i = 0; i < n; i++) samples.add(module.rollFlat(d.id(), ThreadLocalRandom.current()));
        List<LootStats.Entry> agg = LootStats.aggregate(samples);
        double empty = LootStats.emptyRate(samples);
        msg(s, "<gradient:#8a2be2:#c77dff>Balance</gradient> <gray>" + d.id() + " <dark_gray>sur " + n + " tirages"
                + String.format(Locale.ROOT, " <gray>· bredouille <yellow>%.1f%%", empty * 100.0));
        if (agg.isEmpty()) { msg(s, " <gray>(aucun drop — table vide ou pools sans entrée)"); return; }
        int shown = 0;
        for (LootStats.Entry e : agg) {
            if (shown++ >= 25) { msg(s, " <dark_gray>… " + (agg.size() - 25) + " autre(s)"); break; }
            String name = e.custom() ? "<light_purple>" + e.key() : "<white>" + e.key();
            msg(s, String.format(Locale.ROOT, " %s <gray>— <aqua>%.1f%%<gray> des tirages, <yellow>%.2f<gray>/tirage",
                    name, e.frequency() * 100.0, e.avgPerIteration()));
        }
    }

    private void fill(CommandSender s, String[] a) {
        if (!(s instanceof org.bukkit.entity.Player p)) { msg(s, "<red>Réservé aux joueurs (vise un conteneur)."); return; }
        if (a.length < 2) { msg(s, "<red>/moon loot fill <id>  (vise un coffre/baril)"); return; }
        LootTableDef d = module.def(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu : " + a[1]); return; }
        org.bukkit.block.Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof org.bukkit.block.Container container)) {
            msg(s, "<red>Vise un conteneur (coffre, baril, shulker…) à portée."); return;
        }
        int placed = 0;
        for (org.bukkit.inventory.ItemStack it : module.rollItems(d.id(), ThreadLocalRandom.current())) {
            if (container.getInventory().addItem(it).isEmpty()) placed++;
        }
        container.update();
        msg(s, "<green>Conteneur rempli avec <white>" + placed + "<green> pile(s) de la table " + d.id() + ".");
    }

    private void validate(CommandSender s, String[] a) {
        LootTableDef d = need(s, a); if (d == null) return;
        java.util.Set<String> refs = d.referencedTables();
        if (refs.isEmpty()) { msg(s, "<green>✔ " + d.id() + " : aucune table imbriquée référencée."); return; }
        java.util.Set<String> dangling = d.danglingReferences(id -> module.def(id) != null);
        if (dangling.isEmpty()) {
            msg(s, "<green>✔ " + d.id() + " : " + refs.size() + " référence(s) imbriquée(s), toutes valides.");
        } else {
            msg(s, "<red>✖ " + d.id() + " : référence(s) pendante(s) (table inconnue) : <white>" + String.join(", ", dangling));
        }
    }

    private LootTableDef need(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>Id manquant."); return null; }
        LootTableDef d = module.def(a[1]);
        if (d == null) msg(s, "<red>Id inconnu : " + a[1]);
        return d;
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon loot</gradient> <gray>— tables de loot custom");
        String[] l = {
                "create <id> / delete <id> / list / info <id> / reload",
                "addpool <id> [rollsMin] [rollsMax]  (ajoute un pool de tirages)",
                "addentry <id> <poolIndex> <Material|custom:itemId> [poids] [min] [max]",
                "rolls <id> <poolIndex> <min> [max]  (ajuste les tirages d'un pool, garde les entrées)",
                "setweight <id> <poolIndex> <entryIndex> <poids>  (retune le poids d'une entrée)",
                "removepool <id> <poolIndex>  ·  removeentry <id> <poolIndex> <entryIndex>  ·  clearpools <id>",
                "test <id> [n]  (simule n tirages, max 20)  ·  give <joueur|@a> <id>  (donne le butin tiré)",
                "stats <id> [n]  (balance : fréquence + quantité moyenne par item sur n tirages, défaut 1000)",
                "fill <id>  (remplit le conteneur visé avec le butin tiré — design de donjons)",
                "validate <id>  (vérifie que les références imbriquées table:<id> existent)"
        };
        for (String x : l) msg(s, " <dark_gray>▸ <gray>" + x);
        msg(s, "<gray>Référence une table sur une culture/bloc/boss via leur champ <white>loot-table<gray>.");
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "delete", "list", "info", "addpool", "addentry",
                    "removepool", "removeentry", "rolls", "setweight", "clearpools", "test", "stats", "give", "fill", "validate", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "delete", "info", "addpool", "addentry", "removepool", "removeentry",
                     "rolls", "setweight", "clearpools", "test", "stats", "fill", "validate" -> filter(new ArrayList<>(module.ids()), a[1]);
                case "give" -> {
                    List<String> names = org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(org.bukkit.entity.Player::getName).collect(Collectors.toList());
                    names.add(0, "@a");  // récompense de tous les joueurs en ligne (événements)
                    yield filter(names, a[1]);
                }
                default -> List.of();
            };
        }
        if (a.length == 3 && sub.equals("give")) {
            return filter(new ArrayList<>(module.ids()), a[2]);
        }
        if (a.length == 4 && sub.equals("addentry")) {
            List<String> opts = new ArrayList<>(List.of("custom:"));
            opts.addAll(java.util.Arrays.stream(Material.values()).limit(64).map(m -> m.name().toLowerCase(Locale.ROOT)).toList());
            return filter(opts, a[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
