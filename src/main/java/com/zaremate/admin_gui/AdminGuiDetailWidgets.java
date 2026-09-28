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
import java.util.Locale;

final class AdminGuiDetailWidgets {
    private static final int CARD_INSET = 8;
    private static final int CARD_CONTENT_WIDTH = 254;

    private AdminGuiDetailWidgets() {}

    static List<PlainTextButton> build(
            JsonObject detail,
            int playerCount,
            int left,
            int top,
            Font font,
            int noteScroll
    ) {
        List<PlainTextButton> result = new ArrayList<>();
        int x = left + 305;

        add(result, x, top + 8, 150, 18, "ADMINISTRATION", 0xFFE5DED0, null, null, font);
        add(result, left + 205, top + 8, 80, 18,
                playerCount + " players", 0xFF99958A, null, null, font);
        add(result, left + 12, top + 58, 180, 18, "PLAYERS", 0xFFD1C7B7, null, null, font);

        if (detail == null) {
            add(result, x, top + 55, 260, 18, "Select a player", 0xFFA9A49A, null, null, font);
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
                online ? 0xFF79B85F : 0xFF8F8E84, null, null, font);

        int cardY = top + 94;
        int tsaX = x + 8 + CARD_INSET;
        int assX = x + 300 + CARD_INSET;

        header(result, x + 8 + CARD_INSET, cardY + 7, "TSA ANTICHEAT", font);
        header(result, x + 300 + CARD_INSET, cardY + 7, "AIRPORT SECURITY", font);
        tsa(result, detail.getAsJsonObject("tsa"), tsaX, cardY + 27, font);
        ass(result, detail.getAsJsonObject("ass"), assX, cardY + 27, font);

        cardY += 96;
        header(result, x + 8 + CARD_INSET, cardY + 7, "FTB TEAM", font);
        header(result, x + 300 + CARD_INSET, cardY + 7, "DISCORD", font);
        team(result, detail.getAsJsonObject("teams"), tsaX, cardY + 27, font);
        discord(result, detail.getAsJsonObject("discord"), assX, cardY + 27, font);

        cardY += 88;
        header(result, x + 8 + CARD_INSET, cardY + 7, "CLOCK IN", font);
        clock(result, detail.getAsJsonObject("clockin"), tsaX, cardY + 27, font);

        int notesY = top + 331;
        header(result, x + 8 + CARD_INSET, notesY + 7, "ADMIN NOTES", font);
        notes(result, detail, x + 8 + CARD_INSET, notesY + 28, font, noteScroll);
        return result;
    }

    private static void header(List<PlainTextButton> out, int x, int y, String text, Font font) {
        add(out, x, y, CARD_CONTENT_WIDTH, 18, text, 0xFFE0A15F, null, null, font);
    }

