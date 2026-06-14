# 🌙 PROJECT_MOON_MASTER_BRAIN — Ligne AVANCÉE 1.21.11 (v2.0.0)

> **Document de contexte absolu pour MoonCore — LIGNE AVANCÉE 1.21.11.**
> Destiné à une instance Claude qui prendra le relais du développement en **mode boucle (loop) autonome 24 h**.
> Lis ce fichier **en entier** avant toute action.
>
> ⚠️ **Tu es sur la ligne AVANCÉE, pas la baseline.** Deux dossiers coexistent (stratégie **BI-DOSSIER**) :
> - `moonpurpur` = **baseline** Paper **1.21.1**, version **1.1.0** (ligne stable).
> - `moonpurpur-1.21.11` = **CE DOSSIER**, ligne **avancée 1.21.11**, version **2.0.0** — *build séparé*.
>   Elle embarque les briques modernes que la baseline n'a pas : **item_model** (1.21.4+), **armures portées
>   custom** (equippable 1.21.2+), **DataComponent API** (GLIDER), **aperçu 3D in-game** (ItemDisplay),
>   **import auto de textures**, **mod compagnon protocol v2**, et les modules **shop / auction / spawner**.
>
> - **Projet** : MoonCore — plugin Paper/Purpur **1.21.11**, Java 21
> - **Version** : `2.0.0` (voir `pom.xml`)
> - **Taille** : ~310 fichiers `.java`, ~33 000 lignes, 44 modules métier
> - **Snapshot** : 2026-06-13
> - **Auteur du handoff** : instance Claude « Lead System Architect »

---

## 0. PRÉAMBULE — ONBOARDING DU CLAUDE EN BOUCLE

### 0.1 Ce qu'est cette ligne (en une phrase)

La **version moderne** de MoonCore : un serveur de saison **et** un atelier de création de contenu in-game
(`/moon studio`, commandes, assistant IA), qui exploite déjà les **composants de données récents** de Minecraft
1.21.x (item_model, equippable) et dispose d'un **pont vers un mod client Fabric** (rendu 3D haute qualité).

### 0.2 L'objectif ultime (la « North Star »)

Faire de MoonCore un **équivalent de MCreator entièrement in-game** : créer **tout ce que le jeu permet** sans
quitter Minecraft ni recompiler, y compris un **éditeur 3D façon Blockbench** en jeu. Cette ligne 2.0.0 a déjà
franchi une partie du chemin (cf. §1.7, §1.8). Le plan d'exécution restant est en **section 4**.

### 0.3 ⚠️ Le contexte MULTI-IA (très important pour la boucle)

Cette ligne a été développée par **plusieurs instances Claude en parallèle**, coordonnées par des fichiers de
handoff. **Lis-les** :
- `docs/HANDOFF-AI2.md` — IA n°2 a livré **shop / auction / spawner** (intégration + permissions).
- `docs/HANDOFF-AI3.md` — IA n°3 a livré le **companion protocol v2** (mod client + canal plugin-message).

