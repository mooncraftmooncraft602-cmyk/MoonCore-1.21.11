package com.mooncore.modules.ai;

import com.mooncore.modules.loot.LootEntry;
import com.mooncore.modules.loot.LootPool;
import com.mooncore.modules.loot.LootTableDef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation d'une sortie IA en {@link LootTableDef} : les entrées qui ne résolvent vers rien (Material
 * non reconnu, ni {@code custom:}, ni {@code loot-table}) sont ignorées plutôt qu'ajoutées en poids mort
 * (sinon elles fausseraient les taux de drop). Headless (Gson + records purs).
 *
 * <p>Note : {@code Material.matchMaterial} fonctionne sans serveur, mais {@code Material.isItem()} charge le
 * Registry → indisponible en headless. On évite donc d'asserter sur des entrées Material vanilla ; on teste
 * le rejet des entrées vides et la conservation des entrées {@code custom:}/{@code loot-table}.</p>
 */
class AiValidateLootTest {

    private static AiActionValidator validator() {
        return new AiActionValidator(null, 100.0, 5, 3, null);
    }

    @Test
    void emptyEntriesDroppedCustomAndRefKept() {
        String json = """
                { "pools": [ { "rolls": { "min": 1, "max": 1 }, "entries": [
                    { "item": "ce_materiau_nexiste_pas", "weight": 10 },
                    { "item": "custom:ruby", "weight": 5 },
                    { "loot-table": "tresor_rare", "weight": 3 },
                    { "weight": 7 }
                  ] } ] }
                """;
        LootTableDef d = validator().validateLoot(json, "butin");
        assertEquals("butin", d.id());
        assertEquals(1, d.pools().size());
        LootPool pool = d.pools().get(0);
        // 2 entrées valides conservées (custom + table) ; les 2 vides (material inconnu, entrée nue) ignorées.
        assertEquals(2, pool.entries().size());
        LootEntry custom = pool.entries().get(0);
        assertEquals("ruby", custom.itemId());
        LootEntry ref = pool.entries().get(1);
        assertEquals("tresor_rare", ref.tableRef());
    }

    @Test
    void selfReferenceStrippedThenEntryDroppedIfOtherwiseEmpty() {
        String json = """
                { "pools": [ { "entries": [ { "loot-table": "Butin", "weight": 2 } ] } ] }
                """;
        // La référence pointe sur la table elle-même → annulée → entrée vide → ignorée → pool vide → non ajouté.
        LootTableDef d = validator().validateLoot(json, "butin");
        assertTrue(d.pools().isEmpty());
    }

    @Test
    void poolWithOnlyInvalidEntriesIsDropped() {
        String json = """
                { "pools": [
                    { "entries": [ { "item": "materiau_bidon" }, { "item": "encore_faux" } ] },
                    { "entries": [ { "item": "custom:gem" } ] }
                  ] }
                """;
        LootTableDef d = validator().validateLoot(json, "t");
        // Le 1er pool (toutes entrées invalides) est filtré ; seul le 2e (custom valide) subsiste.
        assertEquals(1, d.pools().size());
        assertEquals("gem", d.pools().get(0).entries().get(0).itemId());
    }

    @Test
    void invalidJsonReturnsNull() {
        assertNull(validator().validateLoot("xxx", "x"));
    }
}
