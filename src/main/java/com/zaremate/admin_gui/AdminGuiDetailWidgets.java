package com.zaremate.admin_gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class AdminGuiDetailWidgets {
    private AdminGuiDetailWidgets() {}

    static List<PlainTextButton> build(JsonObject detail, int left, int top, Font font) {
        List<PlainTextButton> result = new ArrayList<>();
        int x = left + 305;

        add(result, x, top + 8, 150, 18, "ADMIN GUI", 0xFFFFFFFF, null, null, font);
        add(result, left + 205, top + 8, 80, 18,
                String.valueOf(detail == null ? 0 : 0) + " players", 0xFF8A9099, null, null, font);
        add(result, left + 12, top + 58, 180, 18, "PLAYERS", 0xFFB8BEC8, null, null, font);

        if (detail == null) {
            add(result, x, top + 55, 260, 18, "Select a player", 0xFF9AA0AA, null, null, font);
            return result;
        }

        String name = text(detail, "name", "Unknown");
        String uuid = text(detail, "uuid", "");
        boolean online = bool(detail, "online");

        add(result, x, top + 38, 390, 18, name, 0xFFFFFFFF,
                "Click to copy player name.", () -> copy(name), font);
        add(result, x, top + 58, 540, 18, "UUID: " + uuid, 0xFF9AA0AA,
                "Click to copy player UUID.", () -> copy(uuid), font);
        add(result, x + 430, top + 38, 130, 18,
                online ? "● ONLINE" : "○ OFFLINE",
                online ? 0xFF55DD77 : 0xFF888E98, null, null, font);

        int cardY = top + 94;
        header(result, x + 8, cardY + 7, "TSA ANTICHEAT", font);
        header(result, x + 300, cardY + 7, "AIRPORT SECURITY", font);
        tsa(result, detail.getAsJsonObject("tsa"), x + 8, cardY + 26, font);
        ass(result, detail.getAsJsonObject("ass"), x + 300, cardY + 26, font);

        cardY += 88;
        header(result, x + 8, cardY + 7, "FTB TEAM", font);
        header(result, x + 300, cardY + 7, "DISCORD", font);
        team(result, detail.getAsJsonObject("teams"), x + 8, cardY + 26, font);
        discord(result, detail.getAsJsonObject("discord"), x + 300, cardY + 26, font);

        cardY += 70;
        header(result, x + 8, cardY + 7, "CLOCK IN", font);
        clock(result, detail.getAsJsonObject("clockin"), x + 8, cardY + 26, font);

        int notesY = top + 298;
        header(result, x + 8, notesY + 7, "ADMIN NOTES", font);
        notes(result, detail, x + 8, notesY + 28, font);
        return result;
    }

    private static void header(List<PlainTextButton> out, int x, int y, String text, Font font) {
        add(out, x, y, 260, 18, text, 0xFFD5A84A, null, null, font);
    }

    private static void tsa(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        String stats = "Packets: " + num(o, "packetChecks")
                + "  PASS: " + num(o, "packetPasses")
                + "  Modified: " + num(o, "packetModified")
                + "  Timeout: " + num(o, "packetTimeout");
        add(out, x, y, 260, 18, stats, 0xFFB8BEC8,
                "Click to copy TSA packet statistics.", () -> copy(stats), font);
        String last = text(o, "lastPacketStatus", "");
        if (!last.isBlank()) {
            String result = "Last: " + last + " " + text(o, "lastPacketDate", "");
            add(out, x, y + 19, 260, 18, result, 0xFF8E96A2,
                    "Click to copy the last TSA packet result.", () -> copy(result), font);
        }
        JsonArray detections = o.getAsJsonArray("detections");
        if (detections != null && !detections.isEmpty()) {
            String all = join(detections);
            add(out, x, y + 38, 260, 18, "Detections: " + detections.size(),
                    0xFFE1B85A, "Click to copy the complete TSA detection history.\n\n" + all,
                    () -> copy(all), font);
        }
    }

    private static void ass(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        String status = text(o, "status", "UNKNOWN");
        String stats = "Status: " + status + "  Checks: " + num(o, "totalChecks")
                + "  Detected: " + num(o, "detectedChecks") + "  Clean: " + num(o, "cleanChecks");
        add(out, x, y, 260, 18, stats,
                status.equalsIgnoreCase("DETECTED") ? 0xFFFF6666 : 0xFFB8BEC8,
                "Click to copy ASS statistics.", () -> copy(stats), font);
        JsonObject dates = o.getAsJsonObject("detectionDates");
        String state = "Cleared: " + text(o, "clearedDate", "-")
                + "  Categories: " + (dates == null ? 0 : dates.entrySet().size());
        add(out, x, y + 19, 260, 18, state, 0xFF8E96A2,
                "Click to copy ASS offense state.", () -> copy(state), font);
    }

    private static void team(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        String name = text(o, "name", "No team");
        String id = text(o, "id", "");
        add(out, x, y, 260, 18, id.isBlank() ? name : name + "  ID: " + id, 0xFFB8BEC8,
                id.isBlank() ? null : "Click to copy FTB Team ID.",
                id.isBlank() ? null : () -> copy(id), font);
        JsonArray members = o.getAsJsonArray("members");
        String all = members == null ? "" : join(members);
        add(out, x, y + 19, 260, 18, "Members: " + (members == null ? 0 : members.size()),
                0xFF8E96A2, all.isBlank() ? null : "Click to copy team member UUIDs.",
                all.isBlank() ? null : () -> copy(all), font);
    }

    private static void discord(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        String display = text(o, "displayName", text(o, "discordTag", "Linked"));
        String id = text(o, "discordId", "");
        add(out, x, y, 260, 18, display, 0xFFB8BEC8,
                id.isBlank() ? null : "Click to copy Discord ID.",
                id.isBlank() ? null : () -> copy(id), font);
        add(out, x, y + 19, 260, 18, "ID: " + id, 0xFF8E96A2,
                id.isBlank() ? null : "Click to copy Discord ID.",
                id.isBlank() ? null : () -> copy(id), font);
    }

    private static void clock(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        long seconds = num(o, "totalSeconds");
        String value = (bool(o, "clockedIn") ? "CLOCKED IN" : "CLOCKED OUT")
                + "  Total: " + formatSeconds(seconds);
        add(out, x, y, 260, 18, value,
                bool(o, "clockedIn") ? 0xFF55DD77 : 0xFF888E98,
                "Click to copy total ClockIn seconds.", () -> copy(String.valueOf(seconds)), font);
    }

    private static void notes(List<PlainTextButton> out, JsonObject detail, int x, int y, Font font) {
        if (!detail.has("notesAvailable") || !detail.get("notesAvailable").getAsBoolean()) {
            add(out, x, y, 430, 18, "Admin Notes is not installed.", 0xFF666D78, null, null, font);
            return;
        }
        JsonArray notes = detail.getAsJsonArray("notes");
        if (notes == null || notes.isEmpty()) {
            add(out, x, y, 430, 18, "No notes for this player.", 0xFF777E89, null, null, font);
            return;
        }
        int shown = Math.min(notes.size(), 3);
        for (int i = 0; i < shown; i++) {
            JsonObject note = notes.get(i).getAsJsonObject();
            String author = text(note, "author", "");
            if (author.isBlank()) author = "System";
            String value = text(note, "text", "");
            String preview = value.length() > 58 ? value.substring(0, 55) + "..." : value;
            add(out, x, y + i * 36, 425, 18, author + ": " + preview, 0xFFE1E4E8,
                    "Click to copy the full note.\n\n" + value, () -> copy(value), font);
        }
        if (notes.size() > shown) {
            add(out, x, y + shown * 36, 430, 18,
                    "+" + (notes.size() - shown) + " more notes", 0xFF666D78, null, null, font);
        }
    }

    private static void add(List<PlainTextButton> out, int x, int y, int width, int height,
                            String value, int color, String tooltip, Runnable action, Font font) {
        Component component = Component.literal(value)
                .withStyle(style -> style.withColor(color));
        PlainTextButton button = new PlainTextButton(x, y, width, height, component,
                ignored -> { if (action != null) action.run(); }, font);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        out.add(button);
    }

    private static boolean empty(JsonObject o) {
        return o == null || o.entrySet().isEmpty();
    }

    private static String text(JsonObject o, String key, String fallback) {
        return o != null && o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).getAsBoolean();
    }

    private static long num(JsonObject o, String key) {
        return o != null && o.has(key) ? o.get(key).getAsLong() : 0L;
    }

    private static String join(JsonArray array) {
        StringBuilder out = new StringBuilder();
        for (JsonElement element : array) {
            if (!out.isEmpty()) out.append('\n');
            out.append(element.getAsString());
        }
        return out.toString();
    }

    private static String formatSeconds(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h + "h " + String.format("%02dm %02ds", m, s);
    }

    private static void copy(String value) {
        if (value != null && !value.isBlank()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
        }
    }
}
