package com.mooncore.modules.customitem.forge;

import java.util.Locale;
import java.util.Random;

/**
 * Compose un <b>programme DSL</b> ({@link TextureSynth#renderProgram}) à partir d'un nom d'item — c'est-à-dire
 * la suite de « mots » qui décrit la texture. Sert deux rôles :
 * <ul>
 *   <li><b>forge live</b> : chaque item forgé obtient une variante unique (graine = le nom) au lieu d'un
 *       gabarit figé — la silhouette de base reste celle, éprouvée, des programmes standard ;</li>
 *   <li><b>générateur de corpus</b> : produit des milliers de paires {@code (nom → programme)} pour
 *       entraîner une IA à <b>écrire ce langage</b> (l'IA imite ce compositeur, puis généralise).</li>
 * </ul>
 * Le type d'objet vient du nom ({@link TextureSynth#itemKind}); des <b>modificateurs</b> (grand/petit/royal…)
 * et une légère variation seedée font varier longueur/largeur, taille du joyau, dorures et rivets. La couleur
 * reste portée séparément par la {@link ThemePalette} (le programme est indépendant du thème).
 */
public final class TextureComposer {

    private TextureComposer() {}

    private static final String[] BIG = {"grand", "grande", "large", "lourd", "lourde", "colossal", "massif",
            "massive", "geant", "titan", "brute", "enorme", "gigantesque"};
    private static final String[] SMALL = {"petit", "petite", "dague", "poignard", "court", "courte", "fin",
            "fine", "leger", "mini", "eclat"};
    private static final String[] ORNATE = {"royal", "royale", "runique", "sacre", "sacree", "divin", "divine",
            "legendaire", "ancien", "ancienne", "mythique", "ender", "dragon", "arcane", "enchante", "celeste",
            "relique", "imperial", "imperiale"};

    /** Spécification compacte d'un item : type + 3 styles. C'est le « gène » que l'IA apprend à émettre. */
    public record Spec(TextureSynth.Kind kind, boolean big, boolean small, boolean ornate) {}

    /** Déduit la spec d'un nom (type + modificateurs). GENERIC -> épée par défaut. */
    public static Spec specOf(String name) {
        TextureSynth.Kind k = TextureSynth.itemKind(name);
        return new Spec(k == TextureSynth.Kind.GENERIC ? TextureSynth.Kind.SWORD : k,
                has(name, BIG), has(name, SMALL), has(name, ORNATE));
    }

    /**
     * Le <b>gène compact</b> {@code "KIND big small ornate"} — la cible COURTE que l'IA génère depuis le nom.
     * Court = le type d'objet domine le signal d'apprentissage, donc l'IA l'apprend vite et juste (au lieu de
     * se noyer dans ~190 caractères de programme).
     */
    public static String tagOf(String name) {
        Spec s = specOf(name);
        return s.kind() + " " + (s.big() ? 1 : 0) + " " + (s.small() ? 1 : 0) + " " + (s.ornate() ? 1 : 0);
    }

    /** Programme DSL complet pour {@code name}, variation déterministe seedée par le nom. */
    public static String compose(String name) {
        return compose(name, name == null ? 0L : name.toLowerCase(Locale.ROOT).hashCode());
    }

    /** Programme DSL complet pour {@code name} avec graine de variation explicite. */
    public static String compose(String name, long seed) {
        return build(specOf(name), new Random(seed));
    }

    /** IA → DSL : parse un gène {@code "KIND b s o"} et construit le programme complet (le serveur dessine). */
    public static String fromTag(String tag, long seed) {
        String[] t = tag == null ? new String[0] : tag.trim().split("[\\s,]+");
        TextureSynth.Kind k = TextureSynth.Kind.SWORD;
        if (t.length > 0) try { k = TextureSynth.Kind.valueOf(t[0].toUpperCase(Locale.ROOT)); } catch (Exception ignored) { }
        if (k == TextureSynth.Kind.GENERIC) k = TextureSynth.Kind.SWORD;
        boolean big = t.length > 1 && t[1].startsWith("1");
        boolean small = t.length > 2 && t[2].startsWith("1");
        boolean ornate = t.length > 3 && t[3].startsWith("1");
        return build(new Spec(k, big, small, ornate), new Random(seed));
    }

    private static String build(Spec s, Random r) {
        return switch (s.kind()) {
            case SWORD, GENERIC -> sword(r, s.big(), s.small(), s.ornate());
            case PICKAXE -> pickaxe(r, s.big(), s.small(), s.ornate());
            case AXE -> axe(r, s.big(), s.ornate());
            case HELMET -> helmet(r, s.ornate());
            case CHESTPLATE -> chestplate(r, s.ornate());
        };
    }

    // ---- gabarits paramétriques (baseline ≈ programmes standard, variation serrée → toujours propres) ----