Si tu produis du code destiné à être intégré par une autre instance, **écris un handoff** du même type
(zone modifiée, points d'intégration dans `MoonCore.java`/`plugin.yml`, dépendances, notes anti-dupe).

### 0.4 Build & lancement (⚠️ pièges Windows)

```powershell
cd "c:\Users\PC\Desktop\hug pas touche\moonpurpur-1.21.11"
.\mvnw.cmd -q -DskipTests package      # ⚠️ chemin AVEC espaces → toujours quoter
# Sortie : target/MoonCore-2.0.0-mc1.21.11.jar (finalName suffixé -mc1.21.11 ; shadé HikariCP/Caffeine/MariaDB/SQLite/Gson)
```

- **Build SÉPARÉ de la baseline** : ne pas confondre les `target/` des deux dossiers. Cette ligne cible 1.21.11.
- **Mod compagnon** : `companion-mod/` (Fabric Loom, JDK 21) → `cd companion-mod ; .\gradlew.bat build`.
  Le `.jar` va dans `companion-mod/build/libs/`. Optionnel côté joueur (Java + Fabric).
- **Piège chemin à espaces** : artefacts `javac.*.args` (ignorés via `.gitignore`).

### 0.5 Topologie de déploiement (serveurs Feather)

- **Serveur principal 1.21.11** : `fafef64c…` (cette ligne tourne ici).
- **Serveur de contenu / source** : `a5178a61-fa82-4165-93f2-5c8418f53b59` →
  `plugins/MoonCore/` contient `vanilla-textures/`, `items-textures/`, `blocks-textures/`, `boss-textures/`,
  **`armor-textures/`** (nouveau : couches d'armure portée), `models/` (`.bbmodel`). C'est le 2e working directory.
- **Mod client** distribué via le flux user-mods ; débloque les outils avancés pour les joueurs Java qui l'ont.

### 0.6 Discipline du mode boucle (LIRE ABSOLUMENT)

1. **Une tâche atomique par itération** (cf. §4). Jamais deux chantiers structurels en parallèle.
2. **Compiler après chaque changement** (`mvnw package`). Rouge → réparer avant d'avancer.
3. **Aucune régression d'API publique** (`ServiceRegistry`, signatures de modules, **API `CompanionModule`** que
   le mod client consomme — cf. §1.9). `grep` les usages avant de renommer/supprimer.
4. **Data-driven d'abord** : capacité **configurable YAML / éditable GUI** plutôt qu'en dur.
5. **Tout I/O en async** ; effets-monde sur le main thread.
6. **Tests verts** : `mvnw test`.
7. **Compat rétro des composants** : la DataComponent API moderne peut manquer sur un Paper plus ancien →
   suivre le **pattern réflexion + try/catch** déjà utilisé pour `GLIDER` (§1.7) quand un composant est risqué.
8. **Handoff** si une autre IA doit intégrer ; **commits nommés** `vX.Y.Z — résumé FR` ; brancher avant de
   committer sur `main`.

---

## 1. ARCHITECTURE ET ÉTAT ACTUEL (ligne 2.0.0)

> La **fondation** (bootstrap, modules, stockage, blocs note_block, paint editor, assistant IA) est **partagée
> avec la baseline** — voir le `PROJECT_MOON_MASTER_BRAIN.md` du dossier `moonpurpur` pour le détail commun.
> Cette section met l'accent sur ce qui est **propre/avancé** dans la ligne 1.21.11.

### 1.1 Stack & chiffres

| Couche | Technologie |
|---|---|
| Runtime | Paper/Purpur **1.21.11**, Java **21**, `api-version: '1.21'` |
| Build | Maven (`mvnw`), shade-plugin (HikariCP, Caffeine, MariaDB, SQLite, Gson) |
| DB | **SQLite** (défaut) ou **MySQL/MariaDB**, **HikariCP** (pool 1 SQLite / 10 MySQL) |
| Cache | **Caffeine** (TTL 600 s, max 50k) |
| Économie | **Vault** (hook) |
| Maths 3D | **JOML** (`Vector3f`, `Matrix4f`, `Quaternionf`, `AxisAngle4f`) |
| Composants 1.21 | `meta.setItemModel`, `meta.getEquippable/setEquippable`, `ItemStack.setData(DataComponentTypes.GLIDER)` |
| Mod client | Fabric Loom (`companion-mod/`), **protocol v2 chunké** |

### 1.2 Bootstrap & modules (`MoonCore.java`)

Ordre strict : `ConfigManager` → services noyau (`ServiceRegistry`, `EventBus`, `Schedulers`) → `DataManager`
(**fail-fast** si DB KO) → commande `/moon` → `registerModules()` + `enableAll()` → `StandaloneCommands`.

**44 modules** enregistrés. Modules **propres/notables de cette ligne** (au-delà du socle baseline) :
`ModelEngineModule`, `CompanionModule` (**protocol v2**), `ResourcePackModule`, `AiAdminModule`, `UpdateModule`
(auto-update GitHub), et les **3 modules de l'intégration IA n°2** : `ShopModule`, `AuctionModule`,
`SpawnerGuiModule`. Communication inter-modules via `ServiceRegistry` (interfaces) + `EventBus` (événements typés).

### 1.3 La commande `/moon studio` (GUI) — version enrichie

Même pattern que la baseline (`StudioMenu extends InventoryHolder`, routage par `StudioListener`, saisie par
`ChatInput`, utilitaires `StudioItems`, listing paginé `CONTENT_SLOTS[36]`). **Nouveautés 1.21.11** :

| Élément | Fichier | Apport |
|---|---|---|
| **Aperçu 3D in-game** | `StudioPreview` (NOUVEAU) | spawn un **`ItemDisplay`** tournant (~15 s, scale 1.6, billboard FIXED, interpolation 3 ticks) ~2,2 blocs devant le joueur pour valider un item packé **sans le donner** |
| **Import auto de textures** | `StudioImport` (NOUVEAU) | dépose `<id>.png` dans `items-textures/` → `/moon studio import` bind chaque PNG à la `CustomItemDef` (`setModelKey` + CMD libre) ; scanne `armor-textures/` (`<id>_body.png`, `<id>_legs.png`) → `setEquipmentKey` → **armure portée activée** ; rebuild pack une seule fois |
| **Équipement de boss** | `BossEquipmentMenu` (NOUVEAU) | 27 slots (casque/plastron/jambières/bottes/mainhand/offhand) ; clic = saisie `custom:<id>` ou `MATERIAL` ; persiste `equipment.<slot>` dans `BossDefinition` |
| Hub textures | `StudioTextureMenu` | slot « Importer dossier » → `StudioImport.run()` ; peinture item/bloc/boss ; retexture IA |

Sous-menus du hub (slots) : Items (10), Recettes (12), Blocs (14), Mobs (16), Boss (28), Textures (30),
Assistant IA (32), Resource pack (34). **Manques GUI** (cibles §4) : pas d'**éditeur 3D in-game** (le preview est
read-only), `RecipeEditorMenu` **3×3 shaped seulement**, pas d'UI pour **food/tool** natifs, import = PNG (pas `.bbmodel`).

