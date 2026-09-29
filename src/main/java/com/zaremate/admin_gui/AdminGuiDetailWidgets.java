package com.zaremate.admin_gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdminGuiDetailWidgets {
    private static final int CARD_CONTENT_WIDTH = 246;
    private static final int VISIBLE_LINES = 3;

    enum Section {
        TSA,
        ASS,
        TEAM,
        DISCORD,
        CLOCK
    }

    private record DetailLine(String text, int color, String tooltip, Runnable action) {}

    private AdminGuiDetailWidgets() {}

    static List<PlainTextButton> build(
            JsonObject detail,
            int playerCount,
            int left,
            int top,
            Font font,
            int tsaScroll,
            int assScroll,
            int teamScroll,
            int discordScroll,
            int clockScroll,
            int noteScroll
    ) {
        List<PlainTextButton> result = new ArrayList<>();
        int x = left + 325;

        add(result, x, top + 68, 150, 18, "PLAYER DETAILS", 0xFF8B949E, null, null, font);
        add(result, left + 205, top + 68, 80, 18,
                playerCount + " players", 0xFF8B949E, null, null, font);

        if (detail == null) {
            add(result, x, top + 91, 260, 18, "Select a player", 0xFF8B949E, null, null, font);
            return result;
        }

        String name = text(detail, "name", "Unknown");
        String uuid = text(detail, "uuid", "");
        boolean online = bool(detail, "online");

        add(result, x, top + 91, 390, 24, name, 0xFFF0F3F6,
                "Click to copy player name.", () -> copy(name), font);

        String uuidDisplay = fit("UUID: " + uuid, 500, font);
        add(result, x, top + 113, 540, 18, uuidDisplay, 0xFF8B949E,
                "Click to copy player UUID.\n\n" + uuid, () -> copy(uuid), font);

        add(result, x + 430, top + 91, 130, 18,
                online ? "● ONLINE" : "○ OFFLINE",
                online ? 0xFF3FB950 : 0xFF8B949E, null, null, font);

        int cardY = top + 128;
        int tsaX = x + 12;
        int assX = x + 304;

        scrollable(result, tsaLines(detail, font), tsaX, cardY + 33, tsaScroll, font);
        scrollable(result, assLines(detail, font), assX, cardY + 33, assScroll, font);

        cardY += 96;
        scrollable(result, teamLines(detail, font), tsaX, cardY + 33, teamScroll, font);
        scrollable(result, discordLines(detail, font), assX, cardY + 33, discordScroll, font);

        cardY += 88;
        scrollable(result, clockLines(detail, font), tsaX, cardY + 27, clockScroll, font);

        int notesY = top + 370;
        notes(result, detail, x + 12, notesY + 34, font, noteScroll);
        return result;
    }

    private static List<DetailLine> tsaLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        lines.add(new DetailLine(
                "Packets: " + num(o, "packetChecks"),
                0xFFF0F3F6,
                "Click to copy TSA packet check count.",
                () -> copy(String.valueOf(num(o, "packetChecks")))
        ));
        lines.add(new DetailLine(
                "PASS: " + num(o, "packetPasses")
                        + "  Modified: " + num(o, "packetModified")
                        + "  Timeout: " + num(o, "packetTimeout"),
                0xFFF0F3F6,
                "Click to copy TSA packet statistics.",
                () -> copy(
                        "PASS: " + num(o, "packetPasses")
                                + "  Modified: " + num(o, "packetModified")
                                + "  Timeout: " + num(o, "packetTimeout")
                )
        ));

        String last = text(o, "lastPacketStatus", "");
        if (!last.isBlank()) {
            String result = "Last: " + last + " " + text(o, "lastPacketDate", "");
            int color = switch (last.toUpperCase(Locale.ROOT)) {
                case "MODIFIED", "TIMEOUT" -> 0xFFF85149;
                case "PASS" -> 0xFF3FB950;
                default -> 0xFF8B949E;
            };
            lines.add(new DetailLine(
                    fit(result, CARD_CONTENT_WIDTH, font),
                    color,
                    "Click to copy the last TSA packet result.",
                    () -> copy(result)
            ));
        }

        for (String detection : uniqueDetections(o.getAsJsonArray("detections"))) {
            String line = "Detection: " + detection;
            lines.add(new DetailLine(
                    fit(line, CARD_CONTENT_WIDTH, font),
                    0xFFF85149,
                    "Click to copy this TSA detection.\n\n" + detection,
                    () -> copy(detection)
            ));
        }
        return lines;
    }

    private static List<DetailLine> assLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        String status = text(o, "status", "UNKNOWN");
        int statusColor = status.equalsIgnoreCase("DETECTED") ? 0xFFF85149 : 0xFFF0F3F6;
        lines.add(new DetailLine("Status: " + status, statusColor,
                "Click to copy ASS status.\n\n" + status, () -> copy(status)));

        String checks = "Checks: " + num(o, "totalChecks")
                + "  Detected: " + num(o, "detectedChecks")
                + "  Clean: " + num(o, "cleanChecks");
        lines.add(new DetailLine(
                fit(checks, CARD_CONTENT_WIDTH, font),
                0xFFF0F3F6,
                "Click to copy ASS check statistics.\n\n" + checks,
                () -> copy(checks)
        ));

        String inconclusive = "Inconclusive: " + num(o, "inconclusiveChecks");
        lines.add(new DetailLine(
                inconclusive,
                0xFF8B949E,
                "Click to copy ASS inconclusive check count.",
                () -> copy(inconclusive)
        ));

        JsonObject dates = o.getAsJsonObject("detectionDates");
        String cleared = "Cleared: " + text(o, "clearedDate", "-");
        lines.add(new DetailLine(
                fit(cleared, CARD_CONTENT_WIDTH, font),
                0xFF8B949E,
                "Click to copy ASS cleared date.",
                () -> copy(cleared)
        ));

        if (dates != null) {
            for (var entry : dates.entrySet()) {
                String category = entry.getKey() + ": " + entry.getValue().getAsString();
                lines.add(new DetailLine(
                        fit(category, CARD_CONTENT_WIDTH, font),
                        0xFFD29922,
                        "Click to copy this ASS detection category.\n\n" + category,
                        () -> copy(category)
                ));
            }
        }
        return lines;
    }

    private static List<DetailLine> teamLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        String name = text(o, "name", "No team");
        String id = text(o, "id", "");
        String teamDisplay = id.isBlank() ? name : name + "  ID: " + id;
        lines.add(new DetailLine(
                fit(teamDisplay, CARD_CONTENT_WIDTH, font),
                0xFFF0F3F6,
                id.isBlank() ? null : "Click to copy FTB Team ID.\n\n" + id,
                id.isBlank() ? null : () -> copy(id)
        ));

        JsonArray members = o.getAsJsonArray("members");
        if (members == null || members.isEmpty()) {
            lines.add(new DetailLine("Members: 0", 0xFF8B949E, null, null));
            return lines;
        }

        for (JsonElement element : members) {
            JsonObject member = element.getAsJsonObject();
            String memberName = text(member, "name", text(member, "uuid", "Unknown"));
            String rank = text(member, "rankDisplay", text(member, "rank", "NONE"));
            String line = memberName + " — " + rank;
            int color = rank.equalsIgnoreCase("OWNER") ? 0xFFD29922 : 0xFFF0F3F6;
            lines.add(new DetailLine(
                    fit(line, CARD_CONTENT_WIDTH, font),
                    color,
                    "Click to copy this team member.\n\n" + line,
                    () -> copy(line)
            ));
        }
        return lines;
    }

    private static List<DetailLine> discordLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        String display = text(o, "displayName", text(o, "discordTag", "Linked"));
        String id = text(o, "discordId", "");
        lines.add(new DetailLine(
                display,
                0xFFF0F3F6,
                id.isBlank() ? null : "Click to copy Discord ID.",
                id.isBlank() ? null : () -> copy(id)
        ));
        lines.add(new DetailLine(
                fit("ID: " + id, CARD_CONTENT_WIDTH, font),
                0xFF8B949E,
                id.isBlank() ? null : "Click to copy Discord ID.\n\n" + id,
                id.isBlank() ? null : () -> copy(id)
        ));

        if (o.has("linkedAt") && num(o, "linkedAt") > 0) {
            lines.add(new DetailLine(
                    "Linked: " + num(o, "linkedAt"),
                    0xFF8B949E,
                    "Click to copy Discord link timestamp.",
                    () -> copy(String.valueOf(num(o, "linkedAt")))
            ));
        }

        if (o.has("rewarded")) {
            String rewarded = "Rewarded: " + (bool(o, "rewarded") ? "yes" : "no");
            lines.add(new DetailLine(
                    rewarded,
                    bool(o, "rewarded") ? 0xFF3FB950 : 0xFF8B949E,
                    null,
                    null
            ));
        }
        return lines;
    }

    private static List<DetailLine> clockLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        long seconds = num(o, "totalSeconds");
        String value = (bool(o, "clockedIn") ? "CLOCKED IN" : "CLOCKED OUT")
                + "  Total: " + formatSeconds(seconds);
        lines.add(new DetailLine(
                value,
                bool(o, "clockedIn") ? 0xFF3FB950 : 0xFF8B949E,
                "Click to copy total ClockIn seconds.",
                () -> copy(String.valueOf(seconds))
        ));
        return lines;
    }

    private static void scrollable(
            List<PlainTextButton> out,
            List<DetailLine> lines,
            int x,
            int y,
            int scroll,
            Font font
    ) {
        int maxScroll = Math.max(0, lines.size() - VISIBLE_LINES);
        int start = Math.min(Math.max(0, scroll), maxScroll);
        int end = Math.min(lines.size(), start + VISIBLE_LINES);

        for (int i = start; i < end; i++) {
            DetailLine line = lines.get(i);
            add(out, x, y + (i - start) * 18, CARD_CONTENT_WIDTH, 18,
                    line.text(), line.color(), line.tooltip(), line.action(), font);
        }
    }

    static int lineCount(JsonObject detail, Section section, Font font) {
        if (detail == null) return 0;
        return switch (section) {
            case TSA -> tsaLines(detail, font).size();
            case ASS -> assLines(detail, font).size();
            case TEAM -> teamLines(detail, font).size();
            case DISCORD -> discordLines(detail, font).size();
            case CLOCK -> clockLines(detail, font).size();
        };
    }

    private static void notes(
            List<PlainTextButton> out,
            JsonObject detail,
            int x,
            int y,
            Font font,
            int noteScroll
    ) {
        if (!bool(detail, "notesAvailable")) {
            add(out, x, y, 405, 18, "Admin Notes is not installed.", 0xFF8B949E, null, null, font);
            return;
        }

        JsonArray notes = detail.getAsJsonArray("notes");
        if (notes == null || notes.isEmpty()) {
            add(out, x, y, 405, 18, "No notes for this player.", 0xFF8B949E, null, null, font);
            return;
        }

        int shown = Math.min(notes.size(), 3);
        int start = Math.min(Math.max(0, noteScroll), Math.max(0, notes.size() - shown));
        for (int i = 0; i < shown; i++) {
            JsonObject note = notes.get(start + i).getAsJsonObject();
            String author = text(note, "author", "");
            if (author.isBlank()) author = "System";
            String value = text(note, "text", "");
            String display = fit(author + " — " + value, 385, font);
            add(out, x, y + i * 34, 405, 26, display, 0xFFF0F3F6,
                    "Click to copy the full note.\n\n" + author + " — " + value,
                    () -> copy(value), font);
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

    private static String fit(String value, int maxWidth, Font font) {
        if (value == null || value.isEmpty() || font.width(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        String ellipsis = "...";
        int available = Math.max(1, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(value, available) + ellipsis;
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
        if (array == null || array.isEmpty()) return List.of();

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
