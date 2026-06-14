package com.mooncore.modules.market;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /market} — marché dynamique offre/demande (achat/vente + admin). Ouvre le GUI sans argument. */
public final class MarketSubCommand implements SubCommand {

    private static final String ADMIN = "mooncore.admin.market";
    private final MarketModule module;

    public MarketSubCommand(MarketModule module) { this.module = module; }

    @Override public String name() { return "market"; }
    @Override public List<String> aliases() { return List.of("marche", "bourse"); }
    @Override public String permission() { return "mooncore.market.use"; }
    @Override public String description() { return "Marché dynamique (prix offre/demande)"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) { new MarketMenu(module, p).open(); }
            else msg(s, "<gray>/market <list|info|buy|sell|sellall> …");
            return;
        }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "list", "liste" -> list(s);
                case "info", "prix" -> info(s, a);
                case "buy", "achat", "acheter" -> buy(s, a);
                case "sell", "vente", "vendre" -> sell(s, a, false);
                case "sellall", "vendretout" -> sell(s, a, true);
                // ---- admin ----
                case "add", "ajouter" -> admin(s, () -> add(s, a));
                case "set", "regler" -> admin(s, () -> set(s, a));
                case "remove", "supprimer" -> admin(s, () -> { msg(s, module.remove(a[1]) ? "<green>Retiré : " + a[1] : "<red>Inconnu."); module.saveAll(); });
                case "restock" -> admin(s, () -> restock(s, a));
                case "reload" -> admin(s, () -> { module.load(); msg(s, "<green>Marché rechargé."); });
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            msg(s, "<red>Nombre invalide.");
        }
    }

    private void admin(CommandSender s, Runnable r) {
        if (!s.hasPermission(ADMIN)) { msg(s, "<red>Réservé aux admins."); return; }
        r.run();
    }

    // ---- Joueur ----

    private void list(CommandSender s) {
        if (module.items().isEmpty()) { msg(s, "<gray>Marché vide."); return; }
        msg(s, "<gradient:#2ecc71:#27ae60>Marché</gradient> <dark_gray>(" + module.items().size() + " marchandises)");
        for (MarketItem m : module.items()) {
            msg(s, " <dark_gray>▸ <white>" + m.id() + " <gray>achat <green>" + MarketModule.round2(m.unitBuyPrice())
                    + "$<gray> · vente <red>" + MarketModule.round2(m.unitSellPrice()) + "$<gray> " + trend(m));
        }
        msg(s, "<dark_gray>/market info <id> · /market buy <id> [n] · /market sell <id> [n]");
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/market info <id>"); return; }
        MarketItem m = module.item(a[1]);
        if (m == null) { msg(s, "<red>Marchandise inconnue : " + a[1]); return; }
        msg(s, "<gradient:#2ecc71:#27ae60>" + m.id() + "</gradient> <gray>— " + m.displayName()
                + (m.isCustom() ? " <dark_gray>(✦" + m.customId() + ")" : ""));
        msg(s, " <gray>Prix achat : <green>" + MarketModule.round2(m.unitBuyPrice()) + "$<gray> · vente : <red>"
                + MarketModule.round2(m.unitSellPrice()) + "$<gray> " + trend(m));
        msg(s, " <gray>Base <white>" + m.basePrice() + "$<gray> · stock <white>" + Math.round(m.stock())
                + "<gray>/<white>" + Math.round(m.equilibrium()) + " <gray>(équilibre) · production <white>" + m.production() + "<gray>/tick");
        msg(s, " <gray>Élasticité <white>" + m.elasticity() + "<gray> · marge vente <white>" + Math.round(m.sellMargin() * 100) + "%");
    }

    private void buy(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 2) { msg(s, "<red>/market buy <id> [quantité]"); return; }
        int qty = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        MarketModule.TxResult r = module.buy(p, a[1], qty);
        msg(s, r.message());
    }

    private void sell(CommandSender s, String[] a, boolean all) {
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 2) { msg(s, "<red>/market " + (all ? "sellall <id>" : "sell <id> [quantité]")); return; }
        int qty;
        if (all) {
            MarketItem m = module.item(a[1]);
            qty = m == null ? 0 : module.countOwned(p, m);
        } else {
            qty = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        }
        MarketModule.TxResult r = module.sell(p, a[1], qty);
        msg(s, r.message());
    }

    /** Tendance affichée (indice de marché). */
    private static String trend(MarketItem m) {
        double idx = m.marketIndex();
        int pct = (int) Math.round((idx - 1.0) * 100);
        if (pct > 1) return "<red>▲ +" + pct + "%";
        if (pct < -1) return "<green>▼ " + pct + "%";
        return "<gray>= au pair";
    }

    // ---- Admin ----

    private void add(CommandSender s, String[] a) {
        if (a.length < 4) { msg(s, "<red>/market add <id> <Material|custom:id> <prixBase> [équilibre] [production]"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (module.exists(id)) { msg(s, "<red>Cet id existe déjà."); return; }
        MarketItem m = new MarketItem(id);
        String src = a[2];
        if (src.toLowerCase(Locale.ROOT).startsWith("custom:")) m.setCustomId(src.substring("custom:".length()));
        else m.setMaterial(src);
        m.setDisplayName(id.replace('_', ' '));
        m.setBasePrice(Double.parseDouble(a[3]));
        double eq = a.length >= 5 ? Double.parseDouble(a[4]) : 1000;
        m.setEquilibrium(eq);
        m.setStock(eq);
        if (a.length >= 6) m.setProduction(Double.parseDouble(a[5]));
        module.put(m); module.saveAll();
        msg(s, "<green>Marchandise <white>" + id + "<green> ajoutée (achat " + MarketModule.round2(m.unitBuyPrice()) + "$).");
    }

    private void set(CommandSender s, String[] a) {
        if (a.length < 4) {
            msg(s, "<red>/market set <id> <base|equilibre|stock|elasticite|production|marge|bornes> <valeur> [valeur2]");
            return;
        }
        MarketItem m = module.item(a[1]);
        if (m == null) { msg(s, "<red>Marchandise inconnue : " + a[1]); return; }
        double v = Double.parseDouble(a[3]);
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "base", "prix" -> m.setBasePrice(v);
            case "equilibre", "equilibrium" -> m.setEquilibrium(v);
            case "stock" -> m.setStock(v);
            case "elasticite", "elasticity" -> m.setElasticity(v);
            case "production" -> m.setProduction(v);
            case "marge", "margin" -> m.setSellMargin(v > 1 ? v / 100.0 : v);
            case "bornes", "bounds" -> m.setPriceBounds(v, a.length >= 5 ? Double.parseDouble(a[4]) : m.maxFactor());
            default -> { msg(s, "<red>Champ inconnu : " + a[2]); return; }
        }
        module.saveAll();
        msg(s, "<green>" + m.id() + " · " + a[2] + " = <white>" + a[3] + " <gray>(achat " + MarketModule.round2(m.unitBuyPrice()) + "$).");
    }

    private void restock(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/market restock <id|*>"); return; }
        if (a[1].equals("*")) {
            for (MarketItem m : module.items()) m.setStock(m.equilibrium());
            module.saveAll();
            msg(s, "<green>Tout le marché remis à l'équilibre.");
            return;
        }
        MarketItem m = module.item(a[1]);
        if (m == null) { msg(s, "<red>Marchandise inconnue : " + a[1]); return; }
        m.setStock(m.equilibrium()); module.saveAll();
        msg(s, "<green>" + m.id() + " remis à l'équilibre (achat " + MarketModule.round2(m.unitBuyPrice()) + "$).");
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#2ecc71:#27ae60>/market</gradient> <gray>— marché dynamique");
        msg(s, " <dark_gray>▸ <gray>list · info <id> · buy <id> [n] · sell <id> [n] · sellall <id>");
        if (s.hasPermission(ADMIN)) {
            msg(s, " <dark_gray>▸ <gray>add <id> <Material|custom:id> <base> [équilibre] [prod] · set <id> <champ> <valeur>");
            msg(s, " <dark_gray>▸ <gray>restock <id|*> · remove <id> · reload");
        }
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("list", "info", "buy", "sell", "sellall"));
            if (s.hasPermission(ADMIN)) subs.addAll(List.of("add", "set", "remove", "restock", "reload"));
            return filter(subs, a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2 && List.of("info", "buy", "sell", "sellall", "set", "remove", "restock").contains(sub)) {
            List<String> ids = new ArrayList<>();
            for (MarketItem m : module.items()) ids.add(m.id());
            if (sub.equals("restock")) ids.add(0, "*");
            return filter(ids, a[1]);
        }
        if (a.length == 3 && sub.equals("set")) {
            return filter(List.of("base", "equilibre", "stock", "elasticite", "production", "marge", "bornes"), a[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