    private static void tsa(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, CARD_CONTENT_WIDTH, 18, "Not installed / no data", 0xFF77776F, null, null, font);
            return;
        }
        String stats = "Packets: " + num(o, "packetChecks")
                + "  PASS: " + num(o, "packetPasses")
                + "  Modified: " + num(o, "packetModified")
                + "  Timeout: " + num(o, "packetTimeout");
        add(out, x, y, CARD_CONTENT_WIDTH, 18, stats, 0xFFB8BEC8,
                "Click to copy TSA packet statistics.", () -> copy(stats), font);
        String last = text(o, "lastPacketStatus", "");
        if (!last.isBlank()) {
            String result = "Last: " + last + " " + text(o, "lastPacketDate", "");
            int resultColor = switch (last.toUpperCase(Locale.ROOT)) {
                case "MODIFIED", "TIMEOUT" -> 0xFFE35A4F;
                case "PASS" -> 0xFF79B85F;
                default -> 0xFF9B978B;
            };
            add(out, x, y + 18, CARD_CONTENT_WIDTH, 18, result, resultColor,
                    "Click to copy the last TSA packet result.", () -> copy(result), font);
        }
        JsonArray detections = o.getAsJsonArray("detections");
        List<String> uniqueDetections = uniqueDetections(detections);
        if (!uniqueDetections.isEmpty()) {
            String all = String.join("\n", uniqueDetections);
            add(out, x, y + 38, CARD_CONTENT_WIDTH, 18, "Detections: " + uniqueDetections.size(),
                    0xFFE35A4F,
                    "Click to copy unique TSA detections.\n\n" + all,
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
        add(out, x, y, CARD_CONTENT_WIDTH, 18, stats,
                status.equalsIgnoreCase("DETECTED") ? 0xFFE35A4F : 0xFFB8BEC8,
                "Click to copy ASS statistics.", () -> copy(stats), font);
        JsonObject dates = o.getAsJsonObject("detectionDates");
        String state = "Cleared: " + text(o, "clearedDate", "-")
                + "  Categories: " + (dates == null ? 0 : dates.entrySet().size());
        add(out, x, y + 18, CARD_CONTENT_WIDTH, 18, state, 0xFF8E96A2,
                "Click to copy ASS offense state.", () -> copy(state), font);
    }

    private static void team(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF77776F, null, null, font);
            return;
        }

        String name = text(o, "name", "No team");
        String id = text(o, "id", "");
        add(out, x, y, CARD_CONTENT_WIDTH, 18,
                id.isBlank() ? name : name + "  ID: " + id,
                0xFFD1C7B7,
                id.isBlank() ? null : "Click to copy FTB Team ID.",
                id.isBlank() ? null : () -> copy(id), font);

        JsonArray members = o.getAsJsonArray("members");
        if (members == null || members.isEmpty()) {
            add(out, x, y + 18, CARD_CONTENT_WIDTH, 18, "Members: 0", 0xFF9B978B, null, null, font);
            return;
        }

        StringBuilder fullRoster = new StringBuilder();
        int shown = Math.min(members.size(), 1);
        for (int i = 0; i < shown; i++) {
            JsonObject member = members.get(i).getAsJsonObject();
            String memberName = text(member, "name", text(member, "uuid", "Unknown"));
            String rank = text(member, "rankDisplay", text(member, "rank", "NONE"));
            String line = memberName + " — " + rank;

            if (!fullRoster.isEmpty()) fullRoster.append('\n');
            fullRoster.append(line);

            final String copiedLine = line;
            add(out, x, y + 18 + i * 18, CARD_CONTENT_WIDTH, 18,
                    line, rank.equalsIgnoreCase("OWNER") ? 0xFFE0A15F : 0xFFD1C7B7,
                    "Click to copy this member.\n\n" + line,
                    () -> copy(copiedLine), font);
        }

        if (members.size() > shown) {
            String all = fullRoster.toString();
            for (int i = shown; i < members.size(); i++) {
                JsonObject member = members.get(i).getAsJsonObject();
                if (!all.isEmpty()) all += "\n";
                all += text(member, "name", text(member, "uuid", "Unknown"))
                        + " — " + text(member, "rankDisplay", text(member, "rank", "NONE"));
            }
            final String roster = all;
            add(out, x, y + 18 + shown * 18, CARD_CONTENT_WIDTH, 18,
                    "+" + (members.size() - shown) + " more members",
                    0xFF9B978B,
                    "Click to copy the complete team roster.\n\n" + roster,
                    () -> copy(roster), font);
        }
    }
    private static void discord(List<PlainTextButton> out, JsonObject o, int x, int y, Font font) {
        if (empty(o)) {
            add(out, x, y, 260, 18, "Not installed / no data", 0xFF666D78, null, null, font);
            return;
        }
        String display = text(o, "displayName", text(o, "discordTag", "Linked"));
        String id = text(o, "discordId", "");
        add(out, x, y, CARD_CONTENT_WIDTH, 18, display, 0xFFB8BEC8,
                id.isBlank() ? null : "Click to copy Discord ID.",
                id.isBlank() ? null : () -> copy(id), font);
        add(out, x, y + 18, CARD_CONTENT_WIDTH, 18, "ID: " + id, 0xFF8E96A2,
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
        add(out, x, y, CARD_CONTENT_WIDTH, 18, value,
                bool(o, "clockedIn") ? 0xFF55DD77 : 0xFF888E98,
                "Click to copy total ClockIn seconds.", () -> copy(String.valueOf(seconds)), font);
    }

    private static void notes(
            List<PlainTextButton> out,
            JsonObject detail,
            int x,
            int y,
            Font font,
            int noteScroll
    ) {
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
        int start = Math.min(noteScroll, Math.max(0, notes.size() - shown));
        for (int i = 0; i < shown; i++) {
            JsonObject note = notes.get(start + i).getAsJsonObject();
            String author = text(note, "author", "");
            if (author.isBlank()) author = "System";
            String value = text(note, "text", "");
            String preview = value.length() > 58 ? value.substring(0, 55) + "..." : value;
            Component noteText = Component.literal(author + ": " + preview)
                    .withStyle(s -> s.withColor(0xFF3A2B20));
            add(out, x, y + i * 36, 425, 26, noteText.getString(), 0xFF3A2B20,
                    "Click to copy the full note.\n\n" + value, () -> copy(value), font);
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

    private static String friendly(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.startsWith("literal(") || value.contains("contents=")) {
            int literalStart = value.indexOf("literal(");
            int end = value.indexOf(')', literalStart + 8);
            if (literalStart >= 0 && end > literalStart) {
                String inner = value.substring(literalStart + 8, end);
                int textStart = inner.indexOf("text=");
                if (textStart >= 0) {
                    inner = inner.substring(textStart + 5);
                }
                return inner.replaceAll(",\\s*style=.*$", "");
            }
        }
        return value;
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

    private static List<String> uniqueDetections(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }

        java.util.LinkedHashMap<String, String> unique = new java.util.LinkedHashMap<>();
        for (JsonElement element : array) {
            String raw = element.getAsString();
            String identity = raw;

            int marker = raw.indexOf(" | DETECTED | ");
            if (marker >= 0) {
                identity = raw.substring(marker + " | DETECTED | ".length());
            }

            identity = identity.replaceFirst("\\s+\\[[0-9a-fA-F]{64}\\]$", "");
            unique.put(identity.toLowerCase(Locale.ROOT), raw);
        }

        return List.copyOf(unique.values());
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
