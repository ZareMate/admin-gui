package com.zaremate.admin_gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AdminGui.MOD_ID, value = Dist.CLIENT)
public final class AdminGuiKeybind {
    private static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.admin_gui.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.misc"
    );

    private AdminGuiKeybind() {
    }

    @SubscribeEvent
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        while (OPEN_GUI.consumeClick()) {
            if (minecraft.screen == null && minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand("adm-gui");
            }
        }
    }
}
