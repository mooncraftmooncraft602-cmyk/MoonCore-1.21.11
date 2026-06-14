package com.mooncore.modules.customblock;

import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.data.type.NoteBlock;

import java.util.List;

/**
 * Associe un identifiant numérique stable ↔ un état de bloc {@code note_block}
 * (instrument × note × powered). C'est la technique standard des blocs custom sans
 * framework : chaque bloc custom occupe une combinaison d'état unique, et le resource
 * pack mappe cette combinaison à un modèle/texture custom.
 * <p>
 * On utilise les 16 instruments « classiques » × 25 notes × 2 états powered = 800
 * blocs custom possibles. Les états vanilla restants sont rendus normalement par le pack.
 */
public final class BlockStateMap {

    public record State(Instrument instrument, String instrumentName, int note, boolean powered) {}

    /** Instrument Bukkit ↔ nom de blockstate Minecraft (16 classiques). */
    private static final Object[][] INSTRUMENTS = {
            {Instrument.PIANO, "harp"}, {Instrument.BASS_DRUM, "basedrum"},
            {Instrument.SNARE_DRUM, "snare"}, {Instrument.STICKS, "hat"},
            {Instrument.BASS_GUITAR, "bass"}, {Instrument.FLUTE, "flute"},
            {Instrument.BELL, "bell"}, {Instrument.GUITAR, "guitar"},
            {Instrument.CHIME, "chime"}, {Instrument.XYLOPHONE, "xylophone"},
            {Instrument.IRON_XYLOPHONE, "iron_xylophone"}, {Instrument.COW_BELL, "cow_bell"},
            {Instrument.DIDGERIDOO, "didgeridoo"}, {Instrument.BIT, "bit"},
            {Instrument.BANJO, "banjo"}, {Instrument.PLING, "pling"}
    };

    /** Tous les noms d'instruments existants (pour couvrir 100% du blockstate dans le pack). */
    private static final List<String> ALL_INSTRUMENT_NAMES = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime",
            "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling",
            "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin", "custom_head");

    static {
        // Garde-fou : le cœur pur (BlockStateCoord) suppose 16 instruments classiques. S'il diverge
        // du tableau ci-dessus, l'attribution d'état serait fausse → on échoue tôt (fail-fast).
        if (INSTRUMENTS.length != BlockStateCoord.INSTRUMENT_COUNT) {
            throw new IllegalStateException("BlockStateMap/BlockStateCoord désynchronisés : "
                    + INSTRUMENTS.length + " != " + BlockStateCoord.INSTRUMENT_COUNT);
        }
    }

    private BlockStateMap() {}

    public static int capacity() { return BlockStateCoord.capacity(); } // 800

    public static List<String> allInstrumentNames() { return ALL_INSTRUMENT_NAMES; }

    public static State forIndex(int index) {
        BlockStateCoord c = BlockStateCoord.fromIndex(index);
        Object[] entry = INSTRUMENTS[c.instrumentIndex()];
        return new State((Instrument) entry[0], (String) entry[1], c.note(), c.powered());
    }

    /** Applique l'état d'index donné à une donnée de note block. */
    public static void apply(NoteBlock data, int index) {
        State s = forIndex(index);
        data.setInstrument(s.instrument());
        data.setNote(new Note(s.note()));
        data.setPowered(s.powered());
    }

    /** Index correspondant à une donnée de note block, ou -1 si hors de nos instruments. */
    public static int indexOf(NoteBlock data) {
        Instrument instr = data.getInstrument();
        int instrIdx = -1;
        for (int k = 0; k < INSTRUMENTS.length; k++) {
            if (INSTRUMENTS[k][0] == instr) { instrIdx = k; break; }
        }
        if (instrIdx < 0) return -1;
        return new BlockStateCoord(instrIdx, data.getNote().getId(), data.isPowered()).toIndex();
    }
}
