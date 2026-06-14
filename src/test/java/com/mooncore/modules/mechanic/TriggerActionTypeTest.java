package com.mooncore.modules.mechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(TriggerType.DEATH, TriggerType.fromText("death"));
        assertEquals(TriggerType.DEATH, TriggerType.fromText("mort"));
        assertTrue(TriggerType.DEATH.usesMatchKey());      // matchKey = cause de mort
        assertEquals(TriggerType.RESPAWN, TriggerType.fromText("respawn"));
        assertEquals(TriggerType.CONSUME_ITEM, TriggerType.fromText("eat"));
        assertEquals(TriggerType.CONSUME_ITEM, TriggerType.fromText("consomme"));
        assertEquals(TriggerType.FISH, TriggerType.fromText("peche"));
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
        assertEquals(ActionType.TAKE_MONEY, ActionType.fromText("charge"));
        assertEquals(ActionType.TAKE_MONEY, ActionType.fromText("cost"));
        assertEquals(ActionType.POTION, ActionType.fromText("effet"));
        assertEquals(ActionType.TELEPORT, ActionType.fromText("tp"));
        assertEquals(ActionType.LIGHTNING, ActionType.fromText("foudre"));
        assertEquals(ActionType.SPAWN_MOB, ActionType.fromText("summon"));
        assertEquals(ActionType.TITLE, ActionType.fromText("titre"));
        assertEquals(ActionType.CLEAR_EFFECTS, ActionType.fromText("clear-effects"));
        assertEquals(ActionType.FEED, ActionType.fromText("nourris"));
        assertEquals(ActionType.LOOT, ActionType.fromText("butin"));
        assertEquals(ActionType.LOOT, ActionType.fromText("loot_table"));
        assertEquals(ActionType.LAUNCH, ActionType.fromText("propulse"));
        assertEquals(ActionType.PARTICLE, ActionType.fromText("particule"));
        assertEquals(ActionType.BROADCAST, ActionType.fromText("annonce"));
        assertEquals(ActionType.BROADCAST, ActionType.fromText("bc"));
        assertEquals(ActionType.PLAYER_COMMAND, ActionType.fromText("run_as_player"));
        assertEquals(ActionType.PLAYER_COMMAND, ActionType.fromText("commande_joueur"));
        assertEquals(ActionType.ACTIONBAR, ActionType.fromText("actionbar"));
        assertEquals(ActionType.ACTIONBAR, ActionType.fromText("action-bar"));
        assertEquals(ActionType.ACTIONBAR, ActionType.fromText("barre_action"));
        assertEquals(ActionType.NONE, ActionType.fromText("xyz"));
        assertEquals(ActionType.NONE, ActionType.fromText(null));
    }

    @Test
    void triggerMatchKeyUsage() {
        assertTrue(TriggerType.INTERACT_BLOCK.usesMatchKey());
        assertTrue(TriggerType.KILL_ENTITY.usesMatchKey());
        assertTrue(TriggerType.PLACE_BLOCK.usesMatchKey());
        assertTrue(TriggerType.DAMAGE_TAKEN.usesMatchKey());
        assertTrue(TriggerType.CONSUME_ITEM.usesMatchKey());
        assertFalse(TriggerType.FISH.usesMatchKey());
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

    @Test
    void missingRequiredParamDetectsNoOpActions() {
        // Params manquants → nom du param requis.
        assertEquals("text", new MechanicAction(ActionType.MESSAGE, java.util.Map.of()).missingRequiredParam());
        assertEquals("table", new MechanicAction(ActionType.LOOT, java.util.Map.of()).missingRequiredParam());
        assertEquals("item", new MechanicAction(ActionType.GIVE_ITEM, java.util.Map.of()).missingRequiredParam());
        assertEquals("amount", new MechanicAction(ActionType.MONEY, java.util.Map.of()).missingRequiredParam());
        assertEquals("command", new MechanicAction(ActionType.PLAYER_COMMAND, java.util.Map.of()).missingRequiredParam());
        // Params présents → null (OK).
        assertNull(new MechanicAction(ActionType.MESSAGE, java.util.Map.of("text", "salut")).missingRequiredParam());
        assertNull(new MechanicAction(ActionType.LOOT, java.util.Map.of("table", "t")).missingRequiredParam());
        // TITLE accepte title OU subtitle.
        assertNull(new MechanicAction(ActionType.TITLE, java.util.Map.of("subtitle", "x")).missingRequiredParam());
        // TELEPORT : target=spawn OU x suffisent.
        assertNull(new MechanicAction(ActionType.TELEPORT, java.util.Map.of("target", "spawn")).missingRequiredParam());
        assertEquals("x|target", new MechanicAction(ActionType.TELEPORT, java.util.Map.of()).missingRequiredParam());
        // Types sans param obligatoire → toujours null.
        assertNull(new MechanicAction(ActionType.CLEAR_EFFECTS, java.util.Map.of()).missingRequiredParam());
        assertNull(new MechanicAction(ActionType.LIGHTNING, java.util.Map.of()).missingRequiredParam());
    }

    @Test
    void setParamEditsInPlaceAndRemovesOnNull() {
        MechanicAction a = new MechanicAction(ActionType.MESSAGE, java.util.Map.of("text", "vieux"));
        a.setParam("Text", "nouveau");                 // clé normalisée → écrase la valeur existante
        assertEquals("nouveau", a.param("text", "?"));
        a.setParam("color", "red");                    // nouvelle clé
        assertEquals("red", a.param("color", "?"));
        a.setParam("text", null);                      // valeur nulle → retire la clé
        assertEquals("def", a.param("text", "def"));
        a.setParam(null, "x");                         // clé nulle → no-op (pas d'exception)
        assertEquals(1, a.params().size());            // seul 'color' subsiste
    }
}
