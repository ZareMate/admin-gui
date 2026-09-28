package com.zaremate.admin_gui;

public final class AdminGuiClientBridge {
    private AdminGuiClientBridge() {}

    public static void open(String data) {
        invoke("open", data);
    }

    public static void detail(String data) {
        invoke("detail", data);
    }

    private static void invoke(String method, String data) {
        try {
            Class<?> client = Class.forName("com.zaremate.admin_gui.AdminGuiClient");
            client.getMethod(method, String.class).invoke(null, data);
        } catch (ClassNotFoundException ignored) {
            // Client-only class is not loaded on a dedicated server.
        } catch (Throwable ex) {
            AdminGui.LOGGER.warn("Failed to dispatch client GUI packet.", ex);
        }
    }
}
