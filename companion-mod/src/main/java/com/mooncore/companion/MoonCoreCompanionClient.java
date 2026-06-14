package com.mooncore.companion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint for the optional MoonCore Companion mod.
 *
 * <p>Protocol v1 HELLO/WELCOME stays compatible. Protocol v2 adds chunked server payloads for
 * rigs, animations, play commands and armor metadata.</p>
 */
public final class MoonCoreCompanionClient implements ClientModInitializer {

    public static volatile boolean connected = false;
    public static volatile int protocol = 0;
    public static volatile int capabilities = 0;

    private static KeyBinding studioKey;
    private int helloTicks = 0;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(CompanionPayload.ID, CompanionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionPayload.ID, CompanionPayload.CODEC);
        ClientRigRenderer.register();

        KeyBinding key = createStudioKey();
        studioKey = (key != null) ? KeyBindingHelper.registerKeyBinding(key) : null;

        ClientPlayNetworking.registerGlobalReceiver(CompanionPayload.ID, (payload, context) -> {
            byte[] data = payload.data();
            CompanionProtocol.handle(data);
            if (data.length >= 1 && data[0] == CompanionProtocol.OP_WELCOME) {
                context.client().execute(() -> {
                    if (context.client().player != null) {
                        context.client().player.sendMessage(Text.literal(
                                "\u00A7dMoonCore Companion \u00A7aconnecte \u00A77(appuie sur \u00A7fM\u00A77 pour le Studio)."), false);
                    }
                });
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetConnectionState());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetConnectionState());
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (client.player != null && !connected && helloTicks < 100) {
            if (helloTicks % 20 == 0 && ClientPlayNetworking.canSend(CompanionPayload.ID)) {
                try {
                    ClientPlayNetworking.send(new CompanionPayload(new byte[]{
                            CompanionProtocol.OP_HELLO, CompanionProtocol.PROTOCOL_V2
                    }));
                } catch (Throwable ignored) {
                    // Non-MoonCore servers simply ignore the channel.
                }
            }
            helloTicks++;
        }
        CompanionProtocol.tickCleanup();
        while (studioKey != null && studioKey.wasPressed()) {
            if (client.player != null) client.setScreen(new StudioScreen());
        }
    }

    private void resetConnectionState() {
        connected = false;
        protocol = 0;
        capabilities = 0;
        helloTicks = 0;
        CompanionProtocol.clear();
    }

    /**
     * Crée la touche du Studio. En 1.21.6+ la catégorie est un {@link KeyBinding.Category}
     * (et non plus une {@code String}) — on appelle l'API <b>directement</b> (remappée à la
     * compilation), jamais par réflexion sur des noms Yarn (qui n'existent pas au runtime
     * intermediary → {@code ClassNotFoundException}/{@code NoSuchMethodException}).
     *
     * <p>Non-fatal : si l'API diffère sur la version cible, on log et on retourne {@code null}
     * — le rendu des rigs fonctionne sans la touche (cf. garde {@code studioKey != null}).
     */
    private static KeyBinding createStudioKey() {
        try {
            KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("mooncore", "companion"));
            return new KeyBinding("key.mooncore-companion.studio",
                    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, category);
        } catch (Throwable t) {
            System.err.println("[MoonCore Companion] Touche Studio indisponible sur cette version, ignorée : " + t);
            return null;
        }
    }
}
