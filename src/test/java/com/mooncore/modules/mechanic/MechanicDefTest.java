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
    void lootTablesUsedCollectsFromLootActions() {
        MechanicDef d = new MechanicDef("rewarder");
        d.addAction(new MechanicAction(ActionType.MESSAGE, Map.of("text", "hi")));   // pas une action loot
        d.addAction(new MechanicAction(ActionType.LOOT, Map.of("table", "Rare_Drops")));
        d.addAction(new MechanicAction(ActionType.LOOT, Map.of("table", "rare_drops"))); // doublon (normalisé)
        d.addAction(new MechanicAction(ActionType.LOOT, Map.of()));                   // loot sans table → ignoré
        assertEquals(java.util.Set.of("rare_drops"), d.lootTablesUsed());
        assertTrue(new MechanicDef("x").lootTablesUsed().isEmpty());
    }

    @Test
    void danglingLootTablesUsesInjectedPredicate() {
        MechanicDef d = new MechanicDef("rewarder");
        d.addAction(new MechanicAction(ActionType.LOOT, Map.of("table", "common")));
        d.addAction(new MechanicAction(ActionType.LOOT, Map.of("table", "missing")));
        d.addAction(new MechanicAction(ActionType.MESSAGE, Map.of("text", "hi")));   // pas une action loot
        assertEquals(java.util.Set.of("missing"), d.danglingLootTables(java.util.Set.of("common")::contains));
        assertEquals(java.util.Set.of("common", "missing"), d.danglingLootTables(null));  // null → tout pendant
        assertTrue(new MechanicDef("x").danglingLootTables(t -> false).isEmpty());        // aucune action loot
    }

    @Test
    void matchesContextRespectsMatchKeyAndTrigger() {
        MechanicDef d = new MechanicDef("m");
        d.setTrigger(TriggerType.BREAK_BLOCK);   // utilise matchKey
        // Sans matchKey : accepte tout (y compris null).
        assertTrue(d.matchesContext("stone"));
        assertTrue(d.matchesContext(null));
        // Avec matchKey (stocké en minuscule) : égalité insensible à la casse.
        d.setMatchKey("custom:Ruby_Ore");
        assertTrue(d.matchesContext("custom:ruby_ore"));
        assertTrue(d.matchesContext("CUSTOM:RUBY_ORE"));
        assertFalse(d.matchesContext("stone"));
        assertFalse(d.matchesContext(null));     // matchKey précis ne matche pas un contexte absent
    }

    @Test
    void matchesContextIgnoresMatchKeyForTriggersWithoutOne() {
        MechanicDef d = new MechanicDef("m");
        d.setTrigger(TriggerType.PLAYER_JOIN);   // n'utilise pas de matchKey
        d.setMatchKey("ignored");
        assertTrue(d.matchesContext(null));      // matchKey ignoré → accepte tout
        assertTrue(d.matchesContext("whatever"));
    }

    @Test
    void customItemsUsedCollectsOnlyCustomGiveItemRefs() {
        MechanicDef d = new MechanicDef("rewarder");
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of("item", "custom:Magic_Wand")));  // normalisé minuscule
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of("item", "custom:magic_wand")));  // doublon
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of("item", "DIAMOND")));            // vanilla → ignoré
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of()));                             // sans item → ignoré
        d.addAction(new MechanicAction(ActionType.MESSAGE, Map.of("text", "hi")));                   // autre type → ignoré
        assertEquals(java.util.Set.of("magic_wand"), d.customItemsUsed());
        assertTrue(new MechanicDef("x").customItemsUsed().isEmpty());
    }

    @Test
    void bossesUsedCollectsOnlySpawnMobBossRefs() {
        MechanicDef d = new MechanicDef("autel");
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of("entity", "boss:Dragon_Noir")));  // normalisé minuscule
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of("entity", "boss:dragon_noir")));  // doublon
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of("entity", "ZOMBIE")));            // vanilla → ignoré
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of()));                              // sans entity → ignoré
        d.addAction(new MechanicAction(ActionType.MESSAGE, Map.of("text", "hi")));                    // autre type → ignoré
        assertEquals(java.util.Set.of("dragon_noir"), d.bossesUsed());
        assertTrue(new MechanicDef("x").bossesUsed().isEmpty());
    }

    @Test
    void danglingBossesUsesInjectedPredicate() {
        MechanicDef d = new MechanicDef("autel");
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of("entity", "boss:connu")));
        d.addAction(new MechanicAction(ActionType.SPAWN_MOB, Map.of("entity", "boss:absent")));
        assertEquals(java.util.Set.of("absent"), d.danglingBosses(java.util.Set.of("connu")::contains));
        assertEquals(java.util.Set.of("connu", "absent"), d.danglingBosses(null));   // null → tout pendant
        assertTrue(new MechanicDef("x").danglingBosses(t -> false).isEmpty());        // aucune action spawn_mob
    }

    @Test
    void danglingCustomItemsUsesInjectedPredicate() {
        MechanicDef d = new MechanicDef("rewarder");
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of("item", "custom:known")));
        d.addAction(new MechanicAction(ActionType.GIVE_ITEM, Map.of("item", "custom:gone")));
        assertEquals(java.util.Set.of("gone"), d.danglingCustomItems(java.util.Set.of("known")::contains));
        assertEquals(java.util.Set.of("known", "gone"), d.danglingCustomItems(null));   // null → tout pendant
        assertTrue(new MechanicDef("x").danglingCustomItems(t -> false).isEmpty());      // aucune action give_item
    }

    @Test
    void costClampsAndRoundTrips() {
        MechanicDef d = new MechanicDef("paid");
        assertFalse(d.hasCost());                 // gratuit par défaut
        d.setCost(-50);
        assertEquals(0.0, d.cost(), 1e-9);        // négatif → 0
        d.setCost(250.0);
        assertTrue(d.hasCost());
        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        MechanicDef back = MechanicDef.load("paid", cfg);
        assertEquals(250.0, back.cost(), 1e-9);
        assertTrue(back.hasCost());
    }

    @Test
    void clampsCooldownAndInterval() {
        MechanicDef d = new MechanicDef("x");
        d.setCooldownTicks(-5);
        assertEquals(0, d.cooldownTicks());
        d.setIntervalTicks(0);
        assertEquals(1, d.intervalTicks());                // plancher 1
    }

    @Test
    void chanceClampsAndGates() {
        MechanicDef d = new MechanicDef("x");
        assertEquals(1.0, d.chance(), 1e-9);               // défaut : toujours
        assertTrue(d.passes(0.99));                        // chance 1.0 → tout passe
        d.setChance(0.3);
        assertEquals(0.3, d.chance(), 1e-9);
        assertTrue(d.passes(0.0));                         // tirage < 0.3 → passe
        assertTrue(d.passes(0.29));
        assertFalse(d.passes(0.3));                        // borne exacte → échoue
        assertFalse(d.passes(0.9));
        d.setChance(5.0);
        assertEquals(1.0, d.chance(), 1e-9);               // clampé à 1
        d.setChance(-2.0);
        assertEquals(0.0, d.chance(), 1e-9);               // clampé à 0
        assertFalse(d.passes(0.0));                        // chance 0 → rien ne passe
    }

    @Test
    void chanceRoundTrips() {
        MechanicDef d = new MechanicDef("x");
        d.setChance(0.25);
        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        assertEquals(0.25, MechanicDef.load("x", cfg).chance(), 1e-9);
    }

    @Test
    void permissionNormalizationAndRoundTrip() {
        MechanicDef d = new MechanicDef("x");
        assertTrue(d.isPublic());                          // aucune par défaut
        d.setPermission("  none  ");                       // 'none' → public
        assertTrue(d.isPublic());
        d.setPermission("mooncore.perk.fly");
        assertFalse(d.isPublic());
        assertEquals("mooncore.perk.fly", d.permission());

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        MechanicDef back = MechanicDef.load("x", cfg);
        assertEquals("mooncore.perk.fly", back.permission());
        assertFalse(back.isPublic());
    }
}
