package com.mooncore.modules.mechanic;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Une action atomique d'une {@link MechanicDef} : un {@link ActionType} + un sac de paramètres nommés
 * (chaînes), interprétés à l'exécution par l'action concernée. Pur (aucune dépendance serveur), avec des
 * accesseurs typés tolérants ({@link #intParam}/{@link #doubleParam}) pour la lecture des paramètres.
 */
public final class MechanicAction {

    private final ActionType type;
    private final Map<String, String> params;

    public MechanicAction(ActionType type, Map<String, String> params) {
        this.type = type == null ? ActionType.NONE : type;
        this.params = new LinkedHashMap<>();
        if (params != null) {
            params.forEach((k, v) -> {
                if (k != null && v != null) this.params.put(k.toLowerCase(Locale.ROOT), v);
            });
        }
    }

    public ActionType type() { return type; }
    public Map<String, String> params() { return params; }

    /**
     * Définit (ou écrase) un paramètre — clé normalisée en minuscule comme à la construction. Une {@code value}
     * nulle <b>retire</b> la clé. Permet d'éditer une action en place sans la retirer/recréer. Pur/testable.
     */
    public void setParam(String key, String value) {
        if (key == null) return;
        String k = key.toLowerCase(Locale.ROOT);
        if (value == null) params.remove(k);
        else params.put(k, value);
    }

    public String param(String key, String def) {
        String v = params.get(key == null ? null : key.toLowerCase(Locale.ROOT));
        return v == null ? def : v;
    }

    public int intParam(String key, int def) {
        try { return Integer.parseInt(param(key, "").trim()); }
        catch (NumberFormatException e) { return def; }
    }

    public double doubleParam(String key, double def) {
        try { return Double.parseDouble(param(key, "").trim()); }
        catch (NumberFormatException e) { return def; }
    }

    /** True si l'action est exploitable (type reconnu). */
    public boolean isValid() { return type != ActionType.NONE; }
}
