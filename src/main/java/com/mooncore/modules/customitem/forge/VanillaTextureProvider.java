package com.mooncore.modules.customitem.forge;

import com.mooncore.MoonCore;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fournit les textures <b>vanilla</b> (item/bloc/armure) côté serveur en les extrayant du <b>client.jar</b>
 * de Minecraft présent localement (depuis la 1.13, les textures sont dans le jar client, pas dans
 * {@code assets/objects}). Résout {@code diamond_sword} → {@code assets/minecraft/textures/item/diamond_sword.png}
 * (ou {@code .../block/...}), met en cache le PNG dans {@code plugins/MoonCore/vanilla-textures/} et renvoie le fichier.
 *
 * <p>Le jar est trouvé via la config {@code textures.vanilla-jar}, sinon auto-détecté dans
 * {@code %APPDATA%/.minecraft/versions/*}. Sert de base à l'import de texture et à la forge intelligente.</p>
 */
public final class VanillaTextureProvider {

    private final MoonCore plugin;
    private File jar;                 // client.jar résolu (mémoïsé)
    private boolean searched;

    public VanillaTextureProvider(MoonCore plugin) { this.plugin = plugin; }

    /** Chemins d'entrée candidats dans le jar pour un nom de texture (item d'abord, puis bloc). Pur/testable. */
    public static List<String> entryCandidates(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace('\\', '/');
        if (n.endsWith(".png")) n = n.substring(0, n.length() - 4);
        List<String> out = new ArrayList<>();
        if (n.contains("/")) {                                    // ex "item/diamond_sword" ou "block/stone"
            out.add("assets/minecraft/textures/" + n + ".png");
        } else {
            out.add("assets/minecraft/textures/item/" + n + ".png");
            out.add("assets/minecraft/textures/block/" + n + ".png");
        }
        return out;
    }

    /** Le jar client a-t-il été localisé ? */
    public boolean available() { return jar() != null; }

    /** Fichier du jar client, ou null si introuvable. Mémoïsé. */
    public synchronized File jar() {
        if (jar != null) return jar;
        if (searched) return null;
        searched = true;
        // 1) config explicite
        String cfg = plugin.getConfig().getString("textures.vanilla-jar", "");
        if (cfg != null && !cfg.isBlank()) {
            File f = new File(cfg);
            if (containsTextures(f)) { jar = f; return jar; }
        }
        // 2) auto-détection dans %APPDATA%/.minecraft/versions/*
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            File versions = new File(appData, ".minecraft/versions");
            File best = scanVersions(versions);
            if (best != null) { jar = best; return jar; }
        }
        return null;
    }

    /** Cherche, dans les sous-dossiers de {@code versions}, un .jar contenant des textures (préf. 1.21.x récent). */
    private static File scanVersions(File versions) {
        if (versions == null || !versions.isDirectory()) return null;
        File[] dirs = versions.listFiles(File::isDirectory);
        if (dirs == null) return null;
        File best = null;
        for (File dir : dirs) {
            File[] jars = dir.listFiles(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".jar"));
            if (jars == null) continue;
            for (File j : jars) {
                if (!containsTextures(j)) continue;
                // priorise un nom qui ressemble à une version 1.21.x (le plus "grand" lexicalement)
                if (best == null || preferOver(j.getName(), best.getName())) best = j;
            }
        }
        return best;
    }

    private static boolean preferOver(String candidate, String current) {
        boolean c21 = candidate.contains("1.21"), b21 = current.contains("1.21");
        if (c21 != b21) return c21;                      // un 1.21.x bat un non-1.21
        return candidate.compareToIgnoreCase(current) > 0;
    }

    /** True si le jar contient l'arborescence de textures item vanilla. */
    private static boolean containsTextures(File f) {
        if (f == null || !f.isFile()) return false;
        try (ZipFile zip = new ZipFile(f)) {
            return zip.getEntry("assets/minecraft/textures/item/diamond_sword.png") != null
                    || zip.getEntry("assets/minecraft/textures/item/iron_ingot.png") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Résout une texture vanilla par nom : renvoie le PNG (extrait/caché dans {@code vanilla-textures/}), ou
     * null si le jar est introuvable / la texture inexistante.
     */
    public synchronized File resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace('/', '_');
        if (key.endsWith(".png")) key = key.substring(0, key.length() - 4);
        File cache = new File(plugin.getDataFolder(), "vanilla-textures/" + key + ".png");
        if (cache.isFile()) return cache;
        File j = jar();
        if (j == null) return null;
        try (ZipFile zip = new ZipFile(j)) {
            for (String path : entryCandidates(name)) {
                ZipEntry e = zip.getEntry(path);
                if (e == null) continue;
                cache.getParentFile().mkdirs();
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, cache.toPath());
                }
                return cache;
            }
        } catch (Exception ex) {
            plugin.logger().warn("[Forge] Extraction texture vanilla '" + name + "' échouée : " + ex.getMessage());
        }
        return null;
    }
}
