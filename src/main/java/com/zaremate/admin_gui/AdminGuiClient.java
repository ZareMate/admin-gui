package com.zaremate.admin_gui;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.text.SimpleDateFormat;
import java.util.*;

public final class AdminGuiClient {
    private static AdminGuiScreen screen;

    private AdminGuiClient() {}

    public static void open(String data) {
        Minecraft.getInstance().execute(() -> {
            screen = new AdminGuiScreen(data);
            Minecraft.getInstance().setScreen(screen);
        });
    }

    public static void detail(String data) {
        Minecraft.getInstance().execute(() -> {
            if (screen != null) screen.updateDetail(data);
        });
    }

    public static final class AdminGuiScreen extends Screen {
        private final List<PlayerRef> players = new ArrayList<>();
        private final List<Button> playerButtons = new ArrayList<>();
        private final List<Button> noteButtons = new ArrayList<>();
        private String selectedUuid;
        private JsonObject detail;
        private EditBox search;
        private EditBox noteInput;
        private UUID editingNote;
        private int playerScroll;
        private boolean suppressSearch;

        private static final int WIDTH = 900;
        private static final int HEIGHT = 520;

        public AdminGuiScreen(String data) {
            super(Component.literal("Admin GUI"));
            loadList(data);
        }

        private void loadList(String data) {
            players.clear();
            try {
                JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("players")) {
                    JsonObject p = e.getAsJsonObject();
                    players.add(new PlayerRef(
                            p.get("uuid").getAsString(),
                            p.get("name").getAsString(),
                            p.has("online") && p.get("online").getAsBoolean()
                    ));
                }
                if (!players.isEmpty()) selectedUuid = players.get(0).uuid();
            } catch (Exception ignored) {}
        }

        public void updateDetail(String data) {
            try {
                detail = JsonParser.parseString(data).getAsJsonObject();
                selectedUuid = detail.get("uuid").getAsString();
                editingNote = null;
                if (noteInput != null) noteInput.setValue("");
            } catch (Exception ignored) {}
            rebuildPlayerButtons();
        }

        @Override
        protected void init() {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            search = new EditBox(font, left + 12, top + 32, 255, 20, Component.literal("Search players"));
            search.setHint(Component.literal("Search online/offline players..."));
            search.setMaxLength(64);
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 555, top + 456, 245, 20, Component.literal("Note"));
            noteInput.setMaxLength(512);
            addRenderableWidget(noteInput);

            addRenderableWidget(Button.builder(Component.literal("Add note"), b -> saveNote())
                    .bounds(left + 805, top + 456, 80, 20).build());

