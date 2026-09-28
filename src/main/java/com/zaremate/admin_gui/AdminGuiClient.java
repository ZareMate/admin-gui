package com.zaremate.admin_gui;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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
        private final List<PlainTextButton> playerButtons = new ArrayList<>();
        private final List<PlainTextButton> noteButtons = new ArrayList<>();
        private final List<PlainTextButton> infoWidgets = new ArrayList<>();
        private String selectedUuid;
        private JsonObject detail;
        private EditBox search;
        private EditBox noteInput;
        private PlainTextButton addNoteButton;
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
                boolean notesAvailable = detail.has("notesAvailable") && detail.get("notesAvailable").getAsBoolean();
                if (noteInput != null) noteInput.visible = notesAvailable;
                if (addNoteButton != null) addNoteButton.visible = notesAvailable;
            } catch (Exception ignored) {}
            rebuildPlayerButtons();
            rebuildInfoWidgets();
        }

        @Override
        protected void init() {
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            search = new EditBox(font, left + 12, top + 32, 255, 20, Component.literal("Search players"));
            search.setHint(Component.literal("Search online/offline players..."));
            search.setMaxLength(64);
            search.setBordered(false);
            search.setTextColor(0xFFE5DED0);
            search.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 555, top + 456, 245, 20, Component.literal("Note"));
            noteInput.setMaxLength(512);
            noteInput.setBordered(false);
            noteInput.setTextColor(0xFFE5DED0);
            noteInput.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(noteInput);

            addNoteButton = new PlainTextButton(
                    left + 805, top + 456, 80, 20,
                    Component.literal("ADD NOTE").withStyle(s -> s.withColor(0xFFE0B05A)),
                    b -> saveNote(),
                    font
            );
            addRenderableWidget(addNoteButton);
            noteInput.visible = false;
            addNoteButton.visible = false;

            rebuildPlayerButtons();
            rebuildInfoWidgets();
        }

        private void rebuildPlayerButtons() {
            for (PlainTextButton b : playerButtons) removeWidget(b);
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
                Component label = Component.literal((p.online() ? "● " : "○ ") + p.name())
                        .withStyle(s -> s.withColor(p.online() ? 0xFF72C96B : 0xFF9A9A91));

                PlainTextButton b = new PlainTextButton(
                        left + 12, y, 255, 25, label,
                        btn -> selectPlayer(p.uuid()),
                        font
                );
                b.setTooltip(Tooltip.create(Component.literal(
                        p.online()
                                ? "Online — click to inspect this player."
                                : "Offline — click to inspect stored player data."
                )));
                playerButtons.add(b);
                addRenderableWidget(b);
            }
        }

        private void rebuildInfoWidgets() {
            for (PlainTextButton widget : infoWidgets) {
                removeWidget(widget);
            }
            infoWidgets.clear();
            AdminGuiDetailWidgets.build(detail, players.size(), (width - WIDTH) / 2, (height - HEIGHT) / 2, font)
                    .forEach(widget -> {
                        infoWidgets.add(widget);
                        addRenderableWidget(widget);
                    });

            for (PlainTextButton button : noteButtons) {
                removeWidget(button);
            }
            noteButtons.clear();

            if (detail == null || !detail.has("notesAvailable")
                    || !detail.get("notesAvailable").getAsBoolean()
                    || !detail.has("notes")) {
                return;
            }

            JsonArray notes = detail.getAsJsonArray("notes");
            int shown = Math.min(notes.size(), 3);
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int x = left + 305;
            int y = top + 298 + 28;

            for (int i = 0; i < shown; i++) {
                JsonObject note = notes.get(i).getAsJsonObject();
                int row = y + i * 36;
                PlainTextButton edit = new PlainTextButton(
                        x + 440, row - 1, 45, 18,
                        Component.literal("EDIT").withStyle(s -> s.withColor(0xFFE0B05A)),
                        b -> editNote(note), font);
                PlainTextButton remove = new PlainTextButton(
                        x + 490, row - 1, 20, 18,
                        Component.literal("×").withStyle(s -> s.withColor(0xFFC66A54)),
                        b -> removeNote(note), font);
                edit.setTooltip(Tooltip.create(Component.literal("Edit this note.")));
                remove.setTooltip(Tooltip.create(Component.literal("Remove this note.")));
                noteButtons.add(edit);
                noteButtons.add(remove);
                addRenderableWidget(edit);
                addRenderableWidget(remove);
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
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("ADD NOTE").withStyle(s -> s.withColor(0xFFE0B05A)));
        }

        private void editNote(JsonObject note) {
            editingNote = UUID.fromString(note.get("id").getAsString());
            noteInput.setValue(note.get("text").getAsString());
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("UPDATE").withStyle(s -> s.withColor(0xFFE0B05A)));
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
            // Fully opaque custom background: do not blur the world behind the GUI.
            renderBackground(g, mouseX, mouseY, partialTick);

            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int right = left + WIDTH;
            int bottom = top + HEIGHT;

            // Create-inspired industrial backdrop.
            g.fill(0, 0, width, height, 0xFF101210);

            // Subtle horizontal panel bands.
            for (int y = 0; y < height; y += 24) {
                g.fill(0, y, width, y + 1, 0xFF171917);
            }

            // Main brass/steel frame.
            g.fill(left - 3, top - 3, right + 3, bottom + 3, 0xFF765336);
            g.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF0E100F);
            g.fill(left, top, right, bottom, 0xFF202322);

            // Header.
            g.fill(left, top, right, top + 27, 0xFF191B1A);
            g.fill(left, top + 25, right, top + 27, 0xFFB2763F);

            // Player panel divider.
            g.fill(left + 280, top + 27, left + 282, bottom, 0xFF8D6946);

            // Search field.
            drawBrassFrame(g, left + 11, top + 31, 257, 22);
            g.fill(left + 13, top + 33, left + 266, top + 51, 0xFF0B0D0C);

            // Player row plates behind the real widgets.
            List<PlayerRef> filtered = filteredPlayers();
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - 1));
            int end = Math.min(filtered.size(), start + 14);

            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int rowY = top + 62 + (i - start) * 29;
                boolean hovered = mouseX >= left + 12 && mouseX <= left + 267
                        && mouseY >= rowY && mouseY <= rowY + 25;
                boolean selected = p.uuid().equals(selectedUuid);

                int plate = selected ? 0xFF4B3B29
                        : hovered ? 0xFF30332F
                        : 0xFF242725;

                g.fill(left + 12, rowY, left + 267, rowY + 25, plate);
                g.fill(left + 12, rowY + 24, left + 267, rowY + 25,
                        selected || hovered ? 0xFFC1844B : 0xFF454844);

                if (selected) {
                    g.fill(left + 12, rowY, left + 15, rowY + 25, 0xFFE0A15F);
                }
            }

            // Detail panels.
            if (detail != null) {
                drawCreateCard(g, left + 313, top + 94, 270, 80);
                drawCreateCard(g, left + 605, top + 94, 270, 80);
                drawCreateCard(g, left + 313, top + 182, 270, 62);
                drawCreateCard(g, left + 605, top + 182, 270, 62);
                drawCreateCard(g, left + 313, top + 252, 270, 45);
                drawCreateCard(g, left + 313, top + 298, 562, 145);

                if (noteInput != null && noteInput.visible) {
                    drawBrassFrame(g, left + 552, top + 453, 251, 26);
                    drawCopperButtonFrame(
                            g,
                            left + 803,
                            top + 453,
                            84,
                            26,
                            addNoteButton != null && addNoteButton.isHoveredOrFocused()
                    );
                }
            }

            // Render the actual interactive widgets last.
            renderWidgetIfVisible(g, search, mouseX, mouseY, partialTick);
            for (PlainTextButton button : playerButtons) {
                renderWidgetIfVisible(g, button, mouseX, mouseY, partialTick);
            }
            for (PlainTextButton widget : infoWidgets) {
                renderWidgetIfVisible(g, widget, mouseX, mouseY, partialTick);
            }
            for (PlainTextButton button : noteButtons) {
                renderWidgetIfVisible(g, button, mouseX, mouseY, partialTick);
            }
            renderWidgetIfVisible(g, noteInput, mouseX, mouseY, partialTick);
            renderWidgetIfVisible(g, addNoteButton, mouseX, mouseY, partialTick);
        }

        private void renderWidgetIfVisible(
                GuiGraphics g,
                net.minecraft.client.gui.components.AbstractWidget widget,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            if (widget != null && widget.visible) {
                widget.render(g, mouseX, mouseY, partialTick);
            }
        }

        private List<PlayerRef> filteredPlayers() {
            if (search == null) {
                return players;
            }

            String query = search.getValue().trim().toLowerCase(Locale.ROOT);
            return players.stream()
                    .filter(p -> query.isEmpty()
                            || p.name().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }

        private void drawCreateCard(GuiGraphics g, int x, int y, int width, int height) {
            g.fill(x, y, x + width, y + height, 0xFF1B1E1D);
            g.fill(x, y, x + width, y + 1, 0xFFB2763F);
            g.fill(x, y + 1, x + 1, y + height, 0xFF574534);
            g.fill(x + width - 1, y + 1, x + width, y + height, 0xFF574534);
            drawRivet(g, x + 4, y + 4);
            drawRivet(g, x + width - 7, y + 4);
        }

        private void drawBrassFrame(GuiGraphics g, int x, int y, int width, int height) {
            g.fill(x, y, x + width, y + 1, 0xFFC1844B);
            g.fill(x, y + height - 1, x + width, y + height, 0xFF65472F);
            g.fill(x, y, x + 1, y + height, 0xFF8D6946);
            g.fill(x + width - 1, y, x + width, y + height, 0xFF8D6946);
        }

        private void drawCopperButtonFrame(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height,
                boolean hovered
        ) {
            int edge = hovered ? 0xFFE3A866 : 0xFFB2763F;
            g.fill(x, y, x + width, y + 1, edge);
            g.fill(x, y + height - 1, x + width, y + height, 0xFF65472F);
            g.fill(x, y, x + 1, y + height, 0xFF8D6946);
            g.fill(x + width - 1, y, x + width, y + height, 0xFF8D6946);
        }

        private void drawRivet(GuiGraphics g, int x, int y) {
            g.fill(x, y, x + 3, y + 3, 0xFF9A9B91);
            g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF4D4F4C);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // Admin GUI intentionally uses a solid background instead of
            // Minecraft's blurred in-world screen background.
            g.fill(0, 0, width, height, 0xFF101210);
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
