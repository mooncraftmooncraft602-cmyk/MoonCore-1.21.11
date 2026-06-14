package com.mooncore.data.content;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Importe le contenu YAML existant ({@code items/}, {@code blocks/}, {@code content/bosses/})
 * dans le store SQL universel {@link UniversalContentStore} — Étape A4.
 * <p>
 * <b>Idempotent</b> : chaque entrée est écrite via {@code upsert} (réimporter ne duplique pas).
 * Conserve intégralement les fichiers YAML (lecture seule). Les fichiers item/block sont plats
 * ({@code <id>.yml} = une définition) ; les fichiers boss regroupent plusieurs définitions sous
 * une section {@code bosses.<id>}.
 */
public final class ContentMigrator {

    public record Result(int items, int blocks, int bosses, int crops, int loot, int errors) {
        public int total() { return items + blocks + bosses + crops + loot; }
    }

    private ContentMigrator() {}

    /** Lance la migration (à appeler sur un thread asynchrone : I/O fichier + DB). */
    public static Result migrate(File dataFolder, UniversalContentStore store, long nowMs) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        AtomicInteger errors = new AtomicInteger();

        int items = migrateFlat(new File(dataFolder, "items"), "item", store, nowMs, futures, errors);
        int blocks = migrateFlat(new File(dataFolder, "blocks"), "block", store, nowMs, futures, errors);
        int bosses = migrateBosses(new File(dataFolder, "content/bosses"), store, nowMs, futures, errors);
        int crops = migrateFlat(new File(dataFolder, "crops"), "crop", store, nowMs, futures, errors);
        int loot = migrateFlat(new File(dataFolder, "loot"), "loot", store, nowMs, futures, errors);

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            errors.incrementAndGet();
        }
        return new Result(items, blocks, bosses, crops, loot, errors.get());
    }

    /** Dossier plat : un fichier {@code <id>.yml} = une définition. */
    private static int migrateFlat(File folder, String type, UniversalContentStore store, long nowMs,
                                   List<CompletableFuture<?>> futures, AtomicInteger errors) {
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!UniversalContentStore.isValidId(id)) { errors.incrementAndGet(); continue; }
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                futures.add(store.upsert(type, id, ContentJson.toJson(yml),
                        ContentSchemas.currentVersion(type), nowMs));
                count++;
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
        return count;
    }

    /** Dossier boss : chaque fichier contient une section {@code bosses.<id>}. */
    private static int migrateBosses(File folder, UniversalContentStore store, long nowMs,
                                     List<CompletableFuture<?>> futures, AtomicInteger errors) {
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                ConfigurationSection sec = yml.getConfigurationSection("bosses");
                if (sec == null) continue;
                for (String key : sec.getKeys(false)) {
                    String id = key.toLowerCase(Locale.ROOT);
                    ConfigurationSection b = sec.getConfigurationSection(key);
                    if (b == null) continue;
                    if (!UniversalContentStore.isValidId(id)) { errors.incrementAndGet(); continue; }
                    futures.add(store.upsert("boss", id, ContentJson.toJson(b),
                            ContentSchemas.currentVersion("boss"), nowMs));
                    count++;
                }
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
        return count;
    }
}