            rebuildPlayerButtons();
            rebuildNoteButtons();
        }

        private void rebuildPlayerButtons() {
            for (Button b : playerButtons) removeWidget(b);
            playerButtons.clear();

            if (search == null) return;

            String query = search.getValue().trim().toLowerCase(Locale.ROOT);
            List<PlayerRef> filtered = players.stream()
                    .filter(p -> query.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(query))
                    .toList();

            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - 1));
            int end = Math.min(filtered.size(), start + 14);

            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int y = top + 62 + (i - start) * 29;
                Button b = Button.builder(
                        Component.literal((p.online() ? "● " : "○ ") + p.name())
                                .withStyle(p.online() ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                        btn -> selectPlayer(p.uuid())
                ).bounds(left + 12, y, 255, 25).build();
                playerButtons.add(b);
                addRenderableWidget(b);
            }
        }

        private void selectPlayer(String uuid) {
            selectedUuid = uuid;
            AdminGuiNetworkSelect.send(uuid);
        }

        private void saveNote() {
            if (detail == null || noteInput.getValue().isBlank()) return;
            String action = editingNote == null ? "add" : "edit";
            String noteId = editingNote == null ? "" : editingNote.toString();
            AdminGuiNetworkNote.send(action, detail.get("uuid").getAsString(), noteId, noteInput.getValue());
            editingNote = null;
            noteInput.setValue("");
        }

        private void editNote(JsonObject note) {
            editingNote = UUID.fromString(note.get("id").getAsString());
            noteInput.setValue(note.get("text").getAsString());
            noteInput.setFocused(true);
        }

        private void removeNote(JsonObject note) {
            AdminGuiNetworkNote.send(
                    "remove",
                    detail.get("uuid").getAsString(),
                    note.get("id").getAsString(),
                    ""
            );
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (search != null && search.isFocused()) {
                boolean result = super.keyPressed(keyCode, scanCode, modifiers);
                rebuildPlayerButtons();
                return result;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            if (mouseX >= left && mouseX <= left + 280 && mouseY >= top + 55 && mouseY <= top + 450) {
                int max = Math.max(0, filteredCount() - 14);
                playerScroll = (int) Math.max(0, Math.min(max, playerScroll - Math.signum(scrollY)));
                rebuildPlayerButtons();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        private int filteredCount() {
            if (search == null) return players.size();
            String q = search.getValue().toLowerCase(Locale.ROOT);
            int n = 0;
            for (PlayerRef p : players) if (q.isBlank() || p.name().toLowerCase(Locale.ROOT).contains(q)) n++;
            return n;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            g.fill(left, top, left + WIDTH, top + HEIGHT, 0xEE111318);
            g.fill(left, top, left + WIDTH, top + 26, 0xFF1C2028);
            g.fill(left + 280, top + 26, left + 282, top + HEIGHT, 0xFF303640);

            g.drawString(font, "ADMIN GUI", left + 12, top + 8, 0xFFFFFFFF);
            g.drawString(font, players.size() + " players", left + 205, top + 8, 0xFF8A9099);

            g.drawString(font, "PLAYERS", left + 12, top + 58, 0xFFB8BEC8);
            if (detail == null) {
                g.drawString(font, "Select a player", left + 305, top + 55, 0xFF9AA0AA);
            } else {
                renderDetail(g, left + 305, top + 42);
            }

            super.render(g, mouseX, mouseY, partialTick);
        }

        private void renderDetail(GuiGraphics g, int x, int y) {
            String name = text(detail, "name", "Unknown");
            boolean online = bool(detail, "online");
            g.drawString(font, name, x, y, 0xFFFFFFFF);
            g.drawString(font, online ? "ONLINE" : "OFFLINE", x + 210, y, online ? 0xFF55DD77 : 0xFF888E98);
            g.drawString(font, detail.get("uuid").getAsString(), x, y + 16, 0xFF777E89);

            int yy = y + 42;
            yy = section(g, "TSA ANTICHEAT", detail.getAsJsonObject("tsa"), x, yy);
            yy = section(g, "AIRPORT SECURITY", detail.getAsJsonObject("ass"), x, yy);
            yy = section(g, "FTB TEAM", detail.getAsJsonObject("teams"), x, yy);
            yy = section(g, "DISCORD", detail.getAsJsonObject("discord"), x, yy);
            section(g, "CLOCK IN", detail.getAsJsonObject("clockin"), x, yy);

            renderNotes(g, x, y + 255);
        }

        private int section(GuiGraphics g, String title, JsonObject o, int x, int y) {
            g.drawString(font, title, x, y, 0xFFD5A84A);
            if (o == null || o.entrySet().isEmpty()) {
                g.drawString(font, "Not installed / no data", x + 120, y, 0xFF666D78);
                return y + 22;
            }
            if (title.equals("TSA ANTICHEAT")) {
                g.drawString(font, "Packets: " + num(o,"packetChecks") + "  Pass: " + num(o,"packetPasses")
                        + "  Modified: " + num(o,"packetModified") + "  Timeout: " + num(o,"packetTimeout"), x + 120, y, 0xFFB8BEC8);
                return y + 22;
            }
            if (title.equals("AIRPORT SECURITY")) {
                g.drawString(font, "Status: " + text(o,"status","UNKNOWN") + "  Checks: " + num(o,"totalChecks")
                        + "  Detected: " + num(o,"detectedChecks") + "  Clean: " + num(o,"cleanChecks"), x + 120, y, 0xFFB8BEC8);
                return y + 22;
            }
            if (title.equals("FTB TEAM")) {
                g.drawString(font, text(o,"name","Team") + "  ID: " + text(o,"id",""), x + 120, y, 0xFFB8BEC8);
                return y + 22;
            }
            if (title.equals("DISCORD")) {
                g.drawString(font, text(o,"displayName",text(o,"discordTag","Linked")), x + 120, y, 0xFFB8BEC8);
                return y + 22;
            }
            g.drawString(font, (bool(o,"clockedIn") ? "CLOCKED IN" : "CLOCKED OUT") + "  Total: " + formatSeconds(num(o,"totalSeconds")), x + 120, y, 0xFFB8BEC8);
            return y + 22;
        }

        private void renderNotes(GuiGraphics g, int x, int y) {
            g.drawString(font, "ADMIN NOTES", x, y, 0xFFD5A84A);
            if (detail == null || !detail.has("notes")) return;
            JsonArray notes = detail.getAsJsonArray("notes");
            int shown = Math.min(notes.size(), 5);
            for (int i = 0; i < shown; i++) {
                JsonObject n = notes.get(i).getAsJsonObject();
                int row = y + 20 + i * 36;
                String author = n.get("author").getAsString();
                String line = n.get("text").getAsString();
                if (line.length() > 66) line = line.substring(0, 63) + "...";
                g.drawString(font, author.isBlank() ? "System" : author, x, row, 0xFF858C97);
                g.drawString(font, line, x, row + 12, 0xFFE1E4E8);

            }
            if (notes.size() > shown) {
                g.drawString(font, "+" + (notes.size() - shown) + " more notes", x, y + 28 + shown * 36, 0xFF666D78);
            }
        }

        private static String text(JsonObject o, String k, String fallback) {
            return o != null && o.has(k) ? o.get(k).getAsString() : fallback;
        }
        private static boolean bool(JsonObject o, String k) {
            return o != null && o.has(k) && o.get(k).getAsBoolean();
        }
        private static long num(JsonObject o, String k) {
            return o != null && o.has(k) ? o.get(k).getAsLong() : 0L;
        }
        private static String formatSeconds(long s) {
            long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
            return h + "h " + String.format("%02dm %02ds", m, sec);
        }

        @Override
        public boolean isPauseScreen() { return false; }

        private record PlayerRef(String uuid, String name, boolean online) {}
    }

    private static final class AdminGuiNetworkSelect {
        static void send(String uuid) {
            Minecraft.getInstance().getConnection().send(
                    new AdminGuiNetwork.SelectPayload(uuid)
            );
        }
    }

    private static final class AdminGuiNetworkNote {
        static void send(String action, String player, String note, String text) {
            Minecraft.getInstance().getConnection().send(
                    new AdminGuiNetwork.NoteActionPayload(action, player, note, text)
            );
        }
    }
}
