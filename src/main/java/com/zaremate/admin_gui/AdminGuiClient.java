package com.zaremate.admin_gui;

import com.google.gson.*;
import net.minecraft.ChatFormatting;
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
        private final List<PlainTextButton> infoWidgets = new ArrayList<>();
        private String selectedUuid;
        private JsonObject detail;
        private EditBox search;
        private EditBox noteInput;
        private Button addNoteButton;
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
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 555, top + 456, 245, 20, Component.literal("Note"));
            noteInput.setMaxLength(512);
            addRenderableWidget(noteInput);

            addNoteButton = Button.builder(Component.literal("Add note"), b -> saveNote())
                    .bounds(left + 805, top + 456, 80, 20).build();
            addRenderableWidget(addNoteButton);
            noteInput.visible = false;
            addNoteButton.visible = false;

            rebuildPlayerButtons();
            rebuildInfoWidgets();
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
                b.setTooltip(Tooltip.create(Component.literal(
                        p.online() ? "Online — click to view administration data." : "Offline — click to view stored administration data.")));
                playerButtons.add(b);
                addRenderableWidget(b);
            }
        }

        private void rebuildNoteButtons() {
            for (Button b : noteButtons) removeWidget(b);
            noteButtons.clear();

            if (detail == null || !detail.has("notesAvailable")
                    || !detail.get("notesAvailable").getAsBoolean()
                    || !detail.has("notes")) {
                return;
            }

            JsonArray notes = detail.getAsJsonArray("notes");
            int shown = Math.min(notes.size(), 3);

            int x = (width - WIDTH) / 2 + 305;
            int y = (height - HEIGHT) / 2 + 42 + 260;

            for (int i = 0; i < shown; i++) {
                JsonObject note = notes.get(i).getAsJsonObject();
                int row = y + i * 44;

                Button edit = Button.builder(Component.literal("Edit"), b -> editNote(note))
                        .bounds(x + 450, row - 2, 45, 18)
                        .build();
                Button remove = Button.builder(Component.literal("X"), b -> removeNote(note))
                        .bounds(x + 500, row - 2, 20, 18)
                        .build();

                noteButtons.add(edit);
                noteButtons.add(remove);
                addRenderableWidget(edit);
                addRenderableWidget(remove);
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

            for (Button button : noteButtons) {
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
                Button edit = Button.builder(Component.literal("Edit"), b -> editNote(note))
                        .bounds(x + 440, row - 1, 45, 18).build();
                Button remove = Button.builder(Component.literal("X"), b -> removeNote(note))
                        .bounds(x + 490, row - 1, 20, 18).build();
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
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("Add note"));
        }

        private void editNote(JsonObject note) {
            editingNote = UUID.fromString(note.get("id").getAsString());
            noteInput.setValue(note.get("text").getAsString());
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("Update"));
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
            renderBackground(g, mouseX, mouseY, partialTick);

            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;

            g.fill(left, top, left + WIDTH, top + HEIGHT, 0xEE111318);
            g.fill(left, top, left + WIDTH, top + 26, 0xFF1C2028);
            g.fill(left + 280, top + 26, left + 282, top + HEIGHT, 0xFF303640);

            if (detail != null) {
                drawCardBackground(g, left + 313, top + 94, 270, 80);
                drawCardBackground(g, left + 605, top + 94, 270, 80);
                drawCardBackground(g, left + 313, top + 182, 270, 62);
                drawCardBackground(g, left + 605, top + 182, 270, 62);
                drawCardBackground(g, left + 313, top + 252, 270, 45);
                drawCardBackground(g, left + 313, top + 298, 562, 145);
            }

            super.render(g, mouseX, mouseY, partialTick);
        }

        private void drawCardBackground(GuiGraphics g, int x, int y, int width, int height) {
            g.fill(x, y, x + width, y + height, 0xAA191D24);
            g.fill(x, y, x + width, y + 1, 0xFF3A404A);
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
