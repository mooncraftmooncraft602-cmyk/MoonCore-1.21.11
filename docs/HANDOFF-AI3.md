# Handoff AI3 - MoonCore Companion protocol v2

Zone modifiee par AI3:

- `src/main/java/com/mooncore/modules/companion/CompanionModule.java`
- `companion-mod/`

Le mod reste optionnel. Sans mod client, ou avec un client v1, les nouvelles methodes v2 sont des
no-op cote serveur.

## Handshake

Canal plugin-message: `mooncore:companion`.

v1 reste supporte:

- client -> serveur `HELLO`: `[0x01, protocol]`
- serveur -> client `WELCOME`: `[0x02, negotiatedProtocol, capabilities]`

Le serveur negocie `protocol=2` si le client annonce v2. Sinon il repond en v1 avec les anciennes
capacites `0x01 | 0x02 | 0x04`.

Capacites v2:

- `0x01` studio 2D
- `0x02` modeles 3D
- `0x04` entites custom
- `0x08` protocole v2
- `0x10` chunking
- `0x20` armor metadata

## Messages v2

Tous les messages v2 serveur -> client sont chunkes. Le premier octet reste l'opcode logique:

- `0x10 PUSH_RIG`
- `0x11 PUSH_ANIM`
- `0x12 PLAY_ANIM`
- `0x13 PUSH_ARMOR`

Header de chaque chunk:

```text
opcode u8
protocol u8 (=2)
transferId mostSigBits i64
transferId leastSigBits i64
chunkIndex u16
chunkCount u16
totalLength i32
chunkLength i32
payload bytes[chunkLength]
```

Le serveur coupe a 28 KiB de payload par message pour rester sous la limite vanilla des
plugin-messages.

## JSON attendu

`PUSH_RIG`:

```json
{
  "schema": 2,
  "type": "rig",
  "rig": "golem",
  "bones": [
    {
      "name": "body",
      "parent": null,
      "pivot": [0.0, 0.625, 0.0],
      "from": [-0.25, 0.625, -0.125],
      "to": [0.25, 1.375, 0.125],
      "block": "minecraft:iron_block"
    }
  ],
  "animations": ["idle", "walk"]
}
```

`PUSH_ANIM`:

```json
{
  "schema": 2,
  "type": "animation",
  "rig": "golem",
  "name": "walk",
  "animation": {
    "name": "walk",
    "length": 1.0,
    "loop": true,
    "tracks": {
      "leg_r": [
        {
          "time": 0.0,
          "translation": [0.0, 0.0, 0.0],
          "rotationDeg": [30.0, 0.0, 0.0],
          "scale": [1.0, 1.0, 1.0]
        }
      ]
    }
  }
}
```

Le client accepte aussi les `Vector3f` serialises en objet `{ "x": 0, "y": 0, "z": 0 }`, ce qui
permet d'utiliser directement la sortie Gson de `Animation`.

`PLAY_ANIM`:

```json
{
  "schema": 2,
  "type": "play",
  "entity": "00000000-0000-0000-0000-000000000000",
  "rig": "golem",
  "animation": "walk",
  "loop": true,
  "hide": [
    "11111111-1111-1111-1111-111111111111"
  ]
}
```

`hide` contient les UUID des `BlockDisplay` de fallback vanilla a masquer pour ce joueur. Le client
annule le rendu uniquement si l'entite est un `DisplayEntity.BlockDisplayEntity` et que son UUID est
dans cette liste.

`PUSH_ARMOR`:

```json
{
  "schema": 2,
  "type": "armor",
  "entity": "00000000-0000-0000-0000-000000000000",
  "payload": {
    "helmet": "mooncore:armor/my_helmet",
    "chestplate": "mooncore:armor/my_chestplate"
  }
}
```

Le client stocke ce payload. Le rendu d'armure texturee peut s'appuyer dessus dans une prochaine
iteration.

## API publique cote serveur

Classe: `com.mooncore.modules.companion.CompanionModule`.

Disponibilite:

- `hasCompanion(Player)` / `hasCompanion(UUID)`
- `hasProtocolV2(Player)`
- `companionCount()`

Envoi rig/animation:

- `sendRig(Player player, String rigJson)`
- `sendRig(Player player, RigModel model)`
- `sendRig(Player player, String rigId, RigModel model)`
- `sendAnim(Player player, String animJson)`
- `sendAnimation(Player player, String animJson)`
- `sendAnim(Player player, String rigId, Animation animation)`
- `sendAnimation(Player player, String rigId, Animation animation)`

Lecture et binding:

- `playAnim(Player player, UUID entityUuid, String animation)`
- `playAnim(Player player, UUID entityUuid, String rigId, String animation)`
- `playAnim(Player player, UUID entityUuid, String rigId, String animation, boolean loop, Collection<UUID> vanillaBoneUuids)`
- `bindRig(Player player, UUID entityUuid, String rigId, Collection<UUID> vanillaBoneUuids)`

Armor:

- `pushArmor(Player player, String armorJson)`
- `pushArmor(Player player, UUID entityUuid, String armorJson)`

## Branchement conseille pour AI1

Quand `ModelEngineModule` spawn un rig vanilla pour un joueur qui a le mod:

1. Recuperer `CompanionModule`.
2. Tester `hasProtocolV2(player)`.
3. Envoyer le rig une fois: `sendRig(player, rigModel)`.
4. Envoyer chaque animation: `sendAnim(player, rigModel.id, animation)`.
5. Binder ou jouer:
   `playAnim(player, hostEntity.getUniqueId(), rigModel.id, defaultAnim, true, blockDisplayUuids)`.

`blockDisplayUuids` doit etre la liste des UUID des `BlockDisplay` crees par `RigInstance` pour le
fallback vanilla. Sans cette liste, le client rendra le rig high-tier mais ne pourra pas masquer le
fallback vanilla.

## Cote client

Le mod Fabric:

- envoie `HELLO` v2;
- recoit et reassemble les chunks;
- stocke rigs, animations et armor metadata;
- rend un rig squelettique maison en world render event;
- masque les `BlockDisplay` de fallback via mixin si leurs UUIDs sont fournis.

GeckoLib n'est pas embarque dans cette iteration: le rendu est maison pour eviter une nouvelle
dependance et garder le mod optionnel/leger.
