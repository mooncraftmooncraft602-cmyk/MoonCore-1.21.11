package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Robustesse du parsing tolérant de {@link TriggerType} et {@link ActionType} (casse, séparateurs,
 * alias FR/EN). Pur, sans serveur. Les enums sont une source classique de bugs de classement d'alias.
 */
class TriggerActionTypeTest {

    @Test
    void triggerFromTextHandlesAliasesAndSeparators() {
        assertEquals(TriggerType.INTERACT_BLOCK, TriggerType.fromText("INTERACT-BLOCK"));
        assertEquals(TriggerType.INTERACT_BLOCK, TriggerType.fromText("clic bloc"));
        assertEquals(TriggerType.BREAK_BLOCK, TriggerType.fromText("Break"));
        assertEquals(TriggerType.USE_ITEM, TriggerType.fromText("rightclick_item"));
        assertEquals(TriggerType.KILL_ENTITY, TriggerType.fromText("tue"));
        assertEquals(TriggerType.PLACE_BLOCK, TriggerType.fromText("pose"));
        assertEquals(TriggerType.DAMAGE_TAKEN, TriggerType.fromText("degats"));
        assertEquals(TriggerType.SNEAK, TriggerType.fromText("accroupi"));
        assertEquals(TriggerType.RESPAWN, TriggerType.fromText("respawn"));
        assertEquals(TriggerType.PLAYER_JOIN, TriggerType.fromText("connexion"));
        assertEquals(TriggerType.INTERVAL, TriggerType.fromText("timer"));
        assertEquals(TriggerType.NONE, TriggerType.fromText("n'importe quoi"));
        assertEquals(TriggerType.NONE, TriggerType.fromText(null));
    }

    @Test
    void actionFromTextHandlesAliasesAndSeparators() {
        assertEquals(ActionType.MESSAGE, ActionType.fromText("MSG"));
        assertEquals(ActionType.COMMAND, ActionType.fromText("commande"));
        assertEquals(ActionType.GIVE_ITEM, ActionType.fromText("give-item"));
        assertEquals(ActionType.GIVE_ITEM, ActionType.fromText("donne item"));
        assertEquals(ActionType.MONEY, ActionType.fromText("argent"));
        assertEquals(ActionType.POTION, ActionType.fromText("effet"));
        assertEquals(ActionType.TELEPORT, ActionType.fromText("tp"));
        assertEquals(ActionType.LIGHTNING, ActionType.fromText("foudre"));
        assertEquals(ActionType.SPAWN_MOB, ActionType.fromText("summon"));
        assertEquals(ActionType.TITLE, ActionType.fromText("titre"));
        assertEquals(ActionType.CLEAR_EFFECTS, ActionType.fromText("clear-effects"));
        assertEquals(ActionType.FEED, ActionType.fromText("nourris"));
        assertEquals(ActionType.LOOT, ActionType.fromText("butin"));
        assertEquals(ActionType.LOOT, ActionType.fromText("loot_table"));
        assertEquals(ActionType.NONE, ActionType.fromText("xyz"));
        assertEquals(ActionType.NONE, ActionType.fromText(null));
    }

    @Test
    void triggerMatchKeyUsage() {
        assertTrue(TriggerType.INTERACT_BLOCK.usesMatchKey());
        assertTrue(TriggerType.KILL_ENTITY.usesMatchKey());
        assertTrue(TriggerType.PLACE_BLOCK.usesMatchKey());
        assertTrue(TriggerType.DAMAGE_TAKEN.usesMatchKey());
        assertFalse(TriggerType.SNEAK.usesMatchKey());
        assertFalse(TriggerType.RESPAWN.usesMatchKey());
        assertFalse(TriggerType.PLAYER_JOIN.usesMatchKey());
        assertFalse(TriggerType.INTERVAL.usesMatchKey());
    }

    @Test
    void actionParamAccessorsAreTolerant() {
        MechanicAction a = new MechanicAction(ActionType.GIVE_ITEM,
                java.util.Map.of("Item", "DIAMOND", "amount", "3", "bad", "xx"));
        assertEquals("DIAMOND", a.param("item", "?"));      // clé normalisée en minuscule
        assertEquals(3, a.intParam("amount", 0));
        assertEquals(9, a.intParam("bad", 9));              // non-nombre → défaut
        assertEquals(1.0, a.doubleParam("absent", 1.0), 1e-9);
        assertTrue(a.isValid());
        assertFalse(new MechanicAction(ActionType.NONE, null).isValid());
    }
}
