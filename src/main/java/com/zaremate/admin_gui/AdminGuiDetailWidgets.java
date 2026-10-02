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
import java.util.Objects;

final class AdminGuiDetailWidgets {
    private static final int CARD_CONTENT_WIDTH = 246;
    private static final int VISIBLE_LINES = 3;

    enum Section {
        TSA,
        ASS,
        TEAM,
        DISCORD,
        PUNISH
    }

    private record DetailLine(
            String text,
            int color,
            String tooltip,
            Runnable action
    ) {}

    private AdminGuiDetailWidgets() {}

    static List<PlainTextButton> build(
            JsonObject detail,
            int playerCount,
            int left,
            int top,
            Font safeFont,
            int tsaScroll,
            int assScroll,
            int teamScroll,
            int discordScroll,
            int punishScroll,
            int clockScroll,
            int noteScroll
    ) {
        safeFont = Objects.requireNonNull(safeFont, "Detail widget font is not initialized");
        List<PlainTextButton> result = new ArrayList<>();
        int x = left + 325;

        add(result, x, top + 68, 150, 18, "PLAYER DETAILS", 0xFF8B949E, null, null, safeFont);
        add(result, left + 205, top + 68, 80, 18,
                playerCount + " players", 0xFF8B949E, null, null, safeFont);

        if (detail == null) {
            return result;
        }

        String name = text(detail, "name", "Unknown");
        String uuid = text(detail, "uuid", "");
        boolean online = bool(detail, "online");

        add(result, x, top + 91, 390, 24, name, 0xFFF0F3F6,
                "Click to copy player name.", () -> copy(name), safeFont);

        String uuidDisplay = fit("UUID: " + uuid, 500, safeFont);
        add(result, x, top + 113, 540, 18, uuidDisplay, 0xFF8B949E,
                "Click to copy player UUID.\n\n" + uuid, () -> copy(uuid), safeFont);

        add(result, x + 430, top + 91, 130, 18,
                online ? "● ONLINE" : "○ OFFLINE",
                online ? 0xFF3FB950 : 0xFF8B949E, null, null, safeFont);

        int cardY = top + 128;
        int tsaX = x + 12;
        int assX = x + 304;

        scrollable(result, tsaLines(detail.getAsJsonObject("tsa"), safeFont), tsaX, cardY + 33, tsaScroll, safeFont);
        scrollable(result, assLines(detail.getAsJsonObject("ass"), safeFont), assX, cardY + 33, assScroll, safeFont);

        cardY += 96;
        scrollable(result, teamLines(detail.getAsJsonObject("teams"), safeFont), tsaX, cardY + 33, teamScroll, safeFont);
        scrollable(result, discordLines(detail.getAsJsonObject("discord"), safeFont), assX, cardY + 33, discordScroll, safeFont);

        int notesY = top + 312;
        scrollable(result, punishLines(detail.getAsJsonObject("punish"), safeFont), x + 12, notesY + 34, punishScroll, safeFont);
        notes(result, detail, x + 304, notesY + 34, safeFont, noteScroll);
        return result;
    }

