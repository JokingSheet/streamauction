package com.payauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PayAuctionClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        AuctionManager.init();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("payauction", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.payauction.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, category));

        ClientReceiveMessageEvents.GAME.register(AuctionManager::onMessage);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new AuctionScreen());
                }
            }
            AuctionManager.tick(client);
        });
    }
}
