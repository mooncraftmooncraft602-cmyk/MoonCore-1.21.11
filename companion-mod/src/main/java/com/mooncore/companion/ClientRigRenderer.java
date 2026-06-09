package com.mooncore.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

final class ClientRigRenderer {

    private static RenderLayer debugQuads;
    private static boolean registered;

    private ClientRigRenderer() {
    }

    static void register() {
        if (registered) return;
        registered = true;
        if (registerAfterEntities("net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents",
                "net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents$AfterEntities")) {
            return;
        }
        registerAfterEntities("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents",
                "net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents$AfterEntities");
    }

    private static boolean registerAfterEntities(String eventsClassName, String callbackClassName) {
        try {
            Class<?> eventsClass = Class.forName(eventsClassName);
            Class<?> callbackClass = Class.forName(callbackClassName);
            Field afterEntities = eventsClass.getField("AFTER_ENTITIES");
            Object event = afterEntities.get(null);
            Object listener = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("afterEntities".equals(method.getName()) && args != null && args.length == 1) {
                            render(args[0]);
                        }
                        return null;
                    });
            Class.forName("net.fabricmc.fabric.api.event.Event")
                    .getMethod("register", Object.class)
                    .invoke(event, listener);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void render(Object context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || ClientRigRegistry.bindings().isEmpty()) return;
        MatrixStack matrices = invokeAny(context, MatrixStack.class, "matrices", "matrixStack");
        VertexConsumerProvider consumers = invokeAny(context, VertexConsumerProvider.class, "consumers");
        Vec3d camera = cameraPos(context);
        if (matrices == null || consumers == null || camera == null) return;
        RenderLayer layer = debugQuads();
        if (layer == null) return;
        VertexConsumer buffer = consumers.getBuffer(layer);
        float tickDelta = tickDelta(context);

