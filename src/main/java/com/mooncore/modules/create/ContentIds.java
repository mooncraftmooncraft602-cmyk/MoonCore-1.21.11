package com.mooncore.modules.create;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation/normalisation d'id de contenu (slug, anti path-traversal). */
public final class ContentIds {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");

    private ContentIds() {}

    public static String norm(String id) {
        return id == null ? null : id.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean valid(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
