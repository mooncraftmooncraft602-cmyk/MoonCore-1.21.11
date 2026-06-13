package com.mooncore.modules.model.editor;

import com.mooncore.modules.model.BlockBenchImporter;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Persistance des modèles 3D de l'éditeur en {@code .bbmodel} dans {@code plugins/MoonCore/models/}
 * (Étape D7). Sauvegarde via {@link BlockBenchExporter}, chargement via {@code BlockBenchImporter}
 * (round-trip avec l'éditeur). Format {@code .bbmodel} = compatible BlockBench (édition externe possible).
 */
public final class RigModelStore {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,48}");

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    private final File folder;
    private final MoonLogger log;

    public RigModelStore(File dataFolder, MoonLogger log) {
        this.folder = new File(dataFolder, "models");
        this.log = log;
        if (!folder.exists() && !folder.mkdirs() && log != null) {
            log.warn("Impossible de créer le dossier models/ pour les modèles 3D.");
        }
    }

    public File folder() { return folder; }

    /** Liste les ids de modèles {@code .bbmodel} présents. */
    public List<String> list() {
        List<String> out = new ArrayList<>();
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
        if (files == null) return out;
        for (File f : files) {
            out.add(f.getName().substring(0, f.getName().length() - ".bbmodel".length()).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    public boolean exists(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        return isValidId(norm) && new File(folder, norm + ".bbmodel").isFile();
    }

    public boolean save(EditableRig rig) {
        if (!isValidId(rig.id)) {
            if (log != null) log.warn("Id de modèle invalide, sauvegarde refusée : " + rig.id);
            return false;
        }
        File f = new File(folder, rig.id.toLowerCase(Locale.ROOT) + ".bbmodel");
        try {
            Files.writeString(f.toPath(), BlockBenchExporter.toBbmodel(rig), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            if (log != null) log.error("Échec de sauvegarde du modèle " + rig.id, e);
            return false;
        }
    }

    /** Charge un modèle en {@link EditableRig} (sans dépendance Bukkit). {@code null} si absent/invalide. */
    public EditableRig load(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (!isValidId(norm)) return null;
        File f = new File(folder, norm + ".bbmodel");
        if (!f.isFile()) return null;
        try {
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            BlockBenchImporter.RawRig raw = BlockBenchImporter.parse(json, norm);
            return EditableRig.fromRaw(raw);
        } catch (Exception e) {
            if (log != null) log.error("Modèle .bbmodel invalide : " + norm, e);
            return null;
        }
    }

    public boolean delete(String id) {
        String norm = id == null ? null : id.toLowerCase(Locale.ROOT);
        if (!isValidId(norm)) return false;
        File f = new File(folder, norm + ".bbmodel");
        return f.isFile() && f.delete();
    }
}
