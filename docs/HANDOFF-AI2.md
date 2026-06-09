# Instructions d'Intégration (Handoff AI #2 -> AI #1)

Les modules `ShopModule`, `AuctionModule` et `SpawnerGuiModule` ont été implémentés avec succès et compilent correctement.

Voici les instructions à suivre pour finaliser leur intégration dans le plugin.

## 1. Enregistrement des modules
Ouvre le fichier `MoonCore.java` et ajoute les lignes suivantes dans la méthode d'initialisation des modules (`moduleManager.register(...)`) :

```java
moduleManager.register(new com.mooncore.modules.shop.ShopModule());
moduleManager.register(new com.mooncore.modules.auction.AuctionModule());
moduleManager.register(new com.mooncore.modules.spawner.SpawnerGuiModule());
```

## 2. Nœuds de permission
Ouvre le fichier `plugin.yml` et ajoute les nœuds de permission suivants dans la section `permissions:` :

```yaml
  mooncore.shop.use:
    description: Permet d'ouvrir le shop
    default: true
  mooncore.admin.shop:
    description: Permet de recharger la configuration du shop
    default: op
  mooncore.ah.use:
    description: Permet d'utiliser l'hôtel des ventes
    default: true
  mooncore.spawner.use:
    description: Permet d'ouvrir l'interface de gestion d'un spawner (clic-droit)
    default: true
  mooncore.spawner.mine:
    description: Permet de récupérer un spawner via l'interface
    default: op
```

## 3. Dépendances et Notes
- Ces 3 modules dépendent implicitement de `EconomyService` (qui wrappe Vault). `ShopModule` et `AuctionModule` s'intègrent aussi avec `CustomItemManagerService` pour supporter les objets custom.
- **Attention anti-dupe (Auction)** : L'AH retire directement l'objet de l'inventaire lors du `/moon ahsell <prix>` et sauvegarde asynchronement. Les objets expirés sont restitués au joueur lors de sa prochaine connexion (via un `PlayerJoinEvent`).
- **Configuration** : Un fichier `modules/shop.yml` par défaut a été créé dans les ressources, il sera extrait lors du premier lancement. L'AH utilise `auction.yml` et `auction_refunds.yml` (créés dynamiquement dans le data folder) pour persister les données.
- **Aucune base de données SQL** n'est requise pour l'AH par soucis de simplicité et de fiabilité comme décidé.

Merci et bon build !
