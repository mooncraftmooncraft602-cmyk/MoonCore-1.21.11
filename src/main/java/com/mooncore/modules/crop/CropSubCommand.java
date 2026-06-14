package com.mooncore.modules.crop;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Commande admin {@code /moon crop ...} : cultures/plantes custom data-driven. */
public final class CropSubCommand implements SubCommand {

    private final CropManagerModule module;

    public CropSubCommand(CropManagerModule module) { this.module = module; }

    @Override public String name() { return "crop"; }
    @Override public List<String> aliases() { return List.of("crops"); }
    @Override public String permission() { return "mooncore.admin.crops"; }
    @Override public String description() { return "Gestion des cultures custom"; }
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
                case "seed" -> setSeed(s, a);
                case "placeon" -> setPlaceOn(s, a);
                case "stages" -> setStages(s, a);
                case "growth" -> setGrowth(s, a);
                case "light" -> setLight(s, a);
                case "water" -> setWater(s, a);
                case "drop" -> setDrop(s, a);
                case "loottable", "loot" -> setLootTable(s, a);
                case "replant" -> setReplant(s, a);
                case "bonemeal" -> setBonemeal(s, a);
                case "giveseed" -> giveSeed(s, a);
                case "reload" -> { module.reloadDefinitions(); msg(s, "<green>Cultures rechargées."); }
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            msg(s, "<red>Nombre invalide.");
        }
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon crop create <id>"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!CropDefStore.isValidId(id)) { msg(s, "<red>Id invalide (a-z 0-9 _ -, max 48)."); return; }
        if (module.def(id) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        module.put(new CropDef(id));
        msg(s, "<green>Culture <white>" + id + "<green> créée. Textures attendues : <white>"
                + id + "_stage0.png … _stage" + (new CropDef(id).stages() - 1) + ".png");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon crop delete <id>"); return; }
        msg(s, module.removeDef(a[1]) ? "<green>Supprimée : " + a[1] : "<red>Id inconnu.");
    }

    private void list(CommandSender s) {
        var defs = module.definitions();
        if (defs.isEmpty()) { msg(s, "<gray>Aucune culture. <white>/moon crop create <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>Cultures custom</gradient> <dark_gray>(" + defs.size() + ")");
        for (CropDef d : defs) {
            msg(s, " <dark_gray>▸ <white>" + d.id() + " <gray>(" + d.stages() + " étapes, sur "
                    + d.placeOn().name().toLowerCase(Locale.ROOT) + ")");
        }
    }

    private void info(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        msg(s, "<gradient:#8a2be2:#c77dff>" + d.id() + "</gradient>");
        msg(s, " <gray>Nom : <reset>" + d.displayName());
        msg(s, " <gray>Graine : <white>" + (d.seedCustomId() != null ? "✦ " + d.seedCustomId() : d.seed().name())
                + " <gray>sur <white>" + d.placeOn().name());
        msg(s, " <gray>Étapes : <white>" + d.stages() + " <gray>· ticks/étape <white>" + d.growthTicks()
                + " <gray>· lumière min <white>" + d.minLight() + " <gray>· eau <white>" + d.requiresWater()
                + " <gray>· engrais <white>" + d.bonemealable());
        if (d.usesLootTable()) {
            msg(s, " <gray>Récolte : <white>table de loot " + d.lootTableId()
                    + (module.lootTableExists(d.lootTableId()) ? "" : " <yellow>⚠ inconnue")
                    + " <gray>· repli <white>" + (d.dropItemId() != null ? "✦ " + d.dropItemId() : d.dropMaterial().name())
                    + " <gray>×" + d.dropMin() + "–" + d.dropMax()
                    + " <gray>· graines rendues <white>" + d.seedReturnMin() + "–" + d.seedReturnMax()
                    + " <gray>· replantable <white>" + d.replantable());
        } else {
            msg(s, " <gray>Récolte : <white>" + (d.dropItemId() != null ? "✦ " + d.dropItemId() : d.dropMaterial().name())
                    + " <gray>×" + d.dropMin() + "–" + d.dropMax()
                    + " <gray>· graines rendues <white>" + d.seedReturnMin() + "–" + d.seedReturnMax()
                    + " <gray>· replantable <white>" + d.replantable());
        }
    }

    private void setSeed(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop seed <id> <Material|custom:itemId>"); return; }
        String v = a[2];
        if (v.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            d.setSeedCustomId(v.substring("custom:".length()));
        } else {
            Material m = Material.matchMaterial(v.toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem()) { msg(s, "<red>Matériau invalide : " + v); return; }
            d.setSeedCustomId(null); d.setSeed(m);
        }
        module.put(d);
        msg(s, "<green>Graine de " + d.id() + " = <white>"
                + (d.seedCustomId() != null ? "✦ " + d.seedCustomId() : d.seed().name()));
    }

    private void setPlaceOn(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop placeon <id> <Material> (ex FARMLAND)"); return; }
        Material m = Material.matchMaterial(a[2].toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) { msg(s, "<red>Bloc invalide : " + a[2]); return; }
        d.setPlaceOn(m); module.put(d);
        msg(s, "<green>Support de " + d.id() + " = <white>" + m.name());
    }

    private void setStages(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop stages <id> <n> (1–16)"); return; }
        d.setStages(Integer.parseInt(a[2])); module.put(d);
        msg(s, "<green>Étapes de " + d.id() + " = <white>" + d.stages());
    }

    private void setGrowth(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop growth <id> <ticksParEtape>"); return; }
        d.setGrowthTicks(Integer.parseInt(a[2])); module.put(d);
        msg(s, "<green>Ticks/étape de " + d.id() + " = <white>" + d.growthTicks()
                + " <gray>(~" + (d.growthTicks() / 20) + "s)");
    }

    private void setLight(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop light <id> <0–15>"); return; }
        d.setMinLight(Integer.parseInt(a[2])); module.put(d);
        msg(s, "<green>Lumière min de " + d.id() + " = <white>" + d.minLight());
    }

    private void setWater(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop water <id> <on|off>"); return; }
        d.setRequiresWater(on(a[2])); module.put(d);
        msg(s, "<green>Hydratation requise pour " + d.id() + " = <white>" + d.requiresWater());
    }

    private void setDrop(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop drop <id> <Material|custom:itemId> [min] [max]"); return; }
        String v = a[2];
        if (v.toLowerCase(Locale.ROOT).startsWith("custom:")) {
            d.setDropItemId(v.substring("custom:".length()));
        } else {
            Material m = Material.matchMaterial(v.toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem()) { msg(s, "<red>Matériau invalide : " + v); return; }
            d.setDropItemId(null); d.setDropMaterial(m);
        }
        int min = a.length >= 4 ? Integer.parseInt(a[3]) : d.dropMin();
        int max = a.length >= 5 ? Integer.parseInt(a[4]) : Math.max(min, d.dropMax());
        d.setDropRange(min, max);
        module.put(d);
        msg(s, "<green>Récolte de " + d.id() + " = <white>"
                + (d.dropItemId() != null ? "✦ " + d.dropItemId() : d.dropMaterial().name())
                + " <gray>×" + d.dropMin() + "–" + d.dropMax());
    }

    private void setBonemeal(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop bonemeal <id> <on|off>"); return; }
        d.setBonemealable(on(a[2])); module.put(d);
        msg(s, "<green>Engrais (bone meal) pour " + d.id() + " = <white>" + d.bonemealable());
    }

    private void setLootTable(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop loottable <id> <tableId|none>"); return; }
        String v = a[2].equalsIgnoreCase("none") ? null : a[2];
        d.setLootTableId(v);
        module.put(d);
        if (d.usesLootTable()) {
            msg(s, "<green>Récolte de " + d.id() + " = table de loot <white>" + d.lootTableId());
            if (!module.lootTableExists(d.lootTableId())) {
                msg(s, "<yellow>⚠ Table de loot inconnue : <white>" + d.lootTableId() + "<yellow> (crée-la, sinon repli sur le drop fixe).");
            }
        } else {
            msg(s, "<green>Récolte de " + d.id() + " = drop fixe (table de loot retirée).");
        }
    }

    private void setReplant(CommandSender s, String[] a) {
        CropDef d = need(s, a); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon crop replant <id> <on|off>"); return; }
        d.setReplantable(on(a[2])); module.put(d);
        msg(s, "<green>Replantable pour " + d.id() + " = <white>" + d.replantable());
    }

    private void giveSeed(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon crop giveseed <joueur> <id> [n]"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { msg(s, "<red>Joueur hors-ligne."); return; }
        CropDef d = module.def(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        int n = a.length >= 4 ? Math.max(1, Integer.parseInt(a[3])) : 1;
        var seed = module.seedItem(d, n);
        if (seed == null) { msg(s, "<red>Graine introuvable (item custom manquant ?)."); return; }
        for (var overflow : t.getInventory().addItem(seed).values()) {
            t.getWorld().dropItemNaturally(t.getLocation(), overflow);  // inventaire plein → au sol
        }
        msg(s, "<green>Donné " + n + "× graine de " + d.id() + " à " + t.getName());
    }

    private CropDef need(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>Id manquant."); return null; }
        CropDef d = module.def(a[1]);
        if (d == null) msg(s, "<red>Id inconnu : " + a[1]);
        return d;
    }

    private static boolean on(String v) {
        return v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("oui");
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon crop</gradient> <gray>— cultures custom");
        String[] l = {
                "create <id> / delete <id> / list / info <id> / reload",
                "seed <id> <Material|custom:itemId> · placeon <id> <Material>",
                "stages <id> <n> · growth <id> <ticks> · light <id> <0-15> · water <id> <on|off>",
                "drop <id> <Material|custom:itemId> [min] [max] · replant <id> <on|off>",
                "loottable <id> <tableId|none>  (récolte = tirage d'une table de loot)",
                "giveseed <joueur> <id> [n]  (donne la graine à planter)"
        };
        for (String x : l) msg(s, " <dark_gray>▸ <gray>" + x);
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "delete", "list", "info", "seed", "placeon", "stages",
                    "growth", "light", "water", "drop", "loottable", "replant", "bonemeal", "giveseed", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "delete", "info", "seed", "placeon", "stages", "growth", "light", "water", "drop", "loottable", "replant", "bonemeal" ->
                        filter(new ArrayList<>(module.ids()), a[1]);
                case "giveseed" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), a[1]);
                default -> List.of();
            };
        }
        if (a.length == 3) {
            return switch (sub) {
                case "water", "replant", "bonemeal" -> filter(List.of("on", "off"), a[2]);
                case "giveseed" -> filter(new ArrayList<>(module.ids()), a[2]);
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
