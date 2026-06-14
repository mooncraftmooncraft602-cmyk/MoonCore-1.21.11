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

    private static final double STRENGTH = 0.9;
    private static volatile GptPaletteSource gptCache;   // modèle Java chargé une fois

    /** Modèle GPT en JVM (chargé depuis plugins/MoonCore/forge-gpt.bin), mémoïsé. */
    private static GptPaletteSource gpt(MoonCore plugin) {
        GptPaletteSource g = gptCache;
        if (g == null) {
            synchronized (ForgeSubCommand.class) {
                g = gptCache;
                if (g == null) g = gptCache = new GptPaletteSource(new java.io.File(plugin.getDataFolder(), "forge-gpt.bin"));
            }
        }
        return g;
    }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        Player p = (Player) s;

        // /moon forge suggest <nom…>  -> propose des couleurs (sans rien forger)
        if (a.length >= 1 && (a[0].equalsIgnoreCase("suggest") || a[0].equalsIgnoreCase("propose"))) {
            suggest(plugin, p, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)).trim());
            return;
        }

        // /moon forge dsl <nom…> :: <programme DSL>  -> texture écrite dans le LANGAGE du serveur
        if (a.length >= 1 && a[0].equalsIgnoreCase("dsl")) {
            forgeDsl(plugin, p, java.util.Arrays.copyOfRange(a, 1, a.length));
            return;
        }

        int i = 0;
        boolean ai = a.length > 0 && a[0].equalsIgnoreCase("ai");
        boolean model = a.length > 0 && (a[0].equalsIgnoreCase("model") || a[0].equalsIgnoreCase("local"));
        if (ai || model) i = 1;
        if (a.length < i + 2) {
            msg(p, "<gray>/moon forge [ai|model] <textureBase> <nom…> [#couleur …]");
            msg(p, "<dark_gray>ex : /moon forge diamond_sword Épée du Vent");
            msg(p, "<dark_gray>     /moon forge model diamond_sword Épée Lunaire     <gray>(modèle IA, sur le serveur)");
            msg(p, "<dark_gray>     /moon forge diamond_sword Ma Lame #1b5e20 #66bb6a #e8f5e9   <gray>(tes couleurs)");
            msg(p, "<dark_gray>     /moon forge suggest Épée du Vent     <gray>(conseille des couleurs)");
            return;
        }
        String base = a[i];
        ForgeColors.Parsed parsed = ForgeColors.parseNameAndColors(a, i + 1);
        String name = parsed.name();
        ForgeService svc = new ForgeService(plugin, module);

        // 1) Couleurs explicites fournies -> priment sur tout (l'utilisateur choisit).
        if (!parsed.colors().isEmpty()) {
            ThemePalette pal = ForgeColors.paletteFromChosen(name, parsed.colors());
            msg(p, svc.forge(p, base, name, pal, STRENGTH).message());
            return;
        }

        // 2) Modèle IA EN JVM (sans dépendance externe) — async, repli déterministe.
        if (model) {
            GptPaletteSource g = gpt(plugin);
            if (!g.available()) {
                msg(p, "<yellow>Modèle non installé (forge-gpt.bin) — palette déduite du nom.");
                msg(p, svc.forge(p, base, name, null, STRENGTH).message());
                return;
            }
            msg(p, "<gray>🜂 Modèle (serveur) : couleurs pour <white>" + name + "<gray>…");
            g.resolve(name).whenComplete((pal, err) ->
                    plugin.schedulers().sync(() -> msg(p, svc.forge(p, base, name,
                            (err != null ? PaletteResolver.fromName(name) : pal), STRENGTH).message())));
            return;
        }

        // 3) IA externe (LLM) si demandée et configurée.
        if (ai) {
            AiAdminModule aiMod = plugin.moduleManager().get(AiAdminModule.class);
            if (aiMod == null || aiMod.client() == null || !aiMod.client().config().hasApiKey()) {
                msg(p, "<yellow>IA non configurée — palette déduite du nom.");
                msg(p, svc.forge(p, base, name, null, STRENGTH).message());
                return;
            }
            msg(p, "<gray>🜂 Forge IA pour <white>" + name + "<gray>…");
            ForgePaletteAI.resolve(aiMod.client(), name).whenComplete((pal, err) ->
                    plugin.schedulers().sync(() -> msg(p, svc.forge(p, base, name,
                            (err != null ? PaletteResolver.fromName(name) : pal), STRENGTH).message())));
            return;
        }

        // 4) Défaut : moteur déterministe (instantané, marche pour TOUT nom).
        msg(p, svc.forge(p, base, name, null, STRENGTH).message());
    }

    /**
     * {@code /moon forge dsl <nom…> :: <programme>} — forge un item dont la texture est écrite directement
     * dans le LANGAGE DSL du serveur (la voie qu'utilisent les IA). Le {@code ::} sépare le nom du programme.
     */
    private void forgeDsl(MoonCore plugin, Player p, String[] rest) {
        int sep = -1;
        for (int k = 0; k < rest.length; k++) if (rest[k].equals("::")) { sep = k; break; }
        if (sep < 1 || sep > rest.length - 2) {
            msg(p, "<gray>/moon forge dsl <nom…> :: <programme DSL>");
            msg(p, "<dark_gray>ex : /moon forge dsl Lame du Vent :: MCAP 5 10 13 2 1.7 1  JEWEL 5 10 1.6  GLINT 12 3");
            msg(p, "<dark_gray>formes : MCAP/GCAP/WCAP/SCAP x0 y0 x1 y1 w taper · MDISC/GDISC/… cx cy r · MRECT/GRECT · MELL cx cy rx ry · CLEAR · GTR");
            msg(p, "<dark_gray>décor : JEWEL cx cy r · FULLER x0 y0 x1 y1 · RIVET x y · GLINT x y   <gray>(M=métal thème, G=or, W=bois, S=acier)");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(rest, 0, sep)).trim();
        String dsl = String.join(" ", java.util.Arrays.copyOfRange(rest, sep + 1, rest.length)).trim();
        if (name.isBlank() || dsl.isBlank()) { msg(p, "<red>Nom ou programme manquant."); return; }
        msg(p, new ForgeService(plugin, module).forgeProgram(p, name, dsl, null).message());
    }

    /** Conseille des couleurs pour un nom (moteur déterministe + modèle si dispo), sans forger. */
    private void suggest(MoonCore plugin, Player p, String name) {
        if (name.isBlank()) { msg(p, "<red>/moon forge suggest <nom…>"); return; }
        ThemePalette kw = PaletteResolver.fromName(name);
        msg(p, "<gradient:#8a2be2:#c77dff>Couleurs proposées</gradient> <gray>pour <white>" + name);
        msg(p, " <gray>thème <white>" + kw.name() + "<gray> : <white>" + String.join(" ", kw.hexStops()));
        GptPaletteSource g = gpt(plugin);
        if (g.available()) {
            java.util.concurrent.CompletableFuture.supplyAsync(() -> g.suggestHex(name)).whenComplete((hex, err) ->
                    plugin.schedulers().sync(() -> {
                        if (err == null && hex != null && !hex.isEmpty())
                            msg(p, " <gray>modèle (serveur) : <white>" + String.join(" ", hex));
                    }));
        }
        msg(p, "<dark_gray>Forge avec ton choix : /moon forge <base> " + name + " #couleur #couleur …");
    }

    private static void msg(Player p, String mm) { p.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            List<String> opts = new ArrayList<>(COMMON_BASES);
            opts.add(0, "suggest");
            opts.add(0, "dsl");
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