### 1.4 Stockage (hybride — identique baseline)

- **SQL** (`DataManager`/`Database`, HikariCP + Caffeine, migrations `MigrationRunner`/`mooncore_schema_version`)
  pour **données joueur** (`mooncore_player_profile`, `mooncore_statistics` EAV) + **audit IA** (`mooncore_ai_audit`, v1300).
- **YAML** pour le **contenu** : `items/<id>.yml` (`CustomItemDefStore`), `blocks/<id>.yml` (`CustomBlockStore`),
  `content/bosses/<id>.yml`, `models/boss-rigs.yml`. **+ propres à cette ligne** : `shop.yml`, `auction.yml` +
  `auction_refunds.yml` (AH, persistance `ItemStack.serialize()`), `armor-textures/` (sources d'armure).
- **PDC** : identité item (`ci_id`), bloc-item (`cb_id`), états de capacités.

> ⚠️ Même limite qu'en baseline : pas de store **universel requêtable** → c'est l'**Étape A** (toujours à faire).

### 1.5 Pipeline Resource Pack (MODERNISÉ)

`PackAssembler` assemble `pack-sources/` + builders → `pack.zip` + SHA-1, servi par `HttpPackServer`
(`/pack.zip?v=<sha1>`, URL versionnée pour forcer le refresh), envoyé forcé via `ResourcePackListener`
(`setResourcePack(url, sha1, force)`). **Différences clés 1.21.11** :

- **`pack.mcmeta`** : `pack_format: 75` + `supported_formats: {min_inclusive: 64, max_inclusive: 99}`
  (la baseline était à **34**).
- **Builders** : `ResourcePackBuilder` (items, **item_model**), `CustomBlockPackBuilder` (note_block),
  `EquipmentPackBuilder` (**armures portées**, NOUVEAU), `BossPackBuilder`.
- Namespace dédié **`mooncore`** (`ResourcePackBuilder.NS`) pour isoler les assets.

### 1.6 Modèle item & composants 1.21 (CustomItemFactory) — AVANCÉ

`CustomItemFactory.build(def, amount)` pose, en plus du lore/PDC/attributs/enchants/glow/unbreakable (comme
baseline), **les composants modernes** :

```java
// item_model (1.21.4+) — remplace custom_model_data ; clé string, zéro collision
if (def.modelKey() != null)
    meta.setItemModel(new NamespacedKey(ResourcePackBuilder.NS, def.modelKey()));

// equippable (1.21.2+) — armure visible SUR LE CORPS du joueur (3e personne)
if (def.equipmentKey() != null) {
    EquipmentSlot slot = armorSlot(def.material());      // *_HELMET→HEAD, *_CHESTPLATE/ELYTRA→CHEST, *_LEGGINGS→LEGS, *_BOOTS→FEET
    EquippableComponent eq = meta.getEquippable();
    eq.setSlot(slot);
    eq.setModel(new NamespacedKey(ResourcePackBuilder.NS, def.equipmentKey()));
    meta.setEquippable(eq);
}
```

**`CustomItemDef` a un champ `equipmentKey` de plus que la baseline.** Champs : `id, displayName, type, rarity,
material, toolKind, toolTier, customModelData(legacy), modelKey, equipmentKey, glowing, unbreakable, lore[],
stats{}, abilities[], drops[], recipe, smelt*, cut*, enchants{}, consumeEffects[]`.

**Assets générés** (namespace `mooncore`) :

```jsonc
// assets/mooncore/items/<key>.json  (item-definition 1.21.4+)
{ "model": { "type": "minecraft:model", "model": "mooncore:item/<key>" } }
// assets/mooncore/models/item/<key>.json
{ "parent": "item/handheld" /* ou item/generated */, "textures": { "layer0": "mooncore:item/<key>" } }
// assets/mooncore/models/equipment/<key>.json  (armure portée — EquipmentPackBuilder)
{ "layers": { "humanoid": [ { "texture": "mooncore:<key>" } ],
              "humanoid_leggings": [ { "texture": "mooncore:<key>" } ] } }   // couche déclarée SI sa texture existe
// textures : entity/equipment/humanoid/<key>.png (corps) + humanoid_leggings/<key>.png (jambes)
//            sources = armor-textures/<key>_body.png | <key>_legs.png | <key>.png
```

> **✅ DÉJÀ FAIT ici (était roadmap baseline)** : `item_model` moderne (Étape B5), **armure portée equippable**
> (Étape B4). **❌ Toujours simulé/absent** : `minecraft:food`, `minecraft:consumable`, `minecraft:tool`,
> `minecraft:max_damage` (les effets de conso passent encore par un listener au clic droit, pas par le composant).

### 1.7 DataComponent API réelle (preuve d'usage)

`EndgameItemsModule` (`enditems`) pose le composant **`minecraft:glider`** via **réflexion** (Paper 1.21.4+),
avec garde de rétro-compat :

```java
Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
Object glider = types.getField("GLIDER").get(null);
Class<?> nonValued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$NonValued");
ItemStack.class.getMethod("setData", nonValued).invoke(item, glider);   // try/catch → no-op si indispo
```

Items endgame : **Netherite Flight Core** (plastron + vol Elytra via GLIDER) et **Recall Staff** (téléport,
cooldown 60 s). **C'est le pattern de référence** pour exploiter d'autres composants modernes sans casser un
Paper plus ancien (Étape B).

### 1.8 Moteur 3D & aperçu (état exact)

- **`RigInstance` rend toujours via `BlockDisplay`** (un par os, cubes de `BlockData` — rig « blocky »), animé par
  composition matricielle `jointLocal = T(transl)·T(pivot)·R(rot)·S(scale)·T(-pivot)`, `jointWorld = parent·local`,
  `mesh = world·T(from)·S(size)`, `setTransformationMatrix` + interpolation client. **Pas encore d'`ItemDisplay`
  texturé par os.**
- `RigBone` = `name, parent, pivot, from, to, BlockData block`. `Animation` = keyframes
  `(time, translation, rotationDeg, scale)`, `sample()` interpolé, base loop + override one-shot.
- `BlockBenchImporter.parse(.bbmodel)` (Bukkit-free) → géométrie + animations → `toRigModel(IRON_BLOCK)`.
- `ModelEngineModule` : tick 2 ticks, cap 64, culling 48 b, `spawnFollowing` (mob/boss invisible + rig suit),
  `boss-rigs.yml`. **NOUVEAU** : `RigInstance.displayUuids()` + `pushToCompanions()` → envoie le rig au **mod
  client v2** (rendu haute qualité + masquage du fallback BlockDisplay).
- **`StudioPreview`** : aperçu **`ItemDisplay`** tournant (read-only) d'un item.

> **Le moteur d'animation + l'import .bbmodel + le pont mod client existent.** Manquent pour un Blockbench in-game :
> os **texturés** (ItemDisplay + modèle JSON), **édition** en jeu (cubes/pivots/UV/keyframes), **export**. → Étape D.

### 1.9 Mod compagnon — protocol v2 (`CompanionModule`)

Canal plugin-message **`mooncore:companion`**. v1 (HELLO/WELCOME + bitmask) reste supporté ; **v2** ajoute le
transport **chunké** (28 KiB/chunk) pour pousser rigs/animations/armures.

- **Capacités v2 (bitmask)** : `0x01` studio2D, `0x02` modèles3D, `0x04` entités, `0x08` protocolV2, `0x10`
  chunking, `0x20` armor-metadata (v2 complet = `0x3F`).
- **Opcodes serveur→client** : `0x10 PUSH_RIG`, `0x11 PUSH_ANIM`, `0x12 PLAY_ANIM`, `0x13 PUSH_ARMOR`.
  En-tête chunk : `opcode u8, protocol u8, transferId i64×2, chunkIndex u16, chunkCount u16, totalLength i32, chunkLength i32, payload`.
- **JSON** : `PUSH_RIG` (bones pivot/from/to/block + animations[]), `PUSH_ANIM` (sortie Gson de `Animation`),
  `PLAY_ANIM` (entity uuid + `hide`[] = UUID des BlockDisplay de fallback à masquer côté client).
- **API publique serveur** (consommée par `ModelEngineModule`, **ne pas casser**) : `hasCompanion`,
  `hasProtocolV2`, `sendRig`, `sendAnim/sendAnimation`, `playAnim`, `bindRig`, `pushArmor`.
- Côté client : rend un rig squelettique **maison** (pas de GeckoLib pour rester léger/optionnel), masque les
  BlockDisplay de fallback via mixin. Détails complets : `docs/HANDOFF-AI3.md`.

### 1.10 Modules d'économie/contenu (intégration IA n°2)

| Module | Stockage | Dépendances | Commandes / déclencheur |
|---|---|---|---|
| **ShopModule** | `shop.yml` (catégories→items, prix achat/vente) | `EconomyService`, `CustomItemManagerService` | `/moon shop`, `/moon adminshop` (rechargement) |
| **AuctionModule** | `auction.yml` + `auction_refunds.yml` (pas de SQL) | `EconomyService` | `/moon ah`, `/moon ahsell <prix>` — expiration auto **48 h**, remboursement au join si offline ; **anti-dupe** : retrait immédiat de l'item à la mise en vente |
| **SpawnerGuiModule** | état = bloc vanilla (pas de persistance) | `EconomyService` | **clic droit** sur un SPAWNER → GUI upgrade vitesse (5000$), quantité (10000$), récupération (1000$, perm `mooncore.spawner.mine`), changer type via œuf |

### 1.11 Blocs custom & boss (deltas vs baseline)

- **Blocs** : technique `note_block` (800 états, `BlockStateMap`) identique, mais `CustomBlockDef` enrichi :
  **faces séparées** (`textureTop/Side/Bottom`), worldgen complet (`generate, replace, minY/maxY, veinsPerChunk,
  veinSize`), **outil requis granulaire** (`requiredTool` ToolKind + `minToolTier` ToolTier), `breakDurability`
  (multi-coups par compteur), `blastResistance`.
- **Boss** : `BossDefinition` est une **classe/record séparé** (vs inline en baseline) avec **`equipment`**
  `Map<slot,itemId>` (équipement custom par slot), `textureKey` + `textureCustomModelData` (compat), `barColor`,
  `phases` (`BossPhase(name, fromPercent, abilities)`), `AbilityInstance(type, cooldownTicks, magnitude, count,
  radius)`. Éditeurs GUI : `BossEditorMenu`/`BossPhaseMenu`/`BossPhaseAbilitiesMenu`/`BossDropMenu`/
  `BossEquipmentMenu`/`BossModelMenu`.

### 1.12 Assistant IA & auto-update (deltas)

- **`AiConfig`** : multi-provider (`anthropic`/`openai`), **endpoint override** (texte ET image — ex. Stable
  Diffusion/Ollama local), génération de textures configurable (`generateTextures`, `imageModel`, `textureSize`,
  `texturePalette`, `textureDither`). Modèles par défaut : `claude-opus-4-8`, `claude-sonnet-4-6`,
  `claude-haiku-4-5-20251001` (Anthropic) ; `gpt-4o`, `gpt-4o-mini`, `gpt-4.1` (OpenAI).
- **`AiPrompts`** : schémas JSON stricts (item/boss/block/recipe/lore/code + **unified create**), plafonds
  d'équilibrage, capacités opt-in. **`AiActionValidator`** clampe et sécurise. **Audit** SQL.
- **`UpdateModule`** (NOUVEAU) : poll **GitHub Releases** (`mooncraftmooncraft602-cmyk/MoonCore`, intervalle 180 min),
  télécharge dans `Bukkit/update/`, tente un **hot-reload best-effort** (réflexion), commande `/moon plugins reinstall|check`.

### 1.13 Carte des classes (deltas par rapport à la baseline)

```
CustomItemFactory ── meta.setItemModel() + meta.setEquippable()     ← MODERNE
   ├─ ResourcePackBuilder (assets/mooncore/items/<key>.json item_model, pack_format 75)
   └─ EquipmentPackBuilder (assets/mooncore/models/equipment + textures/entity/equipment/*)   ← NOUVEAU
EndgameItemsModule ── ItemStack.setData(DataComponentTypes.GLIDER) via réflexion              ← NOUVEAU
ModelEngineModule ── RigInstance(BlockDisplay) + pushToCompanions() ─┐
CompanionModule (protocol v2 chunké) ◄──────────────────────────────┘  sendRig/sendAnim/playAnim/pushArmor
studio/ ── StudioPreview(ItemDisplay), StudioImport(auto-bind PNG→equipmentKey), BossEquipmentMenu   ← NOUVEAUX
shop/ auction/ spawner/ ── modules économie (IA n°2, Vault + YAML)                              ← NOUVEAUX
UpdateModule ── auto-update GitHub Releases + hot-reload                                        ← NOUVEAU
```

---

## 2. VISION — « ULTIMATE IN-GAME MOD CREATOR »

Identique à la baseline (MCreator intégré en jeu : créer **tout** — blocs, items, nourriture, outils, armures,
cultures, mécaniques, recettes, loot — via studio/commande/IA, sans recompiler). **Différence ici** : la ligne
1.21.11 a **déjà** posé deux briques majeures de cette vision — **item_model moderne** et **armures portées
equippable** — et démontré l'usage de la **DataComponent API** (GLIDER). Le levier reste **exploiter à fond les
Data Components 1.21.x**.

**Déjà acquis (1.21.11)** : item_model, equippable (armure sur le corps), glider, attribute_modifiers, enchantments.
**Reste à exploiter** : `minecraft:food`, `minecraft:consumable`, `minecraft:tool`, `minecraft:max_damage`,
`minecraft:jukebox_playable`, `minecraft:rarity`, etc. — via le pattern réflexion+fallback de `GLIDER`.

**Reste à construire** (comme baseline) : cultures/plantes à ticks, tables de loot génériques, recettes complètes
(shapeless/smithing/brewing), blocs avancés (lumière/interactifs/>800), mécaniques génériques trigger→action,
commande globale de création.

**Patron d'extensibilité** (à reproduire pour tout nouveau type) :
`Def → Store → Factory/Spawner → Listener → EditorMenu → schéma IA (AiPrompts + Validator)`.

---

## 3. VISION — « IN-GAME BLOCKBENCH »

Éditeur 3D + textures en jeu répliquant Blockbench. **Atouts déjà présents dans cette ligne** :
- moteur de rig display-entities + matrices + interpolation (`RigInstance`) ;
- keyframes + interpolation (`Animation`) ; import `.bbmodel` (`BlockBenchImporter`) ;
- **aperçu `ItemDisplay`** (`StudioPreview`) — base d'un viewport ;
- **pont mod client v2** (`CompanionModule`) : pousser rig/anim/armure et masquer le fallback → rendu HQ côté
  joueurs moddés (`sendRig`/`sendAnim`/`playAnim`/`pushArmor`) ;
- raytrace regard→texel du **paint editor** (`PaintRaytracer`, `PixelCanvas`, `PaintSession`).

**Stratégie technique** : éditer un `RigModel` en mémoire, le matérialiser via `RigInstance` (Display entities
orientables/scalables/teintables par `Transformation`/`Matrix4f`), peindre les UV via le paint editor, poser des
keyframes (le système d'anim existe), puis **exporter** vers `.bbmodel` (round-trip avec l'importeur) **et** vers
un modèle d'item JSON du resource pack (`elements` from/to/rotation/uv/faces) référencé par **`item_model`**.
Pour les clients moddés, pousser le tout via le **protocol v2** pour un rendu fluide.

**Restes (vs cible)** : os **texturés** (ItemDisplay vs BlockDisplay), **édition** géométrie/UV/keyframes en jeu,
**export** `.bbmodel`/modèle JSON. → **Étape D**.

---

## 4. PLAN D'ACTION — MODE BOUCLE 24 H (tâches atomiques)

> **Règle d'or** : une sous-tâche atomique par itération → `mvnw package` → `mvnw test` → commit nommé.
> ⚠️ **Cette ligne a déjà accompli une partie de la roadmap baseline** : les **Étapes B4/B5 sont FAITES**
> (equippable + item_model), la DataComponent API est **amorcée** (GLIDER). Le plan ci-dessous est **ajusté**.

### Ordre recommandé : **A → B(restant) → C → D → E**.

---

### ÉTAPE A — Stockage universel requêtable *(à faire — identique baseline)*

- **A1** `UniversalContentStore` : table `mooncore_content(content_type, id, data_json, schema_version, created_at,
  updated_at)` (plage migration 1400+), sérialisation **Gson**.
- **A2** Round-trip JSON de `CustomItemDef`/`CustomBlockDef`/`BossDefinition` (garder le YAML existant).
- **A3** Double-write `yaml|sql|both` derrière un flag config ; aucune suppression d'API.
- **A4** `/moon admin migrate-content` (idempotent) qui importe `items|blocks|bosses` dans `mooncore_content`.
- **A5** `schema_version` + hook `upgrade(json, fromVersion)` (backward-compat intra-objet).

**Fin A** : contenu requêtable en SQL, YAML toujours lisible, build+tests verts.

---

### ÉTAPE B — Exploiter LE RESTE des Data Components 1.21 *(B4/B5 déjà faits)*

Réutiliser le **pattern réflexion + fallback** d'`EndgameItemsModule.applyGliderComponent` pour chaque composant.

- **B1** Couche unique `ItemComponentApplier` : centralise tous les `meta.setData(...)`/`meta.setX(...)` à partir
  de `CustomItemDef`. Y déplacer l'existant (item_model, equippable, attributs, enchants, glider) **sans régression**.
- **B2** `food{nutrition, saturation, canAlwaysEat, eatSeconds}` → `minecraft:food` + `minecraft:consumable`
  (animation/son/effets/`use_remainder`). Migrer la conso « clic droit » vers le composant natif (garder le
  listener en fallback Bedrock).
- **B3** `tool{rules[], defaultMiningSpeed, damagePerBlock}` → `minecraft:tool` (relier à `ToolKind/ToolTier`).
- **B4** ✅ *FAIT* — `equippable` (armure portée). *Reste optionnel* : trims, fallback model, son d'équipement.
- **B5** ✅ *FAIT* — `item_model`. *Reste optionnel* : variantes par état (`select`/`condition` model types 1.21.4+).
- **B6** GUI : `FoodEditorMenu`, `ToolRulesMenu` (réutiliser le pattern `StudioMenu`), branchés dans `ItemEditorMenu`.
- **B7** Schémas IA : étendre `AiPrompts.itemSchemaSystem()` + `AiActionValidator` pour `food`/`tool`.

**Fin B** : créer en jeu une **nourriture** et un **outil** custom **natifs** (mangeable/règles de minage réelles),
via GUI **et** IA, l'armure et le modèle restant fonctionnels.

---

### ÉTAPE C — Cultures/plantes custom à cycle de ticks *(à faire — identique baseline)*

- **C1** `CropDef` (stages, growthTicks, lumière, conditions, drop, seed, replantable) → `UniversalContentStore` + YAML.
- **C2** Représentation des étapes (note_block par étape **ou** `ItemDisplay`/`BlockDisplay` posé — documenter le choix).
- **C3** `CropManagerModule` + persistance des **emplantations** (table `mooncore_crop_placement(world,x,y,z,crop_id,stage,planted_at)`).
- **C4** Tick **batché par chunk chargé** (pas de scan global), reprise au `ChunkLoad`.
- **C5** Gameplay : planter/récolter/replanter + protections ; `CropListener`.
- **C6** `CropEditorMenu` + schéma IA `cropSchemaSystem()` + génération pack.

**Fin C** : plante custom qui pousse en étapes, se récolte/replante, sans lag, persistée après reboot.

---

### ÉTAPE D — Éditeur 3D « Blockbench » in-game + export *(à faire — fondations déjà là)*

Profite du moteur `RigInstance`, de `StudioPreview` (ItemDisplay), du paint editor et du **protocol v2**.

- **D1** `EditableRig` (géométrie mutable : add/del cube, set from/to/pivot, UV par face). `RigBone` gagne un
  `itemModelKey` (option ItemDisplay texturé) en plus de `block`.
- **D2** `ModelEditorSession` : matérialise l'`EditableRig` via `RigInstance` et le re-pousse à chaque édition ;
  sélection de cube par **raytrace AABB** (extension de `PaintRaytracer`).
- **D3** Outils d'édition (hotbar, façon paint) : déplacer/scaler/tourner cube, déplacer pivot, dupliquer, supprimer ;
  sneak = pas fin ; undo/redo (`Deque` de snapshots, comme `PixelCanvas`).
- **D4** Texture/UV : intégrer `PaintSession` pour peindre par cube + mapping UV par face ; passer les os de
  `BlockDisplay` → **`ItemDisplay`** texturés (modèle JSON généré).
- **D5** Timeline d'animation : `AnimationEditorMenu` (add/move/delete keyframes par os/canal) + preview live ;
  pousser via **protocol v2** aux clients moddés (`sendRig`/`sendAnim`/`playAnim`).
- **D6** **Export** (cœur) : `BlockBenchExporter.toBbmodel(EditableRig)` (round-trip) + `RigToItemModel`
  (modèle d'item JSON `elements`, référencé par **`item_model`**, intégré au `PackAssembler` + rebuild).
- **D7** Persistance `.bbmodel` dans `models/`, association contenu→modèle, lien `BossModelMenu` + item `item_model`.

**Fin D** : créer un modèle 3D simple **en jeu**, le voir sur un boss (rig) **et** sur un item tenu (item_model),
l'exporter en `.bbmodel`, et — pour les joueurs moddés — le voir en rendu HQ via le mod compagnon.

---

### ÉTAPE E — Commande globale de création *(à faire — identique baseline)*

- **E1** `/moon create <type> <args|description>` (`item|block|crop|boss|mob|recipe|loot|model|mechanic`) →
  NL vers IA (`AiPrompts` unifié), JSON/args vers les `*Validator`.
- **E2** `/moon edit <type> <id>` (dispatch vers le bon `EditorMenu`).
- **E3** `list|info|delete|clone|give <type> <id>` génériques via `ContentTypeRegistry`.
- **E4** `ContentTypeRegistry` : chaque type déclare `{def, store, factory, editor, ai schema, validator}`.
- **E5** Unified IA multi-créations chaînées (« minerai + sa pioche + sa recette + son boss »).
- **E6** Confirmations destructives, audit, perms `mooncore.admin.create.*`, **dry-run** + rebuild.

**Fin E** : créer/éditer/supprimer n'importe quel type depuis une commande ou une phrase à l'IA.

---

## 5. ANNEXES

### 5.1 Conventions (vérifiées)

- Ids `slug()` `[a-z0-9_-]`, max 48. Texte **MiniMessage** (`Text.mm`), italique off sur les noms.
- I/O DB async ; effets-monde sur main thread. Permissions `mooncore.*` ; bypass `mooncore.bypass.*`.
- Migrations : numéros uniques (core 1–99, IA 1300–1399, proposer 1400+ pour `mooncore_content`).
- Après modif de définition/texture : `ResourcePackService.rebuild()` + `resendAll()` (URL `?v=<sha1>`).
- **Composants modernes** : pattern **réflexion + try/catch** (comme `GLIDER`) si le composant peut manquer.

### 5.2 Pièges connus

- **Build séparé** de la baseline : ne pas écraser le mauvais `target/`. Cette ligne = 1.21.11, v2.0.0.
- `pack_format: 75` (+ `supported_formats` 64–99) ; URL versionnée SHA-1 obligatoire pour le refresh client.
- item_model / equippable **non rendus sur Bedrock** (Geyser) → fallback lore/PDC ; gameplay identique (serveur-side).
- Rig = **BlockDisplay** (blocky) ; le rendu HQ dépend du **mod client v2** (`hasProtocolV2`).
- Chemin à espaces ; artefacts `javac.*.args` ignorés.
- Blocs custom plafonnés à **800** états note_block.
- **API `CompanionModule`** consommée par le mod client : ne pas casser ses signatures (cf. `docs/HANDOFF-AI3.md`).

### 5.3 Checklist anti-régression (chaque itération)

1. `mvnw -q -DskipTests package` → vert. 2. `mvnw -q test` → vert. 3. `grep` des usages avant rename/suppression
d'API publique (`ServiceRegistry`, modules, **`CompanionModule`**). 4. Nouveau contenu = data-driven + GUI + schéma IA.
5. I/O async ; effets-monde main thread. 6. Modif déf/texture → rebuild+resend testés. 7. Handoff si intégration
par une autre IA. 8. Commit `vX.Y.Z — résumé FR` (brancher si sur `main`).

### 5.4 Modèles IA (au 2026-06)

Claude récents : **Fable 5** (`claude-fable-5`), **Opus 4.8** (`claude-opus-4-8`), **Sonnet 4.6**
(`claude-sonnet-4-6`), **Haiku 4.5** (`claude-haiku-4-5-20251001`). Textures locales sans clé API via **MoonTex**
(Stable Diffusion + LoRA / Ollama `mooncore-items`), branché par `image-endpoint` de `ai-assistant.yml`.

### 5.5 Fichiers à lire en priorité

| Sujet | Fichiers (ligne 1.21.11) |
|---|---|
| Bootstrap | `MoonCore.java`, `data/DataManager.java`, `data/Database.java` |
| **Item moderne** | `customitem/CustomItemFactory.java`, `CustomItemDef.java`, `ResourcePackBuilder.java`, `EquipmentPackBuilder.java` |
| **DataComponents** | `enditems/EndgameItemsModule.java` (pattern GLIDER) |
| **3D / preview** | `model/RigInstance.java`, `RigModel.java`, `Animation.java`, `BlockBenchImporter.java`, `ModelEngineModule.java`, `studio/StudioPreview.java` |
| **Mod client** | `companion/CompanionModule.java`, `docs/HANDOFF-AI3.md`, `companion-mod/` |
| **Studio avancé** | `studio/StudioImport.java`, `StudioPreview.java`, `BossEquipmentMenu.java`, `StudioTextureMenu.java` |
| **Économie (IA n°2)** | `shop/*`, `auction/*`, `spawner/*`, `docs/HANDOFF-AI2.md` |
| Blocs / Boss | `customblock/*`, `boss/BossDefinition.java`, `boss/BossManagerModule.java` |
| Paint | `customitem/paint/*` (PixelCanvas, PaintSession, MapCanvasRenderer, PaintRaytracer) |
| IA / Update | `ai/AiPrompts.java`, `ai/AiConfig.java`, `ai/AiActionValidator.java`, `update/UpdateModule.java` |
| Docs | `docs/ARCHITECTURE.md`, `TODO.md`, `RISKS.md`, `HANDOFF-AI2.md`, `HANDOFF-AI3.md` |

---

> **Fin du PROJECT_MOON_MASTER_BRAIN (ligne 1.21.11 / v2.0.0).** Si tu es le Claude en boucle : §0.6 (discipline),
> puis §4 Étape A1 — sauf si tu attaques l'éditeur 3D (Étape D), dont les fondations sont déjà solides ici.
> Une sous-tâche → build vert → test vert → commit. Bonne boucle. 🌙
