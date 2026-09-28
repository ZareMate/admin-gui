package com.zaremate.admin_gui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;

public final class AdminGuiClientBridge {
    private AdminGuiClientBridge() {}

    public static void open(String data) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdminGuiClient.open(data));
    }

    public static void detail(String data) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdminGuiClient.detail(data));
    }
}
