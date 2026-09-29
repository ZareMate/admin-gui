package com.zaremate.admin_gui;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
        private final List<PlainTextButton> actionButtons = new ArrayList<>();
        private final List<PlainTextButton> noteButtons = new ArrayList<>();
        private final List<PlainTextButton> infoWidgets = new ArrayList<>();
        private String selectedUuid;
        private JsonObject detail;
        private EditBox search;
        private EditBox noteInput;
        private PlainTextButton addNoteButton;
        private UUID editingNote;
        private int playerScroll;
        private int noteScroll;
        private int tsaScroll;
        private int assScroll;
        private int teamScroll;
        private int discordScroll;

        private static final int WIDTH = 900;
        private static final int HEIGHT = 520;
        private static final int MIN_MARGIN = 12;
        private static final double MAX_SCALE = 2.0;

        // Dark admin-console palette.
        private static final int BG = 0xFF0E1117;
        private static final int PANEL = 0xFF161B22;
        private static final int PANEL_ALT = 0xFF1C2128;
        private static final int BORDER = 0xFF30363D;
        private static final int BORDER_HOVER = 0xFF484F58;
        private static final int TEXT = 0xFFF0F3F6;
        private static final int MUTED = 0xFF8B949E;
        private static final int ACCENT = 0xFF58A6FF;
        private static final int SUCCESS = 0xFF3FB950;
        private static final int WARNING = 0xFFD29922;
        private static final int DANGER = 0xFFF85149;

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
                noteScroll = 0;
                tsaScroll = 0;
                assScroll = 0;
                teamScroll = 0;
                discordScroll = 0;
                editingNote = null;
                if (noteInput != null) noteInput.setValue("");
                boolean notesAvailable = detail.has("notesAvailable") && detail.get("notesAvailable").getAsBoolean();
                if (noteInput != null) noteInput.visible = notesAvailable;
                if (addNoteButton != null) addNoteButton.visible = notesAvailable;
            } catch (Exception ignored) {}
            rebuildPlayerButtons();
            rebuildActionButtons();
            rebuildInfoWidgets();
        }

        @Override
        protected void init() {
            int left = baseLeft();
            int top = baseTop();

            search = new EditBox(font, left + 45, top + 97, 237, 20, Component.literal("Search players"));
            search.setHint(Component.literal("Search players..."));
            search.setMaxLength(64);
            search.setBordered(false);
            search.setTextColor(TEXT);
            search.setTextColorUneditable(MUTED);
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 325, top + 486, 430, 26, Component.literal("Note"));
            noteInput.setMaxLength(512);
            noteInput.setBordered(false);
            noteInput.setTextColor(TEXT);
            noteInput.setTextColorUneditable(MUTED);
            addRenderableWidget(noteInput);

            addNoteButton = new CenteredTextButton(
                    left + 765, top + 486, 110, 26,
                    Component.literal("ADD NOTE").withStyle(s -> s.withColor(0xFFFFFFFF)),
                    b -> saveNote(),
                    font
            );
            addRenderableWidget(addNoteButton);
            noteInput.visible = false;
            addNoteButton.visible = false;

            rebuildPlayerButtons();
            rebuildActionButtons();
            rebuildInfoWidgets();
        }

        private void rebuildPlayerButtons() {
            for (PlainTextButton b : playerButtons) removeWidget(b);
            playerButtons.clear();
            if (search == null) return;

            List<PlayerRef> filtered = filteredPlayers();
            final int visiblePlayers = 9;
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - visiblePlayers));
            int end = Math.min(filtered.size(), start + visiblePlayers);

            int left = baseLeft();
            int top = baseTop();
            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int y = top + 126 + (i - start) * 29;
                PlainTextButton b = new PlayerListButton(
                        left + 24, y, 258, 26, Component.empty(),
                        btn -> selectPlayer(p.uuid()), font);
                b.setTooltip(Tooltip.create(Component.literal(
                        p.online() ? "Online — click to inspect." : "Offline — click to inspect stored data."
                )));
                playerButtons.add(b);
                addRenderableWidget(b);
            }
        }

        private void rebuildActionButtons() {
            for (PlainTextButton b : actionButtons) removeWidget(b);
            actionButtons.clear();

            PlayerRef selected = selectedPlayer();
            boolean hasPlayer = selected != null;
            boolean online = hasPlayer && isPlayerOnline(selected);

            int left = baseLeft();
            int top = baseTop();

            addActionButton(
                    left + 24, top + 402, 82, 21, "PUNISH",
                    hasPlayer,
                    hasPlayer ? "/punish " + selected.name() + " " : "",
                    hasPlayer ? "Insert /punish " + selected.name() + " into chat." : "Select a player first.",
                    true
            );

            addActionButton(
                    left + 112, top + 402, 82, 21, "INVSEE",
                    online,
                    "",
                    online ? "Run /invsee " + selected.name() + "." : "Only available for online players.",
                    false
            );

            addActionButton(
                    left + 200, top + 402, 82, 21, online ? "TP SPEC" : "TP LAST",
                    hasPlayer,
                    "",
                    hasPlayer
                            ? (online ? "Run /tp_spec " + selected.name() + "."
                                      : "Run /teleport_last " + selected.name() + ".")
                            : "Select a player first.",
                    false
            );

            addActionButton(
                    left + 24, top + 425, 82, 21, "KICK",
                    online,
                    online ? "/kick " + selected.name() : "",
                    online ? "Insert /kick " + selected.name() + " into chat."
                           : "Only available for online players.",
                    true
            );

            addActionButton(
                    left + 112, top + 425, 82, 21, "DAMAGE",
                    online,
                    "",
                    online
                            ? "Run /damage " + selected.name() + " 0.1 minecraft:player_attack."
                            : "Only available for online players.",
                    false
            );

            addActionButton(
                    left + 200, top + 425, 82, 21, "MSG",
                    online,
                    online ? "/msg " + selected.name() + " " : "",
                    online
                            ? "Insert /msg " + selected.name() + " into chat."
                            : "Only available for online players.",
                    true
            );
        }

        private void addActionButton(
                int x, int y, int width, int height, String label,
                boolean enabled, String command, String tooltip, boolean insert
        ) {
            PlainTextButton button = new CenteredTextButton(
                    x, y, width, height,
                    Component.literal(label).withStyle(s -> s.withColor(TEXT)),
                    ignored -> {
                        PlayerRef selected = selectedPlayer();
                        if (selected == null) return;

                        boolean online = isPlayerOnline(selected);
                        if ((label.equals("INVSEE") || label.equals("KICK")
                                || label.equals("DAMAGE") || label.equals("MSG")) && !online) {
                            return;
                        }

                        if (label.equals("PUNISH") || label.equals("KICK") || label.equals("MSG")) {
                            openChat(command);
                            return;
                        }

                        String runCommand = switch (label) {
                            case "INVSEE" -> "invsee " + selected.name();
                            case "TP SPEC" -> "tp_spec " + selected.name();
                            case "TP LAST" -> "teleport_last " + selected.name();
                            case "DAMAGE" -> "damage " + selected.name()
                                    + " 0.1 minecraft:player_attack";
                            default -> "";
                        };

                        if (!runCommand.isBlank()) {
                            runClientCommand(runCommand);
                        }
                    },
                    font
            );
            button.active = enabled;
            if (tooltip != null && !tooltip.isBlank()) {
                button.setTooltip(Tooltip.create(Component.literal(tooltip)));
            }
            actionButtons.add(button);
            addRenderableWidget(button);
        }

        private PlayerRef selectedPlayer() {
            if (selectedUuid == null) return null;
            return players.stream()
                    .filter(p -> p.uuid().equals(selectedUuid))
                    .findFirst()
                    .orElse(null);
        }

        private boolean isPlayerOnline(PlayerRef player) {
            if (detail != null && detail.has("uuid")
                    && detail.get("uuid").getAsString().equalsIgnoreCase(player.uuid())) {
                return bool(detail, "online");
            }
            return player.online();
        }

        private void openChat(String command) {
            if (command == null || command.isBlank()) return;
            Minecraft.getInstance().setScreen(new ChatScreen(command));
        }

        private void runClientCommand(String command) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand(command);
            }
        }

        private void rebuildInfoWidgets() {
            for (PlainTextButton widget : infoWidgets) removeWidget(widget);
            infoWidgets.clear();
            AdminGuiDetailWidgets.build(
                    detail, players.size(), baseLeft(), baseTop(), font,
                    tsaScroll, assScroll, teamScroll, discordScroll, 0, noteScroll
            ).forEach(widget -> {
                infoWidgets.add(widget);
                addRenderableWidget(widget);
            });

            for (PlainTextButton button : noteButtons) removeWidget(button);
            noteButtons.clear();

            if (detail == null || !bool(detail, "notesAvailable") || !detail.has("notes")) return;

            JsonArray notes = detail.getAsJsonArray("notes");
            int shown = Math.min(notes.size(), 4);
            int start = Math.min(noteScroll, Math.max(0, notes.size() - shown));
            int left = baseLeft();
            int top = baseTop();

            for (int i = 0; i < shown; i++) {
                JsonObject note = notes.get(start + i).getAsJsonObject();
                int row = top + 346 + i * 34;
                if (canEditNote(note)) {
                    PlainTextButton edit = new PlainTextButton(
                            left + 782, row + 4, 45, 18,
                            Component.literal("EDIT").withStyle(s -> s.withColor(ACCENT)),
                            b -> editNote(note), font);
                    PlainTextButton remove = new PlainTextButton(
                            left + 832, row + 4, 35, 18,
                            Component.literal("DEL").withStyle(s -> s.withColor(DANGER)),
                            b -> removeNote(note), font);
                    edit.setTooltip(Tooltip.create(Component.literal("Edit your note.")));
                    remove.setTooltip(Tooltip.create(Component.literal("Remove your note.")));
                    noteButtons.add(edit);
                    noteButtons.add(remove);
                    addRenderableWidget(edit);
                    addRenderableWidget(remove);
                }
            }
        }

        private void selectPlayer(String uuid) {
            selectedUuid = uuid;
            rebuildActionButtons();
            AdminGuiNetworkSelect.send(uuid);
        }

        private void saveNote() {
            if (detail == null || noteInput.getValue().isBlank()) return;
            String action = editingNote == null ? "add" : "edit";
            String noteId = editingNote == null ? "" : editingNote.toString();
            AdminGuiNetworkNote.send(action, detail.get("uuid").getAsString(), noteId, noteInput.getValue());
            editingNote = null;
            noteInput.setValue("");
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("ADD NOTE").withStyle(s -> s.withColor(TEXT)));
        }

        private void editNote(JsonObject note) {
            editingNote = UUID.fromString(note.get("id").getAsString());
            noteInput.setValue(note.get("text").getAsString());
            if (addNoteButton != null) addNoteButton.setMessage(Component.literal("UPDATE").withStyle(s -> s.withColor(TEXT)));
            noteInput.setFocused(true);
        }

        private void removeNote(JsonObject note) {
            AdminGuiNetworkNote.send("remove", detail.get("uuid").getAsString(), note.get("id").getAsString(), "");
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (search != null && search.isFocused()) {
                boolean result = super.keyPressed(keyCode, scanCode, modifiers);
                rebuildPlayerButtons();
                rebuildActionButtons();
                return result;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            double logicalX = logicalMouseX(mouseX);
            double logicalY = logicalMouseY(mouseY);
            int left = baseLeft();
            int top = baseTop();

            if (logicalX >= left + 12 && logicalX <= left + 292
                    && logicalY >= top + 20 && logicalY <= top + 390) {
                playerScroll = clampScroll(
                        playerScroll - (int) Math.signum(scrollY),
                        Math.max(0, filteredCount() - 9)
                );
                rebuildPlayerButtons();
                return true;
            }

            if (detail != null) {
                int delta = (int) Math.signum(scrollY);
                double rx = logicalX - left;
                double ry = logicalY - top;

                if (rx >= 321 && rx < 593 && ry >= 128 && ry < 214) {
                    tsaScroll = clampScroll(
                            tsaScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TSA, font) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 613 && rx < 885 && ry >= 128 && ry < 214) {
                    assScroll = clampScroll(
                            assScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.ASS, font) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 321 && rx < 593 && ry >= 224 && ry < 302) {
                    teamScroll = clampScroll(
                            teamScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TEAM, font) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 613 && rx < 885 && ry >= 224 && ry < 302) {
                    discordScroll = clampScroll(
                            discordScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.DISCORD, font) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 313 && rx < 885 && ry >= 312 && ry < 480) {
                    int noteCount = detail.has("notes") ? detail.getAsJsonArray("notes").size() : 0;
                    noteScroll = clampScroll(
                            noteScroll - delta,
                            Math.max(0, noteCount - 4)
                    );
                    rebuildInfoWidgets();
                    return true;
                }
            }
            return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return super.mouseClicked(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return super.mouseReleased(logicalMouseX(mouseX), logicalMouseY(mouseY), button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            double scale = uiScale();
            return super.mouseDragged(logicalMouseX(mouseX), logicalMouseY(mouseY), button, dragX / scale, dragY / scale);
        }

        @Override
        public void mouseMoved(double mouseX, double mouseY) {
            super.mouseMoved(logicalMouseX(mouseX), logicalMouseY(mouseY));
        }

        private double uiScale() {
            double horizontal = (width - MIN_MARGIN * 2.0) / WIDTH;
            double vertical = (height - MIN_MARGIN * 2.0) / HEIGHT;
            return Math.max(0.1, Math.min(MAX_SCALE, Math.min(horizontal, vertical)));
        }

        private double logicalMouseX(double mouseX) {
            return width / 2.0 + (mouseX - width / 2.0) / uiScale();
        }

        private double logicalMouseY(double mouseY) {
            return height / 2.0 + (mouseY - height / 2.0) / uiScale();
        }

        private int baseLeft() { return (width - WIDTH) / 2; }
        private int baseTop() { return (height - HEIGHT) / 2; }

        private int clampScroll(int value, int max) { return Math.max(0, Math.min(max, value)); }

        private int filteredCount() { return filteredPlayers().size(); }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            double scale = uiScale();
            int left = baseLeft();
            int top = baseTop();
            double logicalX = logicalMouseX(mouseX);
            double logicalY = logicalMouseY(mouseY);

            g.fill(0, 0, width, height, 0x99000000);

            g.pose().pushPose();
            g.pose().translate(width / 2.0, height / 2.0, 0);
            g.pose().scale((float) scale, (float) scale, 1.0F);
            g.pose().translate(-width / 2.0, -height / 2.0, 0);

            // Main shell
            panel(g, left, top, WIDTH, HEIGHT, BG, BORDER, 1);
            panel(g, left + 1, top + 1, WIDTH - 2, 46, PANEL_ALT, BORDER, 1);
            g.drawString(font, "ADMIN GUI", left + 22, top + 17, TEXT, true);
            g.drawString(font, "Server administration", left + 110, top + 17, MUTED, false);

            // Player sidebar
            panel(g, left + 12, top + 58, 282, 332, PANEL, BORDER, 1);
            g.drawString(font, "PLAYERS", left + 24, top + 69, MUTED, true);
            panel(g, left + 24, top + 94, 258, 26, BG, BORDER, 1);
            drawSearchIcon(g, left + 32, top + 101, MUTED);

            List<PlayerRef> filtered = filteredPlayers();
            for (int i = 0; i < Math.min(10, filtered.size()); i++) {
                PlayerRef p = filtered.get(Math.min(playerScroll + i, filtered.size() - 1));
                int rowY = top + 126 + i * 29;
                boolean hovered = logicalX >= left + 24 && logicalX <= left + 282
                        && logicalY >= rowY && logicalY <= rowY + 26;
                boolean selected = p.uuid().equals(selectedUuid);

                panel(g, left + 24, rowY, 258, 26,
                        selected ? 0xFF1F6FEB : (hovered ? PANEL_ALT : PANEL),
                        selected ? ACCENT : (hovered ? BORDER_HOVER : BORDER),
                        1);
                g.fill(left + 29, rowY + 8, left + 33, rowY + 12, p.online() ? SUCCESS : MUTED);
                g.drawString(font, p.name(), left + 42, rowY + 8, TEXT, false);
            }
            drawScrollBar(g, left + 286, top + 126, 261, filtered.size(), 9, playerScroll, logicalX, logicalY);

            panel(g, left + 12, top + 398, 282, 63, PANEL, BORDER, 1);
            g.drawString(font, "PLAYER ACTIONS", left + 24, top + 404, MUTED, true);

            for (PlainTextButton action : actionButtons) {
                boolean hovered = action.isHoveredOrFocused();
                if (action.active) {
                    buttonSurface(g, action.getX(), action.getY(), action.getWidth(), action.getHeight(), hovered);
                } else {
                    panel(g, action.getX(), action.getY(), action.getWidth(), action.getHeight(),
                            PANEL, BORDER, 1);
                }
            }

            // Detail area
            panel(g, left + 305, top + 58, 580, 422, BG, BORDER, 1);
            if (detail == null) {
                g.drawString(font, "Select a player", left + 330, top + 90, TEXT, true);
                g.drawString(font, "Player information will appear here.", left + 330, top + 110, MUTED, false);
            } else {
                // Cards are drawn as flat surfaces; detail widgets only render text/actions.
                card(g, left + 321, top + 128, 272, 86, "TSA ANTICHEAT");
                card(g, left + 613, top + 128, 272, 86, "AIRPORT SECURITY");
                card(g, left + 321, top + 224, 272, 78, "FTB TEAM");
                card(g, left + 613, top + 224, 272, 78, "DISCORD");
                drawCardScrollBar(g, left + 586, top + 158, 48,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TSA, font),
                        3, tsaScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 878, top + 158, 48,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.ASS, font),
                        3, assScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 586, top + 254, 42,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TEAM, font),
                        3, teamScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 878, top + 254, 42,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.DISCORD, font),
                        3, discordScroll, logicalX, logicalY);
                panel(g, left + 313, top + 312, 562, 168, PANEL, BORDER, 1);
                g.drawString(font, "ADMIN NOTES", left + 325, top + 323, MUTED, false);
                drawScrollBar(g, left + 863, top + 342, 126,
                        detail.has("notes") ? detail.getAsJsonArray("notes").size() : 0, 4, noteScroll, logicalX, logicalY);
            }

            renderWidgetIfVisible(g, search, logicalX, logicalY, partialTick);
            for (PlainTextButton button : playerButtons) renderWidgetIfVisible(g, button, logicalX, logicalY, partialTick);
            for (PlainTextButton action : actionButtons) renderWidgetIfVisible(g, action, logicalX, logicalY, partialTick);
            for (PlainTextButton widget : infoWidgets) renderWidgetIfVisible(g, widget, logicalX, logicalY, partialTick);
            for (PlainTextButton button : noteButtons) renderWidgetIfVisible(g, button, logicalX, logicalY, partialTick);

            if (noteInput != null && noteInput.visible) {
                panel(g, left + 325, top + 486, 430, 26, PANEL_ALT, BORDER, 1);
                buttonSurface(g, left + 765, top + 486, 110, 26, false);
            }
            renderWidgetIfVisible(g, noteInput, logicalX, logicalY, partialTick);
            renderWidgetIfVisible(g, addNoteButton, logicalX, logicalY, partialTick);

            g.pose().popPose();
        }

        private void renderWidgetIfVisible(GuiGraphics g, net.minecraft.client.gui.components.AbstractWidget widget,
                                           double mouseX, double mouseY, float partialTick) {
            if (widget != null && widget.visible) {
                widget.render(g, (int) Math.round(mouseX), (int) Math.round(mouseY), partialTick);
            }
        }

        private boolean canEditNote(JsonObject note) {
            if (note == null || !note.has("authorUuid")) return false;
            if (note.has("system") && note.get("system").getAsBoolean()) return false;
            if (note.has("canEdit")) return note.get("canEdit").getAsBoolean();
            return Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.getUUID().toString().equalsIgnoreCase(note.get("authorUuid").getAsString());
        }

        private List<PlayerRef> filteredPlayers() {
            if (search == null) return players;
            String query = search.getValue().trim().toLowerCase(Locale.ROOT);
            return players.stream().filter(p -> query.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        private void panel(GuiGraphics g, int x, int y, int width, int height, int fill, int border, int thickness) {
            g.fill(x, y, x + width, y + height, fill);
            g.fill(x, y, x + width, y + thickness, border);
            g.fill(x, y + height - thickness, x + width, y + height, border);
            g.fill(x, y, x + thickness, y + height, border);
            g.fill(x + width - thickness, y, x + width, y + height, border);
        }

        private void card(GuiGraphics g, int x, int y, int width, int height, String title) {
            panel(g, x, y, width, height, PANEL, BORDER, 1);
            g.fill(x + 1, y + 1, x + 4, y + height - 1, ACCENT);
            g.drawString(font, title, x + 12, y + 9, TEXT, false);
        }

        private void statusPill(GuiGraphics g, int x, int y, String label, int color) {
            int w = font.width(label) + 16;
            panel(g, x, y, w, 18, 0xFF111820, color, 1);
            g.drawString(font, label, x + 8, y + 5, color, true);
        }

        private void buttonSurface(GuiGraphics g, int x, int y, int width, int height, boolean hovered) {
            panel(g, x, y, width, height, hovered ? 0xFF1F6FEB : 0xFF238636,
                    hovered ? ACCENT : 0xFF2EA043, 1);
        }

        private void drawSearchIcon(GuiGraphics g, int x, int y, int color) {
            g.fill(x, y, x + 7, y + 1, color);
            g.fill(x, y + 1, x + 1, y + 7, color);
            g.fill(x + 1, y + 7, x + 6, y + 8, color);
            g.fill(x + 6, y + 5, x + 7, y + 7, color);
            g.fill(x + 7, y + 7, x + 10, y + 9, color);
        }

        private void drawCardScrollBar(GuiGraphics g, int x, int y, int height,
                                         int total, int visible, int offset,
                                         double mouseX, double mouseY) {
            if (total <= visible || height <= 0) return;

            g.fill(x, y, x + 4, y + height, 0xFF21262D);
            int maxOffset = total - visible;
            int thumbHeight = Math.max(10, height * visible / total);
            int travel = Math.max(0, height - thumbHeight);
            int thumbY = y + (travel * clampScroll(offset, maxOffset) / Math.max(1, maxOffset));

            boolean hovered = mouseX >= x - 3 && mouseX <= x + 7
                    && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            g.fill(x, thumbY, x + 4, thumbY + thumbHeight,
                    hovered ? BORDER_HOVER : MUTED);
        }

        private void drawScrollBar(GuiGraphics g, int x, int y, int height,
                                   int total, int visible, int offset, double mouseX, double mouseY) {
            if (total <= visible || height <= 0) return;
            g.fill(x, y, x + 4, y + height, 0xFF21262D);
            int maxOffset = total - visible;
            int thumbHeight = Math.max(18, height * visible / total);
            int travel = Math.max(0, height - thumbHeight);
            int thumbY = y + (travel * clampScroll(offset, maxOffset) / Math.max(1, maxOffset));
            boolean hovered = mouseX >= x - 3 && mouseX <= x + 8 && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
            g.fill(x, thumbY, x + 4, thumbY + thumbHeight, hovered ? BORDER_HOVER : MUTED);
        }

        private static String fit(String value, int maxWidth, Font font) {
            if (value == null || value.isEmpty() || font.width(value) <= maxWidth) return value == null ? "" : value;
            String ellipsis = "...";
            int available = Math.max(1, maxWidth - font.width(ellipsis));
            return font.plainSubstrByWidth(value, available) + ellipsis;
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

    private static final class PlayerListButton extends PlainTextButton {
        private PlayerListButton(
                int x,
                int y,
                int width,
                int height,
                Component text,
                Button.OnPress onPress,
                Font font
        ) {
            super(x, y, width, height, text, onPress, font);
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int color) {
            Component message = getMessage();
            int textX = getX() + 18;
            int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
            g.drawString(font, message, textX, textY, color, false);
        }
    }

    private static final class CenteredTextButton extends PlainTextButton {
        private CenteredTextButton(
                int x,
                int y,
                int width,
                int height,
                Component text,
                Button.OnPress onPress,
                Font font
        ) {
            super(x, y, width, height, text, onPress, font);
        }

        @Override
        public void renderString(GuiGraphics g, Font font, int color) {
            Component message = getMessage();
            int textWidth = font.width(message);
            int textX = getX() + Math.max(0, (getWidth() - textWidth) / 2);
            int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
            g.drawString(font, message, textX, textY, color, false);
        }
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