    private static List<DetailLine> tsaLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o)) {
            lines.add(new DetailLine("Not installed / no data", 0xFF8B949E, null, null));
            return lines;
        }

        String packets = "Packets: " + num(o, "packetChecks");
        lines.add(new DetailLine(
                packets,
                0xFFF0F3F6,
                "Click to copy TSA packet count.",
                () -> copy(packets)
        ));

        String outcomes = "PASS: " + num(o, "packetPasses")
                + "  Modified: " + num(o, "packetModified")
                + "  Timeout: " + num(o, "packetTimeout");
        lines.add(new DetailLine(
                fit(outcomes, CARD_CONTENT_WIDTH, font),
                0xFFF0F3F6,
                "Click to copy TSA packet outcome statistics.",
                () -> copy(outcomes)
        ));

        String last = text(o, "lastPacketStatus", "");
        if (!last.isBlank()) {
            String date = compactDate(text(o, "lastPacketDate", ""));
            String result = "Last: " + last + (date.isBlank() ? "" : "  " + date);
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
            String line = formatDetection(detection, font);
            lines.add(new DetailLine(
                    line,
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

    private static List<DetailLine> punishLines(JsonObject o, Font font) {
        List<DetailLine> lines = new ArrayList<>();
        if (empty(o) || !bool(o, "available")) {
            lines.add(new DetailLine("Punish is not installed / unavailable", 0xFF8B949E, null, null));
            return lines;
        }

        JsonArray history = o.getAsJsonArray("history");
        if (history == null || history.isEmpty()) {
            lines.add(new DetailLine("No recorded offenses.", 0xFF8B949E, null, null));
            return lines;
        }

        for (JsonElement element : history) {
            JsonObject record = element.getAsJsonObject();
            String offense = text(record, "offense", "unknown");
            long number = num(record, "offenseNumber");
            String type = text(record, "type", "").toLowerCase(Locale.ROOT);
            String date = timestamp(num(record, "at"));
            String line = "#" + num(record, "id") + "  " + offense + " #" + number + "  " + date;
            int color = bool(record, "active") ? 0xFFF85149 : 0xFFF0F3F6;
            String tooltip = "Offense: " + offense
                    + "\\nOffense number: " + number
                    + "\\nPunishment: " + type
                    + "\\nBy: " + text(record, "by", "Unknown")
                    + "\\nDate: " + date
                    + "\\nReason: " + text(record, "reason", "No reason given");
            lines.add(new DetailLine(
                    fit(line, CARD_CONTENT_WIDTH, font),
                    color,
                    tooltip,
                    () -> copy(tooltip.replace("\\n", " | "))
            ));
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
        if (members == null) {
            lines.add(new DetailLine("Members: 0", 0xFF8B949E, null, null));
            return lines;
        }

        int memberCount = members.size();
        if (memberCount == 0) {
            lines.add(new DetailLine("Members: 0", 0xFF8B949E, null, null));
            return lines;
        }

        lines.add(new DetailLine(
                "Members: " + memberCount,
                0xFF8B949E,
                null,
                null
        ));

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
        return lines;
    }

    private static void scrollable(
            List<PlainTextButton> out,
            List<DetailLine> lines,
            int x,
            int y,
            int scroll, Font font
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
            case TSA -> tsaLines(detail.getAsJsonObject("tsa"), font).size();
            case ASS -> assLines(detail.getAsJsonObject("ass"), font).size();
            case TEAM -> teamLines(detail.getAsJsonObject("teams"), font).size();
            case DISCORD -> discordLines(detail.getAsJsonObject("discord"), font).size();
            case PUNISH -> punishLines(detail.getAsJsonObject("punish"), font).size();
        };
    }

    private static void notes(
            List<PlainTextButton> out,
            JsonObject detail,
            int x,
            int y, Font font,
            int noteScroll
    ) {
        if (!bool(detail, "notesAvailable")) {
            add(out, x, y, 175, 18, "Admin Notes unavailable.", 0xFF8B949E, null, null, font);
            return;
        }

        JsonArray notes = detail.getAsJsonArray("notes");
        if (notes == null || notes.isEmpty()) {
            add(out, x, y, 175, 18, "No notes.", 0xFF8B949E, null, null, font);
            return;
        }

        final int visibleNotes = 4;
        int shown = Math.min(notes.size(), visibleNotes);
        int start = Math.min(Math.max(0, noteScroll), Math.max(0, notes.size() - shown));
        for (int i = 0; i < shown; i++) {
            JsonObject note = notes.get(start + i).getAsJsonObject();
            String author = text(note, "author", "");
            if (author.isBlank()) author = "System";
            String value = text(note, "text", "");
            String display = fit(author + " — " + value, 175, font);
            add(out, x, y + i * 34, 175, 26, display, 0xFFF0F3F6,
                    "Click to copy the full note.\n\n" + author + " — " + value,
                    () -> copy(value), font);
        }
    }

    private static void add(
            List<PlainTextButton> out,
            int x,
            int y,
            int width,
            int height,
            String value,
            int color,
            String tooltip,
            Runnable action,
            Font font
    ) {
        String safeValue = Objects.requireNonNull(value, "Widget text cannot be null");
        Font safeFont = Objects.requireNonNull(font, "Widget font cannot be null");
        Component component = Objects.requireNonNull(
                Component.literal(safeValue).withStyle(style -> style.withColor(color))
        );

        Runnable safeAction = action == null ? () -> {} : action;
        PlainTextButton button = new PlainTextButton(
                x,
                y,
                width,
                height,
                component,
                ignored -> safeAction.run(),
                safeFont
        );

        if (tooltip != null && !tooltip.isBlank()) {
            Component tooltipComponent = Objects.requireNonNull(Component.literal(tooltip));
            button.setTooltip(Tooltip.create(tooltipComponent));
        }

        out.add(button);
    }

    private static boolean empty(JsonObject o) {
        return o == null || o.entrySet().isEmpty();
    }

    private static String fit(String value, int maxWidth, Font font) {
        String safeValue = Objects.requireNonNull(value, "Text to fit cannot be null");
        Font safeFont = Objects.requireNonNull(font, "Font to fit text cannot be null");

        if (safeValue.isEmpty() || safeFont.width(safeValue) <= maxWidth) {
            return safeValue;
        }

        String ellipsis = "...";
        int available = Math.max(1, maxWidth - safeFont.width(ellipsis));
        return safeFont.plainSubstrByWidth(safeValue, available) + ellipsis;
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

    private static String timestamp(long millis) {
        if (millis <= 0) return "-";
        return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static String compactDate(String value) {
        if (value == null || value.isBlank()) return "";
        int t = value.indexOf('T');
        if (t < 0) return value;
        String date = value.substring(0, t);
        String rest = value.substring(t + 1);
        if (rest.length() > 5) rest = rest.substring(0, 5);
        return date + " " + rest;
    }

    private static String formatDetection(String raw, Font font) {
        if (raw == null || raw.isBlank()) return "";

        String display = raw;
        String[] parts = raw.split(" \\| ", 3);
        if (parts.length == 3) {
            String detail = parts[2].replaceFirst("\\s+\\[[0-9a-fA-F]{64}\\]$", "");
            if (detail.startsWith("RESOURCE_PACK ")) {
                detail = detail.substring("RESOURCE_PACK ".length());
                display = "Pack: " + detail;
            } else if (detail.startsWith("MOD ")) {
                detail = detail.substring("MOD ".length());
                display = "Mod: " + detail;
            } else {
                display = detail;
            }
        }
        return fit(display, CARD_CONTENT_WIDTH, font);
    }


    private static void copy(String value) {
        if (value != null && !value.isBlank()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(value);
        }
    }
}
