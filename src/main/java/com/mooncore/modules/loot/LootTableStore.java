package com.mooncore.modules.loot;

import com.mooncore.util.MoonLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Persistance des tables de loot custom en YAML ({@code plugins/MoonCore/loot/<id>.yml}). */
public final class LootTableStore {

    /** Id sûr (slug, pas de séparateur de chemin → anti path-traversal). */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,48}");

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private final File folder;
    private final MoonLogger log;

    public LootTableStore(File dataFolder, MoonLogger log) {
        this.folder = new File(dataFolder, "loot");
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warn("Impossible de créer le dossier loot/ pour les tables de loot custom.");
        }
    }

    public File folder() { return folder; }

    public Map<String, LootTableDef> loadAll() {
        Map<String, LootTableDef> out = new LinkedHashMap<>();
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                out.put(id, LootTableDef.load(id, YamlConfiguration.loadConfiguration(f)));
            } catch (Exception e) {
                log.error("Table de loot custom invalide : " + f.getName(), e);
            }
        }
        return out;
    }

    public void save(LootTableDef def) {
        if (!isValidId(def.id())) {
            log.warn("Id de table de loot invalide, sauvegarde refusée : " + def.id());
            return;
        }
        File f = new File(folder, def.id() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        def.save(yml);
        try {
            yml.save(f);
        } catch (IOException e) {
            log.error("Échec de sauvegarde de la table de loot " + def.id(), e);
        }
    }

    public boolean delete(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (!isValidId(norm)) return false;
        File f = new File(folder, norm + ".yml");
        return f.exists() && f.delete();
    }

    public boolean exists(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (!isValidId(norm)) return false;
        return new File(folder, norm + ".yml").exists();
    }
}
