package com.mooncore.data.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ContentMigrator.Result#total()} somme tous les types migrés, loot inclus (les erreurs n'y
 * entrent pas). Verrou de régression pour l'ajout de nouveaux types. Pur, sans serveur.
 */
class ContentMigratorResultTest {

    @Test
    void totalSumsAllTypesExcludingErrors() {
        ContentMigrator.Result r = new ContentMigrator.Result(3, 2, 1, 4, 5, 6, 7);
        assertEquals(21, r.total());   // 3+2+1+4+5+6, pas les 7 erreurs
        assertEquals(5, r.loot());
        assertEquals(6, r.mechanics());
        assertEquals(7, r.errors());
    }
}
