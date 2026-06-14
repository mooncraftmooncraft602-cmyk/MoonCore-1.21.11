# HANDOFF — Boucle autonome « plan section 4 » (branche `loop/master-brain`)

> Instance Claude en **mode boucle** ayant exécuté **tout le plan d'action de la section 4** du
> `PROJECT_MOON_MASTER_BRAIN.md` (Étapes A→E), une sous-tâche atomique par passe, build + tests verts
> à chaque commit. Puis **backlog vision §2** : 2 nouveaux types data-driven complets (loot + mécaniques).
> **237 tests verts** au dernier point. Branche : `loop/master-brain` (non encore mergée sur `main`).
> Build : voir mémoire `mooncore-build-command` (JDK 21 + wrapper, le `mvnw` direct est cassé).

## Ce qui a été livré (par étape)

### A — Stockage universel requêtable (`data/content/`)
- `UniversalContentStore` : table SQL `mooncore_content(content_type,id,data_json,schema_version,created_at,updated_at)`, migration **1400**, CRUD async + Gson.
- `ContentJson` : pont générique **YAML↔JSON** (réutilise les `save/load(section)` existants, zéro couplage data→modules).
- `ContentSyncService` : double-write piloté par flag config **`content.storage-mode`** = `yaml`(défaut)|`both`|`sql`.
- `ContentMigrator` + commande **`/moon admin migrate-content`** (importe items/blocks/bosses YAML → SQL, idempotent).
- `ContentSchema`/`ContentSchemas` : versionnage de schéma + `upgradeToCurrent(json, fromVersion)` à la lecture SQL.
- Branché en **écriture** sur les modules **item** (lecture SQL aussi), **block** et **crop** (YAML canonique).

### B — Data Components 1.21 natifs (`customitem/`)
- `ItemComponentApplier` : **couche unique** (item_model, equippable, glint, unbreakable, flags, attributs, enchants, food, tool, + helper `applyGlider` réflexion). `CustomItemFactory`/`EndgameItemsModule` délèguent.
- **food** (`minecraft:food`+`consumable`) : champ `food{nutrition,saturation,canAlwaysEat,eatSeconds}` + GUI `FoodEditorMenu` + listener `PlayerItemConsumeEvent` (fallback clic-droit conservé) + schéma IA.
- **tool** (`minecraft:tool`) : champ `tool{mining-speed,damage-per-block,rules[]}` (record `ToolRule`) + GUI `ToolRulesMenu` (auto-règle depuis ToolKind) + schéma IA.

### C — Cultures custom à cycle de ticks (`crop/`)
- `CropDef` + `CropDefStore` (YAML), `CropVisual` (rendu **ItemDisplay** texturé par étape, pas note_block — voir doc dans la classe).
- `CropManagerModule` + `CropPlacementStore` (table `mooncore_crop_placement`, migration **1401**) ; tick de croissance **batché par chunk chargé** (index `byChunk`, conditions lumière/support/eau) ; `CropListener` (ChunkLoad/Unload + planter/récolter/replanter).
- Commande `/moon crop …`, schéma IA `cropSchemaSystem()`+`validateCrop`, `CropPackBuilder` (item-model par étape, intégré au `PackAssembler`).

