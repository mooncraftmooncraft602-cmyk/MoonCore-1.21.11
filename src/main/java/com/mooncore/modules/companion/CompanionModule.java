package com.mooncore.modules.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.model.Animation;
import com.mooncore.modules.model.RigBone;
import com.mooncore.modules.model.RigModel;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.joml.Vector3f;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge vers le mod client optionnel MoonCore Companion.
 *
 * <p>Le protocole v1 HELLO/WELCOME reste volontairement minimal. Le protocole v2 ajoute des
 * messages serveur -> client chunkes pour pousser des rigs, animations, commandes de lecture et
 * textures d'armure sans depasser la limite vanilla des plugin-messages.</p>
 */
@ModuleInfo(id = "companion", name = "CompanionBridge")
public final class CompanionModule extends AbstractModule implements Listener, PluginMessageListener {

    /** Plugin-message channel shared with the Fabric companion mod. */
    public static final String CHANNEL = "mooncore:companion";

    public static final byte OP_HELLO = 0x01;      // client -> server
    public static final byte OP_WELCOME = 0x02;    // server -> client
    public static final byte OP_PUSH_RIG = 0x10;   // server -> client, chunked JSON
    public static final byte OP_PUSH_ANIM = 0x11;  // server -> client, chunked JSON
    public static final byte OP_PLAY_ANIM = 0x12;  // server -> client, chunked JSON
    public static final byte OP_PUSH_ARMOR = 0x13; // server -> client, chunked JSON

    public static final byte PROTOCOL_V1 = 1;
    public static final byte PROTOCOL_V2 = 2;
    public static final byte PROTOCOL = PROTOCOL_V2;

    private static final byte CAP_STUDIO_2D = 0x01;
    private static final byte CAP_MODELS_3D = 0x02;
    private static final byte CAP_ENTITY = 0x04;
    private static final byte CAP_PROTOCOL_V2 = 0x08;
    private static final byte CAP_CHUNKING = 0x10;
    private static final byte CAP_ARMOR = 0x20;
    private static final byte LEGACY_CAPABILITIES = CAP_STUDIO_2D | CAP_MODELS_3D | CAP_ENTITY;
    private static final byte CAPABILITIES = LEGACY_CAPABILITIES | CAP_PROTOCOL_V2 | CAP_CHUNKING | CAP_ARMOR;

    private static final Gson GSON = new Gson();
    private static final int MAX_CHUNK_PAYLOAD_BYTES = 28 * 1024;

    private final Set<UUID> companions = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> protocols = new ConcurrentHashMap<>();
    private boolean enabled;

