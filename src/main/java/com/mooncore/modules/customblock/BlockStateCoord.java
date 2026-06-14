package com.mooncore.modules.customblock;

/**
 * Cœur arithmétique <b>pur</b> (sans dépendance Bukkit) de l'attribution d'état des blocs custom :
 * la bijection {@code index ↔ (instrument, note, powered)}. Isolé de {@link BlockStateMap} (qui, lui,
 * référence l'enum {@code Instrument} et charge donc le Registry serveur) afin d'être testable headless.
 * <p>
 * Constantes du protocole note_block : 16 instruments « classiques » × 25 notes × 2 états powered.
 */
record BlockStateCoord(int instrumentIndex, int note, boolean powered) {

    static final int INSTRUMENT_COUNT = 16;
    static final int NOTES = 25;

    /** Nombre total d'états distincts (800). */
    static int capacity() { return INSTRUMENT_COUNT * NOTES * 2; }

    /** Décompose un index en coordonnées ; tout index est ramené dans {@code [0, capacity())}. */
    static BlockStateCoord fromIndex(int index) {
        int cap = capacity();
        int i = ((index % cap) + cap) % cap;
        int instr = i / (NOTES * 2);
        int rem = i % (NOTES * 2);
        return new BlockStateCoord(instr, rem / 2, (rem % 2) == 1);
    }

    /** Recompose l'index canonique (inverse de {@link #fromIndex}). */
    int toIndex() {
        return instrumentIndex * (NOTES * 2) + note * 2 + (powered ? 1 : 0);
    }
}
