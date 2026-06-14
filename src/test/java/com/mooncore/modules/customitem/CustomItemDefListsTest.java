package com.mooncore.modules.customitem;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip YAML des champs LISTE de {@link CustomItemDef} : drops (loot), capacités, effets de
 * consommation. Pur (MemoryConfiguration), sans serveur.
 */
class CustomItemDefListsTest {

    @Test
    void dropsRoundTrip() {
        CustomItemDef d = new CustomItemDef("relique");
        d.drops().add(new CustomItemDef.DropRule("boss:gardien", 0.25, 1, 3));
        d.drops().add(new CustomItemDef.DropRule("mob:ZOMBIE", 0.05, 1, 1));

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("relique", cfg);

        assertEquals(2, back.drops().size());
        assertEquals("boss:gardien", back.drops().get(0).source());
        assertEquals(0.25, back.drops().get(0).chance(), 1e-9);
        assertEquals(3, back.drops().get(0).max());
        assertEquals("mob:ZOMBIE", back.drops().get(1).source());
    }

    @Test
    void abilitiesRoundTrip() {
        CustomItemDef d = new CustomItemDef("baton");
        d.addAbility("lifesteal", 2);
        d.addAbility("dash", 1);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("baton", cfg);

        assertEquals(2, back.abilities().size());
        assertEquals("lifesteal", back.abilities().get(0).id());
        assertEquals(2, back.abilities().get(0).level());
    }

    @Test
    void consumeEffectsRoundTrip() {
        CustomItemDef d = new CustomItemDef("potion");
        d.setConsumeEffect("regeneration", 200, 1);
        d.setConsumeEffect("speed", 600, 0);

        MemoryConfiguration cfg = new MemoryConfiguration();
        d.save(cfg);
        CustomItemDef back = CustomItemDef.load("potion", cfg);

        assertEquals(2, back.consumeEffects().size());
        var regen = back.consumeEffects().stream().filter(c -> c.key().equals("regeneration")).findFirst().orElseThrow();
        assertEquals(200, regen.duration());
        assertEquals(1, regen.amplifier());
    }
}
