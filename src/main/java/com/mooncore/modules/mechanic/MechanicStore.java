package com.mooncore.modules.mechanic;

import com.mooncore.util.MoonLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Persistance des mécaniques custom en YAML ({@code plugins/MoonCore/mechanics/<id>.yml}). */
public final class MechanicStore {

    /** Id sûr (slug, pas de séparateur de chemin → anti path-traversal). */
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,48}");

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private final File folder;
    private final MoonLogger log;

    public MechanicStore(File dataFolder, MoonLogger log) {
        this.folder = new File(dataFolder, "mechanics");
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warn("Impossible de créer le dossier mechanics/ pour les mécaniques custom.");
        }
    }

    public File folder() { return folder; }

    public Map<String, MechanicDef> loadAll() {
        Map<String, MechanicDef> out = new LinkedHashMap<>();
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                out.put(id, MechanicDef.load(id, YamlConfiguration.loadConfiguration(f)));
            } catch (Exception e) {
                log.error("Mécanique custom invalide : " + f.getName(), e);
            }
        }
        return out;
    }

    public void save(MechanicDef def) {
        if (!isValidId(def.id())) {
            log.warn("Id de mécanique invalide, sauvegarde refusée : " + def.id());
            return;
        }
        File f = new File(folder, def.id() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        def.save(yml);
        try {
            yml.save(f);
        } catch (IOException e) {
            log.error("Échec de sauvegarde de la mécanique " + def.id(), e);
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
