package com.mooncore.modules.customitem.forge;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.ai.AiAdminModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon forge [ai] <textureBase> <nom…>} — forge un item dont la texture est la base vanilla/custom
 * <b>recolorée selon le thème du nom</b> (ex. {@code /moon forge diamond_sword épée du vent} → diamond_sword
 * en blanc/vert). Sans {@code ai}, palette déduite par mots-clés (instantané, hors-ligne). Avec {@code ai},
 * le LLM choisit les couleurs (si une clé API est configurée), sinon repli automatique. Marche pour armes,
 * outils, armures, minerais — tout ce qui a une texture de base.
 */
public final class ForgeSubCommand implements SubCommand {

    private final CustomItemManagerModule module;

    public ForgeSubCommand(CustomItemManagerModule module) { this.module = module; }

    @Override public String name() { return "forge"; }
    @Override public List<String> aliases() { return List.of("forger"); }
    @Override public String permission() { return "mooncore.admin.items"; }
    @Override public String description() { return "Forge un item recoloré selon le thème du nom"; }
    @Override public String category() { return "admin"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        Player p = (Player) s;
        int i = 0;
        boolean ai = a.length > 0 && a[0].equalsIgnoreCase("ai");
        boolean model = a.length > 0 && (a[0].equalsIgnoreCase("model") || a[0].equalsIgnoreCase("local"));
        if (ai || model) i = 1;
        if (a.length < i + 2) {
            msg(p, "<gray>/moon forge [ai|model] <textureBase> <nom…>");
            msg(p, "<dark_gray>ex : /moon forge diamond_sword Épée du Vent  ·  /moon forge model diamond_sword Épée du Vent");
            return;
        }
        String base = a[i];
        String name = String.join(" ", java.util.Arrays.copyOfRange(a, i + 1, a.length)).trim();
        ForgeService svc = new ForgeService(plugin, module);
        double strength = 0.9;

        if (model) {
            // Modèle local auto-hébergé (sidecar) : palette async, forge resynchronisée sur le thread principal.
            String endpoint = "http://127.0.0.1:8770/palette";
            int timeout = 8;
            AiAdminModule am = plugin.moduleManager().get(AiAdminModule.class);
            if (am != null && am.client() != null) {
                endpoint = am.client().config().localModelEndpoint();
                timeout = am.client().config().localModelTimeoutSeconds();
            }
            msg(p, "<gray>🜂 Modèle local : palette pour <white>" + name + "<gray>… <dark_gray>(repli auto si éteint)");
            new LocalModelPaletteSource(endpoint, timeout).resolve(name).whenComplete((palette, err) ->
                    plugin.schedulers().sync(() -> {
                        ThemePalette pal = (err != null || palette == null) ? PaletteResolver.fromName(name) : palette;
                        msg(p, svc.forge(p, base, name, pal, strength).message());
                    }));
            return;
        }

        if (!ai) {
            ForgeService.Result r = svc.forge(p, base, name, null, strength);
            msg(p, r.message());
            return;
        }
        // Mode IA : palette choisie par le LLM (async), forge resynchronisée sur le thread principal.
        AiAdminModule aiMod = plugin.moduleManager().get(AiAdminModule.class);
        if (aiMod == null || aiMod.client() == null || !aiMod.client().config().hasApiKey()) {
            msg(p, "<yellow>IA non configurée — palette déduite du nom.");
            ForgeService.Result r = svc.forge(p, base, name, null, strength);
            msg(p, r.message());
            return;
        }
        msg(p, "<gray>🜂 Forge IA en cours pour <white>" + name + "<gray>…");
        ForgePaletteAI.resolve(aiMod.client(), name).whenComplete((palette, err) ->
                plugin.schedulers().sync(() -> {
                    ThemePalette pal = (err != null || palette == null) ? PaletteResolver.fromName(name) : palette;
                    ForgeService.Result r = svc.forge(p, base, name, pal, strength);
                    msg(p, r.message());
                }));
    }

    private static void msg(Player p, String mm) { p.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            List<String> opts = new ArrayList<>(COMMON_BASES);
            opts.add(0, "model");
            opts.add(0, "ai");
            return filter(opts, a[0]);
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("ai") || a[0].equalsIgnoreCase("model") || a[0].equalsIgnoreCase("local")))
            return filter(COMMON_BASES, a[1]);
        return List.of();
    }

    private static final List<String> COMMON_BASES = List.of(
            "wooden_sword", "stone_sword", "iron_sword", "golden_sword", "diamond_sword", "netherite_sword",
            "iron_pickaxe", "diamond_pickaxe", "netherite_pickaxe", "iron_axe", "diamond_axe",
            "bow", "trident", "shield", "stick",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
            "netherite_chestplate", "iron_chestplate", "leather_chestplate",
            "diamond", "emerald", "iron_ingot", "gold_ingot", "netherite_ingot", "amethyst_shard");

    private static List<String> filter(List<String> options, String prefix) {
        String pf = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(pf)) out.add(o);
        return out;
    }
}
