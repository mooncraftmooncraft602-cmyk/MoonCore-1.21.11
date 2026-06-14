# CLAUDE.md — MoonCore (Paper/Purpur 1.21.11, Java 21)

Guide pour une instance Claude travaillant sur ce dépôt. Plugin Minecraft « ligne avancée » (v2.x),
shaded jar (HikariCP, Caffeine, MariaDB, SQLite, Gson). Vision : MCreator + Blockbench **in-game**.

## Build & tests
Le wrapper `mvnw.cmd` est **cassé** (lance le help de Java 8). Buildez via JDK 21 + la classe wrapper directe :

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
& "$env:JAVA_HOME\bin\java.exe" -classpath ".mvn\wrapper\maven-wrapper.jar" `
  "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" `
  org.apache.maven.wrapper.MavenWrapperMain package          # ou: test  |  "-Dtest=MaClasseTest" test
```

Le jar shadé sort dans `target/`. Build + tests doivent rester **verts** à chaque commit.

## Règles de contribution
- ⚠️ **Git** : ne JAMAIS `git commit -am` (le repo a ~10 modifs préexistantes non liées). Toujours
  `git add <fichiers précis>` puis commit. Terminer le message par la ligne `Co-Authored-By: Claude …`.
- **Tests headless** : pas de serveur Bukkit. Utilisables : `MemoryConfiguration`, `Material.matchMaterial`,
  enums, JOML, records purs. **NON** utilisables (chargent le `Registry`/serveur) : `Material.isItem()`,
  `org.bukkit.Attribute`, l'enum `Instrument` (donc `BlockStateMap`), `Sound`/`Particle` à l'init de classe.
  → testez la **logique pure** (RNG/horloge/prédicats injectés) ; isolez-la dans des helpers package-private.
- Migrations SQL : numéros uniques. Réservés : core 1–99, antifarm 200, IA 1300, content **1400**,
  crop_placement **1401**, season **800**. Le contenu data-driven (loot/mécanique) réutilise `mooncore_content`.

## Architecture
- **Modules** : `AbstractModule` + `@ModuleInfo(id, name, softDepends)`, enregistrés dans
  `MoonCore.registerModules()`, activés par `config.yml` → `modules:`. Services via `services().get(X.class)`.
- **Commandes** : interface `SubCommand`, enregistrées par `plugin().rootCommand().register(...)`.
- **Contenu data-driven** : patron `Def → Store → Module → Listener → Editor/Command → schéma IA`.
  Façade unifiée Étape E : `ContentTypeHandler` + `ContentTypeRegistry` → `/moon content …`.
- **Stockage universel** : `data/content/` (table `mooncore_content`, pont `ContentJson` YAML↔JSON,
  `ContentSyncService` flag `content.storage-mode` yaml|sql|both, `ContentMigrator` = `/moon admin migrate-content`).

## Systèmes data-driven principaux
- **Items custom** (`modules/customitem`) : Data Components 1.21 via `ItemComponentApplier` (item_model,
  equippable, food, tool, max_damage, glider…). Recettes : shaped/shapeless/furnace/blast/smoker/stonecutting
  **+ smithing** (`/moon item smithing`, `/moon ai createsmithing`).
- **Tables de loot** (`modules/loot`) : pools pondérés, **tables imbriquées** (`table:<id>`, anti-cycle via
  `LootResolver`), 4 consommateurs (culture/bloc/boss + action mécanique `loot`). `/moon loot …`
  (addpool/addentry/test/give/fill/validate/…).
- **Mécaniques** (`modules/mechanic`) : trigger→action data-driven, 11 triggers / 19 actions, gating
  `enabled→matchKey→permission→chance→cooldown`. `/moon mechanic …`. Exécution LIVE défensive.
- **Cultures** (`modules/crop`), **blocs custom** (`modules/customblock`, note_block + `BlockStateMap`),
  **boss** (`modules/boss`).

## Reste à faire (LIVE — validation EN JEU requise, hors boucle autonome)
Brewing (listener `BrewEvent`) ; éditeurs GUI in-game (loot/mécanique/studio 3D) ; Étape D « live »
(rendu os ItemDisplay texturé, paint par cube, push protocol v2 du mod compagnon). Décision humaine :
PR/merge de la branche `loop/master-brain` → `main`. Détails d'avancement : `docs/HANDOFF-LOOP.md`.