### D — Éditeur 3D in-game « Blockbench » (`model/editor/`) — briques PURES, testées
- `EditableRig`/`EditableBone`/`CubeFace` (géométrie mutable, UV/face), `RigBone.itemModelKey`.
- `ModelEditorSession` (matérialise via `RigInstance`) + `RigRaytracer` (sélection cube ray→AABB).
- `ModelEditorTools` (translate/scale/pivot/duplicate) + `RigHistory` (undo/redo).
- `BoneItemModelBuilder`/`RigModelPackBuilder` (os texturé → modèle JSON), `EditableAnimation` (keyframes par os/canal).
- `BlockBenchExporter` (.bbmodel, **round-trip vérifié** via l'importeur) + `RigToItemModel` + `RigModelStore` (persistance models/).
- ⚠️ **PARTIES LIVE NON FAITES** (nécessitent une validation visuelle en jeu, impossible en boucle autonome) :
  swap entité BlockDisplay→**ItemDisplay** texturé (calibrage des transforms), peinture par cube (PaintSession),
  push protocol v2, GUI studio 3D + timeline `AnimationEditorMenu`, lien `BossModelMenu`, pose `RigToItemModel` sur item tenu.

### E — Commande de création unifiée (`create/`) — la « North Star »
- `ContentTypeRegistry` + `ContentTypeHandler` (façade par type) + handlers **item/block/crop/boss**.
- `CreateModule` (module `create`, softDepends contenu+ai) + `CreateSubCommand` → **`/moon content`** :
  `create|createall|edit|delete|list|info|clone|give|types <type> …`.
- **Création IA langage-naturel** : `/moon content create <type> <id> <description…>` (et `createall` multi-créations
  chaînées via `unifiedCreateSystem()`). `--dry` (preview sans persister), perms granulaires
  `mooncore.admin.create.<type>`, confirmation `delete … confirm`, audit `mooncore_ai_audit`, rebuild pack.

## Backlog vision §2 — nouveaux types data-driven (post-plan, COMPLETS)

### Tables de loot génériques (`modules/loot/`)
- `LootEntry`/`LootPool`/`LootResult`/`LootTableDef` : pools pondérés, sélection cumulée **robuste aux bornes**,
  rolls, round-trip YAML (ordre préservé). Tirage **100% pur** (RNG injecté) → testé déterministe.
- `LootTableStore` (loot/<id>.yml) + `LootManagerModule` (registry, miroir SQL type `loot`, API `roll`).
- **3 consommateurs** : `CropDef`/`CustomBlockDef`/`BossDefinition` ont un champ `lootTableId` ; récolte de
  culture / casse de bloc / défaite de boss tirent la table (repli sur le drop fixe).
- `/moon loot` (create/addpool/addentry/clearpools/**test** simulation/…) + handler `/moon content … loot` + IA
  (`lootSchemaSystem`/`validateLoot`) + import `ContentMigrator`. **Complet (model→store→module→3 conso→cmd→IA→SQL).**

### Mécaniques génériques trigger→action (`modules/mechanic/`)
- `TriggerType` (11 : INTERACT/BREAK/PLACE_BLOCK, USE_ITEM, KILL_ENTITY, DAMAGE_TAKEN, SNEAK, RESPAWN, JOIN, QUIT,
  INTERVAL) + `ActionType` (15 : message/command/sound/potion/give_item/money/damage/heal/xp/teleport/lightning/
  spawn_mob/title/clear_effects/feed), `fromText` **tolérant** (alias FR-EN, défaut NONE).
- `MechanicDef` (trigger/matchKey/cooldown/interval/**chance**/**permission**/enabled/actions, round-trip, `isRunnable`).
- `MechanicCooldowns` (pur, temps en ticks injecté, **testé aux bornes**) + `MechanicExecutor` (LIVE, dispatch
  défensif) + `MechanicListener` (events→triggers, MONITOR/ignoreCancelled) + tick INTERVAL. **Système actif en jeu.**
- `/moon mechanic` (trigger/match/cooldown/interval/chance/permission/addaction/test/…) + handler `/moon content …
  mechanic` + IA (`mechanicSchemaSystem`/`validateMechanic`) + import `ContentMigrator`. **Complet.**
- Gating d'exécution : `enabled` → `matchKey` → `permission` → `chance` → `cooldown`.
- **Restes (LIVE/optionnel)** : éditeur GUI, triggers/actions additionnels.

## Pour tester en jeu (côté utilisateur)
1. Build (mémoire `mooncore-build-command`), déposer le jar, démarrer le serveur 1.21.11.
2. `content.storage-mode: both` dans config.yml puis `/moon admin migrate-content` pour peupler le SQL.
3. `/moon content create item epee_lunaire une epee legendaire qui vole de la vie` (IA), `… --dry` pour prévisualiser.
4. `/moon content createall un minerai lunaire, sa pioche et son boss gardien`.
5. Cultures : `/moon crop create ble_lunaire`, régler, `/moon crop giveseed <joueur> ble_lunaire`, planter sur FARMLAND.
6. Loot : `/moon loot create butin`, `/moon loot addpool butin 1 1`, `/moon loot addentry butin 0 DIAMOND 5 1 2`,
   `/moon loot test butin 10` ; lier : `/moon crop drop`/`block`/`boss` via leur champ `loot-table` = `butin`.
7. Mécaniques : `/moon mechanic create zap`, `/moon mechanic trigger zap use_item`, `/moon mechanic match zap custom:baguette`,
   `/moon mechanic addaction zap lightning`, `/moon mechanic chance zap 0.5`, `/moon mechanic test zap`.
8. Valider les **parties live de l'Étape D** (rendu/édition 3D) — c'est ce qui reste à finaliser.

## Notes d'intégration
- Migrations réservées : **1400** (content), **1401** (crop_placement). 1300=IA, 200=antifarm.
- Ne pas casser l'API `CompanionModule` (mod client). RigBone a un nouveau champ optionnel `itemModelKey` (ctor rétrocompat).
- ⚠️ **Git** : ne jamais `git commit -am` (le repo a ~10 modifs préexistantes non liées) ; toujours `git add <fichiers>`.
