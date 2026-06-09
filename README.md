# MoonCore — ligne avancée **1.21.11**

Build **avancé** de MoonCore ciblant **Paper 1.21.11** (Java 21). Dépôt distinct du build baseline 1.21.1 (`MoonCore`). Tout ce qu'a la 1.21.1 **plus** :

## Nouveautés de cette ligne
- **Studio de création** : armures **portées custom** (composant `equippable`), système **item-model moderne** (`assets/mooncore/items/<id>.json`, clés string, zéro collision), `pack_format` 75.
- **Moteur de modèles/animations MAISON** (`com.mooncore.modules.model`, sans dépendance externe) : import **BlockBench** (`.bbmodel` → géométrie + animations), rigs en display-entities animés, **mobs/boss à modèle 3D custom** (`/moon studio rig`, `/moon studio bossrig`).
- **Création facile** : `/moon studio import` (glisser-déposer de PNG), aperçu live (`/moon studio preview`), rebuild de pack débouncé.
- **Features ex-plugins réintégrées en modules** : EnderChest, Vanish, RTP, OnePlayerSleep, ClearLag, PvPManager, PlayerHeads, Essentials (heal/feed/fly/god/repair/hat/near/workbench/back), Messagerie (`/msg`,`/reply`), Warps, Kits.
- **Commandes utilisables sans `/moon`** : `/ec`, `/heal`, `/warp`, `/msg`, `/rtp`, `/home`, `/spawn`… (`/moon …` reste valable).
- **Integrations** : MoonCore se *branche* sur les plugins déjà installés (WorldGuard, CoreProtect, Vault, LuckPerms, Geyser…) au lieu de les réécrire.

## Build

```bash
mvnw.cmd clean package      # Windows (wrapper ; télécharge Maven 3.9.9 au 1er run)
./mvnw clean package        # Linux/macOS
```

> Si le chemin du projet contient des espaces et que `mvnw.cmd` échoue, lancer le wrapper directement :
> `java "-Dmaven.multiModuleProjectDirectory=%CD%" -classpath ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain clean package`

Jar final : `target/MoonCore-2.0.0-mc1.21.11.jar` (dépendances embarquées + relocalisées).

## Installation

1. Serveur **Paper 1.21.11**. Déposer le jar dans `plugins/`.
2. Démarrer une fois (config + `lang/fr.yml` créés). Backend **SQLite** par défaut (aucun serveur SQL requis).
3. (Optionnel) Garder Vault/PlaceholderAPI/WorldGuard/CoreProtect… : MoonCore les détecte et s'y branche.
4. Redémarrer.

## État

Build **vert**, 80 tests. Plugin de saison complet (progression, events, boss, zones, anti-farm, économie, enchants, endgame) + studio de contenu (items/blocs/mobs/boss/armures custom) + suite QoL de type Essentials.