    @Override
    protected void onEnable() {
        this.enabled = moduleConfig().getBoolean("enabled", true);
        if (!enabled) {
            log().info("[Companion] Desactive par config.");
            return;
        }
        var messenger = plugin().getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin(), CHANNEL);
        messenger.registerIncomingPluginChannel(plugin(), CHANNEL, this);
        registerListener(this);
        log().info("[Companion] Pont mod client pret (canal " + CHANNEL + "). Mod facultatif (Java only).");
    }

    @Override
    protected void onDisable() {
        if (!enabled) return;
        var messenger = plugin().getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin(), CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin(), CHANNEL);
        companions.clear();
        protocols.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
    }

    /** True if this player installed the MoonCore Companion client mod and completed HELLO. */
    public boolean hasCompanion(UUID uuid) {
        return uuid != null && companions.contains(uuid);
    }

    public boolean hasCompanion(Player p) {
        return p != null && companions.contains(p.getUniqueId());
    }

    /** True if the client announced protocol v2 and can receive chunked model payloads. */
    public boolean hasProtocolV2(Player p) {
        return p != null && protocols.getOrDefault(p.getUniqueId(), 0) >= PROTOCOL_V2;
    }

    public int companionCount() {
        return companions.size();
    }

    // ---------------------------------------------------------------------
    // Public v2 API for ModelEngineModule / other server modules
    // ---------------------------------------------------------------------

    /** Pushes a v2 rig JSON payload. No-op for vanilla players and v1 companion clients. */
    public void sendRig(Player player, String rigJson) {
        sendJson(player, OP_PUSH_RIG, rigJson);
    }

    /** Serializes and pushes only the RigModel geometry/bone hierarchy. Animations are separate. */
    public void sendRig(Player player, RigModel model) {
        if (model == null) return;
        sendRig(player, serializeRig(model, model.id));
    }

    /** Same as {@link #sendRig(Player, RigModel)} but lets the caller override the wire rig id. */
    public void sendRig(Player player, String rigId, RigModel model) {
        if (model == null) return;
        sendRig(player, serializeRig(model, rigId));
    }

    /** Pushes a v2 animation JSON payload. No-op for vanilla players and v1 companion clients. */
    public void sendAnim(Player player, String animJson) {
        sendJson(player, OP_PUSH_ANIM, animJson);
    }

    /** Alias kept readable for call sites that prefer the full word. */
    public void sendAnimation(Player player, String animJson) {
        sendAnim(player, animJson);
    }

    /** Serializes and pushes one Animation under a rig id. */
    public void sendAnim(Player player, String rigId, Animation animation) {
        if (animation == null) return;
        sendAnim(player, serializeAnimation(rigId, animation));
    }

    /** Alias kept readable for call sites that prefer the full word. */
    public void sendAnimation(Player player, String rigId, Animation animation) {
        sendAnim(player, rigId, animation);
    }

    /**
     * Plays an animation for an entity UUID. The client uses the entity's current position as the
     * rig anchor. If no rig is supplied, the client reuses the previous binding for that entity.
     */
    public void playAnim(Player player, UUID entityUuid, String animation) {
        playAnim(player, entityUuid, null, animation, true, null);
    }

    /** Plays an animation and binds the entity to a rig id if needed. */
    public void playAnim(Player player, UUID entityUuid, String rigId, String animation) {
        playAnim(player, entityUuid, rigId, animation, true, null);
    }

    /**
     * Plays/binds a client-side rig and optionally hides the vanilla BlockDisplay bones by UUID.
     *
     * @param loop true for a base looping animation, false for a one-shot animation.
     * @param vanillaBoneUuids BlockDisplay entity UUIDs rendered by the vanilla fallback rig.
     */
    public void playAnim(Player player, UUID entityUuid, String rigId, String animation,
                         boolean loop, Collection<UUID> vanillaBoneUuids) {
        if (entityUuid == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("schema", 2);
        json.addProperty("type", "play");
        json.addProperty("entity", entityUuid.toString());
        if (rigId != null && !rigId.isBlank()) json.addProperty("rig", rigId);
        if (animation != null && !animation.isBlank()) json.addProperty("animation", animation);
        json.addProperty("loop", loop);
        if (vanillaBoneUuids != null && !vanillaBoneUuids.isEmpty()) {
            JsonArray hide = new JsonArray();
            for (UUID uuid : vanillaBoneUuids) if (uuid != null) hide.add(uuid.toString());
            json.add("hide", hide);
        }
        sendJson(player, OP_PLAY_ANIM, json.toString());
    }

    /** Binds a rig to an entity without changing animation, and can hide vanilla bone displays. */
    public void bindRig(Player player, UUID entityUuid, String rigId, Collection<UUID> vanillaBoneUuids) {
        playAnim(player, entityUuid, rigId, null, true, vanillaBoneUuids);
    }

    /** Pushes armor/skin texture metadata JSON. */
    public void pushArmor(Player player, String armorJson) {
        sendJson(player, OP_PUSH_ARMOR, armorJson);
    }

    /** Pushes armor/skin metadata and tags it with an entity UUID. */
    public void pushArmor(Player player, UUID entityUuid, String armorJson) {
        if (entityUuid == null || armorJson == null || armorJson.isBlank()) return;
        JsonObject json = new JsonObject();
        json.addProperty("schema", 2);
        json.addProperty("type", "armor");
        json.addProperty("entity", entityUuid.toString());
        try {
            json.add("payload", GSON.fromJson(armorJson, JsonObject.class));
        } catch (Exception ignored) {
            json.addProperty("payload", armorJson);
        }
        pushArmor(player, json.toString());
    }

    // ---------------------------------------------------------------------
    // Incoming v1 handshake
    // ---------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length < 1) return;
        if (message[0] == OP_HELLO) {
            int proto = message.length > 1 ? (message[1] & 0xFF) : PROTOCOL_V1;
            int negotiated = Math.max(PROTOCOL_V1, Math.min(proto, PROTOCOL_V2));
            boolean isNew = companions.add(player.getUniqueId());
            protocols.put(player.getUniqueId(), negotiated);
            sendWelcome(player, negotiated);
            if (isNew) {
                log().info("[Companion] " + player.getName() + " utilise le mod client (protocole "
                        + negotiated + ", annonce " + proto + ").");
                player.sendMessage(Text.mm("<gradient:#8a2be2:#c77dff>MoonCore Companion</gradient> <green>detecte "
                        + "- fonctionnalites Java avancees activees."));
            }
        }
    }

    private void sendWelcome(Player player, int protocol) {
        byte proto = (byte) (protocol >= PROTOCOL_V2 ? PROTOCOL_V2 : PROTOCOL_V1);
        byte capabilities = protocol >= PROTOCOL_V2 ? CAPABILITIES : LEGACY_CAPABILITIES;
        byte[] data = {OP_WELCOME, proto, capabilities};
        try {
            player.sendPluginMessage(plugin(), CHANNEL, data);
        } catch (Throwable t) {
            log().warn("[Companion] Envoi WELCOME echoue a " + player.getName() + " : " + t.getMessage());
        }
    }

    /** Sends a raw payload to a companion client. Kept for v1 compatibility and diagnostics. */
    public void send(Player player, byte[] data) {
        if (player == null || data == null || !hasCompanion(player)) return;
        try {
            player.sendPluginMessage(plugin(), CHANNEL, data);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        companions.remove(e.getPlayer().getUniqueId());
        protocols.remove(e.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------------
    // Chunked v2 transport
    // ---------------------------------------------------------------------

    private void sendJson(Player player, byte opcode, String json) {
        if (json == null || json.isBlank()) return;
        sendChunked(player, opcode, json.getBytes(StandardCharsets.UTF_8));
    }

    private void sendChunked(Player player, byte opcode, byte[] payload) {
        if (player == null || payload == null || !hasProtocolV2(player)) return;
        UUID transferId = UUID.randomUUID();
        int chunkCount = Math.max(1, (payload.length + MAX_CHUNK_PAYLOAD_BYTES - 1) / MAX_CHUNK_PAYLOAD_BYTES);
        if (chunkCount > 0xFFFF) {
            log().warn("[Companion] Payload trop gros pour " + player.getName() + " (" + payload.length + " octets).");
            return;
        }
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int start = chunkIndex * MAX_CHUNK_PAYLOAD_BYTES;
            int len = Math.min(MAX_CHUNK_PAYLOAD_BYTES, payload.length - start);
            byte[] packet = encodeChunk(opcode, transferId, chunkIndex, chunkCount, payload.length, payload, start, len);
            send(player, packet);
        }
    }

    private byte[] encodeChunk(byte opcode, UUID transferId, int chunkIndex, int chunkCount,
                               int totalLength, byte[] payload, int offset, int length) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 32);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(opcode);
            out.writeByte(PROTOCOL_V2);
            out.writeLong(transferId.getMostSignificantBits());
            out.writeLong(transferId.getLeastSignificantBits());
            out.writeShort(chunkIndex);
            out.writeShort(chunkCount);
            out.writeInt(totalLength);
            out.writeInt(length);
            out.write(payload, offset, length);
            out.flush();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode companion chunk", e);
        }
    }

    private String serializeRig(RigModel model, String rigId) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("type", "rig");
        root.addProperty("rig", rigId == null || rigId.isBlank() ? model.id : rigId);
        JsonArray bones = new JsonArray();
        for (RigBone bone : model.bones) {
            JsonObject b = new JsonObject();
            b.addProperty("name", bone.name);
            if (bone.parent != null) b.addProperty("parent", bone.parent);
            b.add("pivot", vector(bone.pivot));
            b.add("from", vector(bone.from));
            b.add("to", vector(bone.to));
            if (bone.block != null) b.addProperty("block", bone.block.getAsString());
            bones.add(b);
        }
        root.add("bones", bones);
        JsonArray anims = new JsonArray();
        for (String name : model.animations.keySet()) anims.add(name);
        root.add("animations", anims);
        return root.toString();
    }

    private String serializeAnimation(String rigId, Animation animation) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("type", "animation");
        if (rigId != null && !rigId.isBlank()) root.addProperty("rig", rigId);
        root.addProperty("name", animation.name());
        root.add("animation", GSON.toJsonTree(animation));
        return root.toString();
    }

    private JsonArray vector(Vector3f v) {
        JsonArray out = new JsonArray();
        out.add(v.x);
        out.add(v.y);
        out.add(v.z);
        return out;
    }
}
