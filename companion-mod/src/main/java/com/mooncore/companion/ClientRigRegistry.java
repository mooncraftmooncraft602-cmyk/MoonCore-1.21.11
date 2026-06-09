package com.mooncore.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientRigRegistry {

    private static final Map<String, RigDefinition> RIGS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, AnimationDefinition>> ANIMATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, RigBinding> BINDINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, JsonObject> ARMOR = new ConcurrentHashMap<>();
    private static final Collection<UUID> HIDDEN_BONES = ConcurrentHashMap.newKeySet();

    private ClientRigRegistry() {
    }

    static void accept(byte opcode, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            switch (opcode) {
                case CompanionProtocol.OP_PUSH_RIG -> pushRig(root);
                case CompanionProtocol.OP_PUSH_ANIM -> pushAnimation(root);
                case CompanionProtocol.OP_PLAY_ANIM -> playAnimation(root);
                case CompanionProtocol.OP_PUSH_ARMOR -> pushArmor(root);
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    static void clear() {
        RIGS.clear();
        ANIMATIONS.clear();
        BINDINGS.clear();
        ARMOR.clear();
        HIDDEN_BONES.clear();
    }

    static Collection<RigBinding> bindings() {
        return List.copyOf(BINDINGS.values());
    }

    static RigDefinition rig(String id) {
        return id == null ? null : RIGS.get(id);
    }

    static AnimationDefinition animation(String rigId, String animation) {
        Map<String, AnimationDefinition> byRig = rigId == null ? null : ANIMATIONS.get(rigId);
        return byRig == null || animation == null ? null : byRig.get(animation);
    }

    public static boolean shouldHideVanillaBone(Entity entity) {
        return entity instanceof DisplayEntity.BlockDisplayEntity && HIDDEN_BONES.contains(entity.getUuid());
    }

    private static void pushRig(JsonObject root) {
        String rigId = string(root, "rig", string(root, "id", "rig"));
        JsonArray bonesJson = array(root, "bones");
        if (bonesJson == null) return;
        List<BoneDefinition> bones = new ArrayList<>();
        for (JsonElement element : bonesJson) {
            if (!element.isJsonObject()) continue;
            JsonObject b = element.getAsJsonObject();
            String name = string(b, "name", null);
            if (name == null || name.isBlank()) continue;
            String parent = string(b, "parent", null);
            Vec3 pivot = vec(b.get("pivot"), Vec3.ZERO);
            Vec3 from = vec(b.get("from"), Vec3.ZERO);
            Vec3 to = vec(b.get("to"), from);
            String block = string(b, "block", "");
            bones.add(new BoneDefinition(name, parent, pivot, from, to, block, colorForBlock(block)));
        }
        RIGS.put(rigId, new RigDefinition(rigId, bones));
    }

    private static void pushAnimation(JsonObject root) {
        JsonObject anim = root.has("animation") && root.get("animation").isJsonObject()
                ? root.getAsJsonObject("animation") : root;
        String rigId = string(root, "rig", string(anim, "rig", firstRigId()));
        if (rigId == null) return;
        String name = string(root, "name", string(anim, "name", null));
        if (name == null || name.isBlank()) return;
        double length = number(anim, "length", number(root, "length", 1.0));
        boolean loop = bool(anim, "loop", bool(root, "loop", true));
        JsonObject tracksJson = object(anim, "tracks");
        if (tracksJson == null) tracksJson = object(root, "tracks");
        if (tracksJson == null) return;

        Map<String, List<Keyframe>> tracks = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : tracksJson.entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            List<Keyframe> keyframes = new ArrayList<>();
            for (JsonElement kfEl : entry.getValue().getAsJsonArray()) {
                if (!kfEl.isJsonObject()) continue;
                JsonObject kf = kfEl.getAsJsonObject();
                keyframes.add(new Keyframe(
                        number(kf, "time", 0.0),
                        vec(first(kf, "translation", "position", "translate"), Vec3.ZERO),
                        vec(first(kf, "rotationDeg", "rotation", "rot"), Vec3.ZERO),
                        vec(kf.get("scale"), Vec3.ONE)));
            }
            keyframes.sort(Comparator.comparingDouble(Keyframe::time));
            if (!keyframes.isEmpty()) tracks.put(entry.getKey(), keyframes);
        }
        ANIMATIONS.computeIfAbsent(rigId, ignored -> new ConcurrentHashMap<>())
                .put(name, new AnimationDefinition(name, Math.max(0.0001, length), loop, tracks));
    }

    private static void playAnimation(JsonObject root) {
        UUID entity = uuid(string(root, "entity", null));
        if (entity == null) return;
        String rigId = string(root, "rig", null);
        String animation = string(root, "animation", null);
        boolean loop = bool(root, "loop", true);
        addHidden(root.get("hide"));

        RigBinding current = BINDINGS.get(entity);
        if (rigId == null && current != null) rigId = current.rigId;
        if (rigId == null) rigId = firstRigId();
        if (rigId == null || !RIGS.containsKey(rigId)) return;
        if (animation == null && current != null) animation = current.animationName;

        RigBinding binding = new RigBinding(entity, rigId, animation, loop, System.nanoTime());
        BINDINGS.put(entity, binding);
    }

    private static void pushArmor(JsonObject root) {
        UUID entity = uuid(string(root, "entity", null));
        if (entity != null) ARMOR.put(entity, root);
    }

    private static void addHidden(JsonElement element) {
        if (element == null || !element.isJsonArray()) return;
        for (JsonElement e : element.getAsJsonArray()) {
            UUID uuid = uuid(e.isJsonPrimitive() ? e.getAsString() : null);
            if (uuid != null) HIDDEN_BONES.add(uuid);
        }
    }

    private static String firstRigId() {
        return RIGS.keySet().stream().findFirst().orElse(null);
    }

    private static int colorForBlock(String block) {
        String key = block == null ? "" : block.toLowerCase(Locale.ROOT);
        if (key.contains("gold")) return 0xFFE0A336;
        if (key.contains("copper")) return 0xFFB86F43;
        if (key.contains("iron")) return 0xFFC8D0D7;
        if (key.contains("diamond")) return 0xFF61D6D6;
        if (key.contains("emerald")) return 0xFF4DD17D;
        int hash = key.hashCode();
        int r = 90 + Math.abs(hash & 0x7F);
        int g = 90 + Math.abs((hash >> 8) & 0x7F);
        int b = 90 + Math.abs((hash >> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static JsonElement first(JsonObject object, String... keys) {
        for (String key : keys) if (object.has(key)) return object.get(key);
        return null;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double number(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) ? object.get(key).getAsDouble() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) ? object.get(key).getAsBoolean() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Vec3 vec(JsonElement element, Vec3 fallback) {
        if (element == null || element.isJsonNull()) return fallback;
        try {
            if (element.isJsonArray()) {
                JsonArray a = element.getAsJsonArray();
                return new Vec3(a.size() > 0 ? a.get(0).getAsFloat() : fallback.x,
                        a.size() > 1 ? a.get(1).getAsFloat() : fallback.y,
                        a.size() > 2 ? a.get(2).getAsFloat() : fallback.z);
            }
            if (element.isJsonObject()) {
                JsonObject o = element.getAsJsonObject();
                return new Vec3(floatField(o, "x", fallback.x),
                        floatField(o, "y", fallback.y),
                        floatField(o, "z", fallback.z));
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static float floatField(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    record RigDefinition(String id, List<BoneDefinition> bones) {
    }

    record BoneDefinition(String name, String parent, Vec3 pivot, Vec3 from, Vec3 to, String block, int color) {
        Vec3 size() {
            return new Vec3(to.x - from.x, to.y - from.y, to.z - from.z);
        }
    }

    record AnimationDefinition(String name, double length, boolean loop, Map<String, List<Keyframe>> tracks) {
        Pose sample(String bone, double time, boolean playLoop) {
            List<Keyframe> keyframes = tracks.get(bone);
            if (keyframes == null || keyframes.isEmpty()) return Pose.REST;
            double t = loop && playLoop ? ((time % length) + length) % length : Math.max(0, Math.min(time, length));
            Keyframe first = keyframes.get(0);
            if (t <= first.time) return first.pose();
            Keyframe last = keyframes.get(keyframes.size() - 1);
            if (t >= last.time) return last.pose();
            for (int i = 0; i < keyframes.size() - 1; i++) {
                Keyframe a = keyframes.get(i);
                Keyframe b = keyframes.get(i + 1);
                if (t >= a.time && t <= b.time) {
                    double span = b.time - a.time;
                    float f = span <= 0 ? 0f : (float) ((t - a.time) / span);
                    return new Pose(a.translation.lerp(b.translation, f),
                            a.rotationDeg.lerp(b.rotationDeg, f),
                            a.scale.lerp(b.scale, f));
                }
            }
            return last.pose();
        }
    }

    record Keyframe(double time, Vec3 translation, Vec3 rotationDeg, Vec3 scale) {
        Pose pose() {
            return new Pose(translation, rotationDeg, scale);
        }
    }

    record RigBinding(UUID entityUuid, String rigId, String animationName, boolean loop, long startedNanos) {
        double elapsedSeconds() {
            return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        }
    }

    record Pose(Vec3 translation, Vec3 rotationDeg, Vec3 scale) {
        static final Pose REST = new Pose(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);
    }

    record Vec3(float x, float y, float z) {
        static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
        static final Vec3 ONE = new Vec3(1f, 1f, 1f);

        Vec3 lerp(Vec3 other, float f) {
            return new Vec3(x + (other.x - x) * f, y + (other.y - y) * f, z + (other.z - z) * f);
        }
    }
}