        for (ClientRigRegistry.RigBinding binding : ClientRigRegistry.bindings()) {
            ClientRigRegistry.RigDefinition rig = ClientRigRegistry.rig(binding.rigId());
            if (rig == null) continue;
            Entity entity = findEntity(client.world, binding.entityUuid());
            if (entity == null) continue;
            renderBinding(rig, binding, entity, tickDelta, camera, matrices, buffer);
        }
    }

    private static void renderBinding(ClientRigRegistry.RigDefinition rig,
                                      ClientRigRegistry.RigBinding binding,
                                      Entity entity,
                                      float tickDelta,
                                      Vec3d camera,
                                      MatrixStack matrices,
                                      VertexConsumer buffer) {
        Vec3d pos = entity.getLerpedPos(tickDelta);
        float yaw = entity.getYaw(tickDelta);
        ClientRigRegistry.AnimationDefinition animation =
                ClientRigRegistry.animation(binding.rigId(), binding.animationName());

        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
        Matrix4f root = new Matrix4f().rotateY((float) Math.toRadians(180.0f - yaw));
        Map<String, Matrix4f> world = new HashMap<>();
        for (ClientRigRegistry.BoneDefinition bone : rig.bones()) {
            ClientRigRegistry.Pose pose = animation != null
                    ? animation.sample(bone.name(), binding.elapsedSeconds(), binding.loop())
                    : ClientRigRegistry.Pose.REST;
            Matrix4f joint = new Matrix4f()
                    .translate(pose.translation().x(), pose.translation().y(), pose.translation().z())
                    .translate(bone.pivot().x(), bone.pivot().y(), bone.pivot().z())
                    .rotateXYZ(rad(pose.rotationDeg().x()), rad(pose.rotationDeg().y()), rad(pose.rotationDeg().z()))
                    .scale(pose.scale().x(), pose.scale().y(), pose.scale().z())
                    .translate(-bone.pivot().x(), -bone.pivot().y(), -bone.pivot().z());
            Matrix4f parent = bone.parent() != null && world.containsKey(bone.parent())
                    ? world.get(bone.parent()) : root;
            Matrix4f boneWorld = new Matrix4f(parent).mul(joint);
            world.put(bone.name(), boneWorld);
            ClientRigRegistry.Vec3 size = bone.size();
            if (Math.abs(size.x()) < 1.0e-5 || Math.abs(size.y()) < 1.0e-5 || Math.abs(size.z()) < 1.0e-5) {
                continue;
            }
            Matrix4f mesh = new Matrix4f(boneWorld)
                    .translate(bone.from().x(), bone.from().y(), bone.from().z())
                    .scale(size.x(), size.y(), size.z());
            cube(matrices.peek(), buffer, mesh, bone.color());
        }
        matrices.pop();
    }

    private static Entity findEntity(ClientWorld world, java.util.UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) return entity;
        }
        return null;
    }

    private static Vec3d cameraPos(Object context) {
        Object camera = invokeAny(context, Object.class, "camera");
        if (camera != null) {
            Vec3d pos = invokeAny(camera, Vec3d.class, "getCameraPos", "getPos");
            if (pos != null) return pos;
        }
        Object worldState = invokeAny(context, Object.class, "worldState");
        if (worldState != null) {
            try {
                Object cameraRenderState = worldState.getClass().getField("cameraRenderState").get(worldState);
                if (cameraRenderState != null) {
                    Object pos = cameraRenderState.getClass().getField("pos").get(cameraRenderState);
                    if (pos instanceof Vec3d vec) return vec;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return invokeAny(client.gameRenderer.getCamera(), Vec3d.class, "getCameraPos", "getPos");
    }

    private static float tickDelta(Object context) {
        Object camera = invokeAny(context, Object.class, "camera");
        if (camera != null) {
            Float value = invokeAny(camera, Float.class, "getLastTickProgress", "getLastTickDelta");
            if (value != null) return value;
        }
        return 1.0f;
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeAny(Object target, Class<T> type, String... methods) {
        if (target == null) return null;
        for (String name : methods) {
            try {
                Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                if (value == null) return null;
                if (type == Object.class || type.isInstance(value)) return (T) value;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static RenderLayer debugQuads() {
        if (debugQuads != null) return debugQuads;
        try {
            Class<?> renderLayers = Class.forName("net.minecraft.client.render.RenderLayers");
            debugQuads = (RenderLayer) renderLayers.getMethod("debugQuads").invoke(null);
            return debugQuads;
        } catch (Throwable ignored) {
        }
        try {
            debugQuads = (RenderLayer) RenderLayer.class.getMethod("getDebugQuads").invoke(null);
            return debugQuads;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void cube(MatrixStack.Entry entry, VertexConsumer buffer, Matrix4f matrix, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        quad(entry, buffer, matrix, r, g, b, a, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0);
        quad(entry, buffer, matrix, r, g, b, a, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1);
        quad(entry, buffer, matrix, r, g, b, a, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0);
        quad(entry, buffer, matrix, r, g, b, a, 0, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1);
        quad(entry, buffer, matrix, r, g, b, a, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1);
        quad(entry, buffer, matrix, r, g, b, a, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 0);
    }

    private static void quad(MatrixStack.Entry entry, VertexConsumer buffer, Matrix4f matrix,
                             int r, int g, int b, int a,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        vertex(entry, buffer, matrix, ax, ay, az, r, g, b, a);
        vertex(entry, buffer, matrix, bx, by, bz, r, g, b, a);
        vertex(entry, buffer, matrix, cx, cy, cz, r, g, b, a);
        vertex(entry, buffer, matrix, dx, dy, dz, r, g, b, a);
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer buffer, Matrix4f matrix,
                               float x, float y, float z, int r, int g, int b, int a) {
        Vector4f v = new Vector4f(x, y, z, 1f).mul(matrix);
        buffer.vertex(entry, v.x(), v.y(), v.z()).color(r, g, b, a);
    }

    private static float rad(float deg) {
        return (float) Math.toRadians(deg);
    }
}
