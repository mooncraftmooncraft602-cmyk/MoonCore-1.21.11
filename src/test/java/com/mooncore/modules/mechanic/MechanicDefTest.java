package com.mooncore.modules.mechanic;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip YAML de {@link MechanicDef} (trigger/match/cooldown/actions ordonnées) et règle
 * {@code isRunnable}. Pur, sans serveur ({@link MemoryConfiguration}).
 */
class MechanicDefTest {

    private static MechanicDef sample() {
        MechanicDef d = new MechanicDef("magic_wand");
        d.setDisplayName("<gold>Baguette</gold>");
        d.setTrigger(TriggerType.USE_ITEM);
        d.setMatchKey("custom:magic_wand");
        d.setCooldownTicks(40);
        d.setIntervalTicks(200);
        Map<String, String> m1 = new LinkedHashMap<>();
        m1.put("text", "<aqua>Zap!</aqua>");
        d.addAction(new MechanicAction(ActionType.MESSAGE, m1));
        Map<String, String> m2 = new LinkedHashMap<>();
        m2.put("effect", "SPEED");
        m2.put("duration", "100");
        m2.put("amplifier", "1");
        d.addAction(new MechanicAction(ActionType.POTION, m2));
        return d;
    }

    @Test
    void roundTripPreservesTriggerActionsAndOrder() {
        MemoryConfiguration cfg = new MemoryConfiguration();
        sample().save(cfg.createSection("m"));

        MechanicDef back = MechanicDef.load("magic_wand", cfg.getConfigurationSection("m"));
        assertEquals("<gold>Baguette</gold>", back.displayName());
        assertEquals(TriggerType.USE_ITEM, back.trigger());
        assertEquals("custom:magic_wand", back.matchKey());
        assertEquals(40, back.cooldownTicks());
        assertEquals(200, back.intervalTicks());
        assertTrue(back.enabled());

        assertEquals(2, back.actions().size());
        assertEquals(ActionType.MESSAGE, back.actions().get(0).type());     // ordre préservé
        assertEquals("<aqua>Zap!</aqua>", back.actions().get(0).param("text", ""));
        assertEquals(ActionType.POTION, back.actions().get(1).type());
        assertEquals(100, back.actions().get(1).intParam("duration", 0));
        assertTrue(back.isRunnable());
    }

    @Test
    void isRunnableRequiresEnabledTriggerAndValidAction() {
        MechanicDef d = new MechanicDef("x");
        assertFalse(d.isRunnable());                       // NONE trigger, pas d'action
        d.setTrigger(TriggerType.PLAYER_JOIN);
        assertFalse(d.isRunnable());                       // pas d'action valide
        d.addAction(new MechanicAction(ActionType.NONE, null));
        assertFalse(d.isRunnable());                       // action invalide
        d.addAction(new MechanicAction(ActionType.MESSAGE, Map.of("text", "salut")));
        assertTrue(d.isRunnable());
        d.setEnabled(false);
        assertFalse(d.isRunnable());                       // désactivée
    }

    @Test
    void clampsCooldownAndInterval() {
        MechanicDef d = new MechanicDef("x");
        d.setCooldownTicks(-5);
        assertEquals(0, d.cooldownTicks());
        d.setIntervalTicks(0);
        assertEquals(1, d.intervalTicks());                // plancher 1
    }
}
