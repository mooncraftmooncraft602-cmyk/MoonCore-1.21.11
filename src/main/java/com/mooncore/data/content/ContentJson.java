package com.mooncore.data.content;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pont générique <b>YAML&nbsp;↔&nbsp;JSON</b> (Étape A2 du master brain). Convertit n'importe
 * quelle {@link ConfigurationSection} (la forme de sérialisation déjà utilisée par
 * {@code CustomItemDef}, {@code CustomBlockDef} et les fichiers boss) en JSON et inversement.
 * <p>
 * Le round-trip d'une définition réutilise donc le code de (dé)sérialisation YAML existant,
 * sans le dupliquer :
 * <pre>{@code
 *   // item -> json
 *   MemoryConfiguration cfg = new MemoryConfiguration();
 *   def.save(cfg);
 *   String json = ContentJson.toJson(cfg);
 *   // json -> item
 *   CustomItemDef back = CustomItemDef.load(id, ContentJson.toSection(json));
 * }</pre>
 * Volontairement <b>sans aucune dépendance vers les classes de modules</b> : la couche data
 * reste en dessous des modules métier.
 */
public final class ContentJson {

    private ContentJson() {}

    private static final Gson GSON = UniversalContentStore.GSON;
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();

    /** Sérialise une section de configuration (et ses sous-sections) en JSON. */
    public static String toJson(ConfigurationSection section) {
        return GSON.toJson(sectionToMap(section));
    }

    /**
     * Reconstruit une {@link ConfigurationSection} autonome depuis un JSON produit par
     * {@link #toJson(ConfigurationSection)}. Le résultat est consommable par les méthodes
     * {@code load(id, section)} existantes.
     */
    public static ConfigurationSection toSection(String json) {
        MemoryConfiguration cfg = new MemoryConfiguration();
        Map<String, Object> map = GSON.fromJson(json, MAP_TYPE);
        if (map != null) applyMap(cfg, map);
        return cfg;
    }

    // ---- Section -> Map (JSON-compatible) ----

    private static Map<String, Object> sectionToMap(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, normalize(section.get(key)));
        }
        return out;
    }

    private static Object normalize(Object v) {
        if (v instanceof ConfigurationSection cs) {
            return sectionToMap(cs);
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), normalize(e.getValue()));
            }
            return out;
        }
        if (v instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) out.add(normalize(o));
            return out;
        }
        return v; // String / Number / Boolean / null
    }

    // ---- Map (depuis JSON) -> Section ----

    @SuppressWarnings("unchecked")
    private static void applyMap(ConfigurationSection section, Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> child) {
                applyMap(section.createSection(e.getKey()), (Map<String, Object>) child);
            } else {
                section.set(e.getKey(), v);
            }
        }
    }
}