    private static String sword(Random r, boolean big, boolean small, boolean ornate) {
        double tipx = 13.7 + j(r, 0.2) + (big ? 0.3 : 0) + (small ? -1.6 : 0);
        double tipy = 1.6 + (small ? 1.8 : 0) - (big ? 0.3 : 0);
        double w = (small ? 1.4 : big ? 2.0 : 1.7) + j(r, 0.08);
        double jr = (ornate ? 1.8 : 1.5) + j(r, 0.08);
        StringBuilder b = new StringBuilder();
        b.append("WCAP 4.2 11.0 2.6 13.8 1.0 0 GCAP 3.5 11.4 4.3 12.6 0.9 0 GDISC 2.2 13.8 1.5 ");
        b.append("GCAP 2.7 7.7 7.3 12.3 1.1 0 ");
        b.append("MCAP 5.0 10.2 ").append(f(tipx)).append(' ').append(f(tipy)).append(' ').append(f(w)).append(" 1 ");
        b.append("FULLER 6.0 9.4 12.8 2.6 JEWEL 5.0 10.0 ").append(f(jr)).append(' ');
        b.append("GLINT 12 3 GLINT 10 5");
        if (ornate) b.append(" RIVET 4 11");
        return b.toString();
    }

    private static String pickaxe(Random r, boolean big, boolean small, boolean ornate) {
        double w = (small ? 1.5 : big ? 2.0 : 1.7) + j(r, 0.08);
        double reach = (big ? 0.4 : small ? -0.6 : 0) + j(r, 0.2);
        double jr = (ornate ? 1.8 : 1.6) + j(r, 0.08);
        StringBuilder b = new StringBuilder();
        b.append("WCAP 10.8 14.4 8.2 6.4 1.0 0 GCAP 8.9 7.4 9.7 8.8 0.9 0 ");
        b.append("MCAP 8.0 4.6 ").append(f(2.2 - reach)).append(" 6.6 ").append(f(w)).append(" 1 ");
        b.append("MCAP 8.0 4.6 ").append(f(13.8 + reach)).append(" 3.0 ").append(f(w)).append(" 1 ");
        b.append("GDISC 8.0 5.6 1.8 JEWEL 8.0 5.4 ").append(f(jr)).append(' ');
        b.append("GLINT 4 5 GLINT 12 4");
        if (ornate) b.append(" RIVET 6 8");
        return b.toString();
    }

    private static String axe(Random r, boolean big, boolean ornate) {
        double rad = (big ? 3.5 : 3.1) + j(r, 0.12);
        double jr = (ornate ? 1.8 : 1.6) + j(r, 0.08);
        StringBuilder b = new StringBuilder();
        b.append("WCAP 12.6 14.6 5.6 3.0 1.0 0 ");
        b.append("MDISC 4.7 5.9 ").append(f(rad)).append(' ');
        b.append("MCAP 6.4 2.6 2.4 5.0 1.7 0 MCAP 2.4 5.0 5.0 9.6 1.9 0 ");
        b.append("GCAP 6.6 3.2 6.0 8.6 0.7 0 GDISC 6.6 5.4 1.4 ");
        b.append("JEWEL 4.6 5.8 ").append(f(jr)).append(" RIVET 6 7 GLINT 2 6 GLINT 3 4");
        return b.toString();
    }

    private static String helmet(Random r, boolean ornate) {
        double rx = 6.2 + j(r, 0.18), ry = 6.0 + j(r, 0.18);
        double jr = (ornate ? 1.7 : 1.5) + j(r, 0.06);
        StringBuilder b = new StringBuilder();
        b.append("MELL 8 7.5 ").append(f(rx)).append(' ').append(f(ry)).append(' ');
        b.append("CLEAR 4 7 11 8 GTR 2 11 13 13 GTR 4 6 11 6 GTR 4 9 11 9 ");
        b.append("JEWEL 8.0 4.6 ").append(f(jr)).append(" RIVET 4 5 RIVET 11 5 GLINT 5 3 GLINT 11 4");
        if (ornate) b.append(" RIVET 7 12");
        return b.toString();
    }

    private static String chestplate(Random r, boolean ornate) {
        double jr = (ornate ? 1.9 : 1.7) + j(r, 0.08);
        StringBuilder b = new StringBuilder();
        b.append("MRECT 3 3 12 14 CLEAR 6 3 9 4 MRECT 2 2 4 5 MRECT 11 2 13 5 ");
        b.append("GTR 2 5 5 5 GTR 10 5 13 5 GTR 5 5 5 6 GTR 10 5 10 6 ");
        b.append("JEWEL 7.5 8.8 ").append(f(jr)).append(" RIVET 4 11 RIVET 11 11 GLINT 4 4 GLINT 11 4");
        if (ornate) b.append(" RIVET 7 6");
        return b.toString();
    }

    // ---- utilitaires ----

    /** Vrai si l'un des mots-clés apparaît comme mot entier dans le nom (insensible casse/accents partiels). */
    private static boolean has(String name, String... kw) {
        if (name == null) return false;
        String norm = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String s = " " + norm.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
        for (String k : kw) if (s.contains(" " + k + " ")) return true;
        return false;
    }

    private static double j(Random r, double amt) { return (r.nextDouble() * 2 - 1) * amt; }
    private static String f(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
