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

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

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
        if (action.equals("createall") || action.equals("createmulti")) { createAll(plugin, s, a); return; }

        if (a.length < 2) { help(s); return; }
        ContentTypeHandler h = registry.get(a[1]);
        if (h == null) { msg(s, "<red>Type inconnu : <white>" + a[1] + "</white>. Voir <white>/moon content types"); return; }

        // Permission granulaire par type : refus si mooncore.admin.create.<type> est explicitement nié.
        if (!canManage(s, h.type())) {
            msg(s, "<red>Permission refusée pour le type <white>" + h.type() + "</white> "
                    + "<dark_gray>(mooncore.admin.create." + h.type() + ").");
            return;
        }

        switch (action) {
            case "create" -> create(plugin, s, h, a);
            case "delete", "remove" -> delete(s, h, a);
            case "list" -> list(s, h);
            case "info" -> info(s, h, a);
            case "clone" -> clone(s, h, a);
            case "give" -> give(s, h, a);
            case "edit" -> edit(s, h, a);
            default -> help(s);
        }
    }

    private void create(MoonCore plugin, CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon content create " + h.type() + " <id> [description IA… | --dry]"); return; }
        String id = a[2];

        // Avec description → génération IA (si le type la supporte et l'IA est dispo).
        if (a.length > 3) {
            // Flag dry-run : --dry / dry en dernier → prévisualise sans persister.
            int end = a.length;
            boolean dry = isDryFlag(a[end - 1]);
            if (dry) end--;
            if (end <= 3) { msg(s, "<red>Description manquante avant --dry."); return; }
            String description = String.join(" ", java.util.Arrays.copyOfRange(a, 3, end));
            createWithAi(plugin, s, h, id, description, dry);
            return;
        }

        if (h.create(id)) msg(s, "<green>" + h.type() + " <white>" + id + "<green> créé. <gray>Édite-le avec <white>/moon content edit " + h.type() + " " + id);
        else msg(s, "<red>Création impossible (id invalide ou déjà existant) : <white>" + id);
    }

    private static boolean isDryFlag(String token) {
        return token.equalsIgnoreCase("--dry") || token.equalsIgnoreCase("dry") || token.equalsIgnoreCase("dryrun");
    }

    /** Génère une définition depuis une description en langage naturel via l'IA (dry-run = prévisualise). */
    private void createWithAi(MoonCore plugin, CommandSender s, ContentTypeHandler h, String id, String description, boolean dry) {
        String system = h.aiSystemPrompt();
        if (system == null) {
            msg(s, "<yellow>Pas de génération IA pour le type <white>" + h.type() + "</white>. "
                    + "Crée-le vide : <white>/moon content create " + h.type() + " " + id);
            return;
        }
        var ai = plugin.moduleManager().get(com.mooncore.modules.ai.AiAdminModule.class);
        if (ai == null || ai.client() == null || !ai.client().config().hasApiKey()) {
            msg(s, "<red>IA indisponible (module ai-assistant inactif ou clé API absente).");
            return;
        }
        if (!dry && h.exists(id)) { msg(s, "<red>Cet id existe déjà : <white>" + id); return; }

        msg(s, "<gray>" + (dry ? "Prévisualisation IA" : "Génération IA") + " d'un <white>" + h.type()
                + "</white> « <white>" + description + "</white> »…");
        ai.client().ask(system, description).whenComplete((text, err) ->
                plugin.schedulers().sync(() -> {
                    if (err != null || text == null || text.isBlank()) {
                        msg(s, "<red>Échec IA : " + (err != null ? err.getMessage() : "réponse vide"));
                        return;
                    }
                    String cmd = "content create " + h.type() + (dry ? " --dry" : "");
                    if (dry) {
                        String preview = h.validateAi(text, id);
                        if (preview != null) {
                            msg(s, "<aqua>Dry-run</aqua> <gray>— le " + h.type() + " serait : <reset>" + preview);
                            msg(s, "<dark_gray>Relance sans <white>--dry</white> pour créer.");
                        } else {
                            msg(s, "<red>Sortie IA invalide (dry-run) pour un " + h.type() + ".");
                        }
                        audit(ai, s, cmd, description, preview != null ? preview : "invalide", preview != null ? "dry" : "fail");
                        return;
                    }
                    String created = h.createFromAi(text, id);
                    if (created != null) {
                        msg(s, "<green>" + h.type() + " <white>" + created + "<green> généré par l'IA. "
                                + "<gray>Édite : <white>/moon content edit " + h.type() + " " + created);
                        rebuildPack(plugin);
                    } else {
                        msg(s, "<red>Sortie IA invalide pour un " + h.type() + ".");
                    }
                    audit(ai, s, cmd, description, created != null ? created : "invalide", created != null ? "ok" : "fail");
                }));
    }

    private void rebuildPack(MoonCore plugin) {
        plugin.services().get(com.mooncore.api.resourcepack.ResourcePackService.class).ifPresent(rp -> {
            rp.rebuild();
            rp.resendAll();
        });
    }

    /** Trace une création IA dans {@code mooncore_ai_audit} (best-effort). */
    private static void audit(com.mooncore.modules.ai.AiAdminModule ai, CommandSender s,
                              String command, String prompt, String result, String status) {
        if (ai == null || ai.audit() == null) return;
        ai.audit().record(s.getName(), command, prompt, result, status, System.currentTimeMillis());
    }

    /**
     * Multi-création IA chaînée (Étape E5) : une description génère plusieurs objets via
     * {@code unifiedCreateSystem()} ; chaque élément {@code creations[]} est routé vers le
     * {@link ContentTypeHandler} de son {@code kind} (item, block, crop…) qui le valide et le persiste.
     */
    @SuppressWarnings("unchecked")
    private void createAll(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length < 2) {
            msg(s, "<red>/moon content createall <description> <gray>(ex : <white>un minerai lunaire, sa pioche et sa recette</white>)");
            return;
        }
        var ai = plugin.moduleManager().get(com.mooncore.modules.ai.AiAdminModule.class);
        if (ai == null || ai.client() == null || !ai.client().config().hasApiKey()) {
            msg(s, "<red>IA indisponible (module ai-assistant inactif ou clé API absente).");
            return;
        }
        String description = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
        msg(s, "<gray>Génération IA multiple « <white>" + description + "</white> »…");

        ai.client().ask(ai.prompts().unifiedCreateSystem(), description).whenComplete((text, err) ->
                plugin.schedulers().sync(() -> {
                    if (err != null || text == null || text.isBlank()) {
                        msg(s, "<red>Échec IA : " + (err != null ? err.getMessage() : "réponse vide"));
                        return;
                    }
                    java.util.Map<String, Object> root = ai.validator().extractMap(text);
                    Object listObj = root == null ? null : root.get("creations");
                    if (!(listObj instanceof java.util.List<?> creations) || creations.isEmpty()) {
                        msg(s, "<red>Rien à créer (réponse IA inattendue).");
                        return;
                    }
                    int created = 0, skipped = 0;
                    int max = Math.min(8, creations.size());
                    for (int i = 0; i < max; i++) {
                        if (!(creations.get(i) instanceof java.util.Map<?, ?> elRaw)) continue;
                        java.util.Map<String, Object> el = (java.util.Map<String, Object>) elRaw;
                        String kind = normalizeKind(String.valueOf(el.getOrDefault("kind", "item")));
                        ContentTypeHandler h = registry.get(kind);
                        if (h == null) { skipped++; continue; }
                        try {
                            String createdId = h.createFromAi(GSON.toJson(el), null);
                            if (createdId != null) { created++; msg(s, "<green>▸ " + kind + " <white>" + createdId); }
                            else skipped++;
                        } catch (Exception ex) {
                            skipped++;
                            msg(s, "<yellow>⚠ élément ignoré (" + kind + ") : " + ex.getMessage());
                        }
                    }
                    if (created > 0) rebuildPack(plugin);
                    msg(s, "<green>" + created + " élément(s) créé(s)"
                            + (skipped > 0 ? " <gray>(" + skipped + " ignoré(s) : type non géré par la commande unifiée ou sortie invalide)" : "")
                            + ".");
                    audit(ai, s, "content createall", description,
                            created + " créé(s), " + skipped + " ignoré(s)", created > 0 ? "ok" : "fail");
                }));
    }

    /** Normalise les synonymes de {@code kind} de l'IA vers les types enregistrés. */
    private static String normalizeKind(String kind) {
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "ore", "minerai" -> "block";
            case "mob" -> "boss";
            case "plant", "plante", "culture" -> "crop";
            default -> kind.toLowerCase(Locale.ROOT);
        };
    }

    private void delete(CommandSender s, ContentTypeHandler h, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon content delete " + h.type() + " <id> confirm"); return; }
        String id = a[2];
        if (!h.exists(id)) { msg(s, "<red>Id inconnu : <white>" + id); return; }
        boolean confirmed = a.length >= 4 && a[3].equalsIgnoreCase("confirm");
        if (!confirmed) {
            msg(s, "<yellow>⚠ Suppression DÉFINITIVE de <white>" + h.type() + " " + id + "</white>. "
                    + "Confirme : <white>/moon content delete " + h.type() + " " + id + " confirm");
            return;
        }
        msg(s, h.delete(id) ? "<green>Supprimé : <white>" + id : "<red>Échec de suppression : <white>" + id);
    }

    /** Refuse seulement si {@code mooncore.admin.create.<type>} est explicitement nié (granularité par type). */
    private static boolean canManage(CommandSender s, String type) {
        String node = "mooncore.admin.create." + type;
        return !s.isPermissionSet(node) || s.hasPermission(node);
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
        if (a.length < 4) { msg(s, "<red>/moon content clone " + h.type() + " <source> <nouvelId>"); return; }
        if (!h.exists(a[2])) { msg(s, "<red>Source inconnue : <white>" + a[2]); return; }
        if (h.cloneEntry(a[2], a[3])) {
            msg(s, "<green>" + h.type() + " <white>" + a[2] + "<green> cloné en <white>" + a[3] + "<green>.");
        } else if (h.create(a[3])) {
            msg(s, "<yellow>Copie profonde non supportée pour " + h.type()
                    + " : nouvel <white>" + a[3] + "</white> vide créé (à éditer).");
        } else {
            msg(s, "<red>Clonage impossible (id invalide ou déjà existant) : <white>" + a[3]);
        }
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
                "create <type> <id> [description IA… | --dry] · edit <type> <id>",
                "delete <type> <id> confirm · list <type> · info <type> <id>",
                "clone <type> <src> <nouvel> · give <type> <joueur> <id> [n]",
                "createall <description> (multi-création IA chaînée) · types",
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
            return filter(List.of("create", "createall", "edit", "delete", "list", "info", "clone", "give", "types"), a[0]);
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
