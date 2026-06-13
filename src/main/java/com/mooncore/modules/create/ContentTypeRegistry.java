package com.mooncore.modules.create;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registre des {@link ContentTypeHandler} par type de contenu (Étape E4). Source unique de vérité de
 * la commande de création unifiée ({@code /moon create|edit|list|info|delete|clone|give <type> …}) :
 * chaque type métier s'y enregistre au démarrage de son module.
 */
public final class ContentTypeRegistry {

    private final Map<String, ContentTypeHandler> handlers = new LinkedHashMap<>();

    /** Enregistre (ou remplace) le handler d'un type. */
    public void register(ContentTypeHandler handler) {
        if (handler != null && handler.type() != null) {
            handlers.put(handler.type().toLowerCase(Locale.ROOT), handler);
        }
    }

    public ContentTypeHandler get(String type) {
        return type == null ? null : handlers.get(type.toLowerCase(Locale.ROOT));
    }

    public boolean has(String type) {
        return type != null && handlers.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /** Types enregistrés (ordre d'enregistrement). */
    public Set<String> types() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    public int size() {
        return handlers.size();
    }
}
