package com.mooncore.modules.ai;

import com.mooncore.modules.mechanic.ActionType;
import com.mooncore.modules.mechanic.MechanicDef;
import com.mooncore.modules.mechanic.TriggerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation d'une sortie IA en {@link MechanicDef} : déclencheur/actions normalisés, et surtout les actions
 * de type inconnu (halluciné) sont <b>ignorées</b> plutôt qu'ajoutées en poids mort. Headless (Gson + records
 * purs ; le validateur s'instancie sans serveur car ses caps de stats sont une simple table string→double).
 */
class AiValidateMechanicTest {

    private static AiActionValidator validator() {
        return new AiActionValidator(null, 100.0, 5, 3, null);
    }

    @Test
    void unknownActionTypesAreDropped() {
        String json = """
                { "trigger": "USE_ITEM", "match": "custom:wand",
                  "actions": [
                    { "type": "message", "params": { "text": "Salut %player%" } },
                    { "type": "teleportation_quantique", "params": { "x": "0" } },
                    { "type": "heal", "params": { "amount": "5" } }
                  ] }
                """;
        MechanicDef d = validator().validateMechanic(json, "spell");
        assertEquals("spell", d.id());
        assertEquals(TriggerType.USE_ITEM, d.trigger());
        assertEquals(2, d.actions().size());   // l'action inconnue n'est pas ajoutée
        assertEquals(ActionType.MESSAGE, d.actions().get(0).type());
        assertEquals(ActionType.HEAL, d.actions().get(1).type());
        assertEquals("Salut %player%", d.actions().get(0).param("text", ""));  // params préservés tels quels
    }

    @Test
    void allUnknownActionsYieldEmptyButValidDef() {
        String json = """
                { "trigger": "SNEAK", "actions": [ { "type": "inconnu1" }, { "type": "inconnu2" } ] }
                """;
        MechanicDef d = validator().validateMechanic(json, "noop");
        assertTrue(d.actions().isEmpty());
        assertEquals(TriggerType.SNEAK, d.trigger());
    }

    @Test
    void invalidJsonReturnsNull() {
        assertNull(validator().validateMechanic("pas du json", "x"));
    }
}
