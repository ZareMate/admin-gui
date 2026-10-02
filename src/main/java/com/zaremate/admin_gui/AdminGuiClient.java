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

import javax.annotation.Nonnull;

import java.util.*;

public final class AdminGuiClient {
    private static AdminGuiScreen screen;

    private AdminGuiClient() {}

    public static void open(String data) {
        Minecraft.getInstance().execute(() -> {
            screen = new AdminGuiScreen(data);
            Minecraft.getInstance().setScreen(screen);

            if (screen.selectedUuid != null) {
                AdminGuiNetworkSelect.send(screen.selectedUuid);
            }
        });
    }

    public static void detail(String data) {
        Minecraft.getInstance().execute(() -> {
            if (screen != null) screen.updateDetail(data);
        });
    }

    public static void listUpdate(String data) {
        Minecraft.getInstance().execute(() -> {
            if (screen != null) screen.updatePlayerList(data);
        });
    }

    public static final class AdminGuiScreen extends Screen {
        private final List<PlayerRef> players = new ArrayList<>();
        private final List<PlainTextButton> playerButtons = new ArrayList<>();
        private final List<Button> actionButtons = new ArrayList<>();
        private final List<PlainTextButton> noteButtons = new ArrayList<>();
        private final List<PlainTextButton> infoWidgets = new ArrayList<>();
        private String selectedUuid;
        private JsonObject detail;
        private EditBox search;
        private EditBox noteInput;
        private Button addNoteButton;
        private UUID editingNote;
        private int playerScroll;
        private int noteScroll;
        private int tsaScroll;
        private int assScroll;
        private int teamScroll;
        private int discordScroll;
        private int punishScroll;
        private boolean switchingToPunish;

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
        private static final int DANGER = 0xFFF85149;

        public AdminGuiScreen(String data) {
            super(literal("Admin GUI"));
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
                JsonObject next = JsonParser.parseString(data).getAsJsonObject();
                String nextUuid = next.get("uuid").getAsString();
                boolean selectionChanged = !Objects.equals(selectedUuid, nextUuid);

                detail = next;
                selectedUuid = nextUuid;

                if (selectionChanged) {
                    noteScroll = 0;
                    tsaScroll = 0;
                    assScroll = 0;
                    teamScroll = 0;
                    discordScroll = 0;
                    punishScroll = 0;
                    editingNote = null;
                    if (noteInput != null) noteInput.setValue("");
                }

                boolean notesAvailable = detail.has("notesAvailable")
                        && detail.get("notesAvailable").getAsBoolean();
                if (noteInput != null) noteInput.visible = notesAvailable;
                if (addNoteButton != null) addNoteButton.visible = notesAvailable;
            } catch (Exception ignored) {}

            rebuildPlayerButtons();
            rebuildActionButtons();
            rebuildInfoWidgets();
        }

        public void updatePlayerList(String data) {
            String previousSelection = selectedUuid;
            try {
                JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                List<PlayerRef> refreshed = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("players")) {
                    JsonObject p = e.getAsJsonObject();
                    refreshed.add(new PlayerRef(
                            p.get("uuid").getAsString(),
                            p.get("name").getAsString(),
                            p.has("online") && p.get("online").getAsBoolean()
                    ));
                }

                players.clear();
                players.addAll(refreshed);

                // Never replace the selected UUID just because the list changed.
                selectedUuid = previousSelection;

                int maxScroll = Math.max(0, filteredCount() - 9);
                playerScroll = clampScroll(playerScroll, maxScroll);

                rebuildPlayerButtons();
                rebuildActionButtons();
            } catch (Exception ignored) {}
        }

        @Override
        protected void init() {
            switchingToPunish = false;
            int left = baseLeft();
            int top = baseTop();

            Font guiFont = nonNullFont(font);
            search = new EditBox(guiFont, left + 51, top + 106, 231, 20, literal("Search players"));
            search.setHint(literal("Search players..."));
            search.setMaxLength(64);
            search.setBordered(false);
            search.setTextColor(TEXT);
            search.setTextColorUneditable(MUTED);
            addRenderableWidget(Objects.requireNonNull(search));

            noteInput = new EditBox(guiFont, left + 331, top + 495, 424, 26, literal("Note"));
            noteInput.setMaxLength(512);
            noteInput.setBordered(false);
            noteInput.setTextColor(TEXT);
            noteInput.setTextColorUneditable(MUTED);
            addRenderableWidget(Objects.requireNonNull(noteInput));

            addNoteButton = new CenteredTextButton(
                    left + 765, top + 486, 110, 26,
                    colored("ADD NOTE", 0xFFFFFFFF),
                    b -> saveNote()
            );
            addRenderableWidget(Objects.requireNonNull(addNoteButton));
            noteInput.visible = false;
            addNoteButton.visible = false;

            rebuildPlayerButtons();
            rebuildActionButtons();
            rebuildInfoWidgets();
        }

        private void rebuildPlayerButtons() {
            for (PlainTextButton b : playerButtons) removeWidget(Objects.requireNonNull(b));
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
                        left + 24, y, 258, 26, literal(""),
                        btn -> selectPlayer(p.uuid()), nonNullFont(font));
                b.setTooltip(Tooltip.create(literal(
                        p.online() ? "Online — click to inspect." : "Offline — click to inspect stored data."
                )));
                playerButtons.add(b);
                addRenderableWidget(Objects.requireNonNull(b));
            }
        }

        private void rebuildActionButtons() {
            for (Button b : actionButtons) removeWidget(Objects.requireNonNull(b));
            actionButtons.clear();

            PlayerRef selected = selectedPlayer();
            boolean hasPlayer = selected != null;
            boolean online = selected != null && isPlayerOnline(selected);
            String selectedName = selected == null ? "" : selected.name();
            JsonObject punishData = detail == null ? null : detail.getAsJsonObject("punish");
            boolean punishAvailable = hasPlayer
                    && punishData != null
                    && bool(punishData, "available")
                    && punishData.has("offenses")
                    && !punishData.getAsJsonArray("offenses").isEmpty();

            int left = baseLeft();
            int top = baseTop();

            addActionButton(
                    left + 24, top + 411, 82, 21, "PUNISH",
                    punishAvailable,
                    "",
                    punishAvailable
                            ? "Select an offense to punish " + selectedName + "."
                            : (hasPlayer ? "Punish mod is not installed / no offenses are available." : "Select a player first."),
                    false
            );

            addActionButton(
                    left + 112, top + 411, 82, 21, "INVSEE",
                    online,
                    "",
                    online ? "Run /invsee " + selectedName + "." : "Only available for online players.",
                    false
            );

            addActionButton(
                    left + 200, top + 411, 82, 21, online ? "TP SPEC" : "TP LAST",
                    hasPlayer,
                    "",
                    hasPlayer
                            ? (online ? "Run /tp_spec " + selectedName + "."
                                      : "Run /teleport_last " + selectedName + ".")
                            : "Select a player first.",
                    false
            );

            addActionButton(
                    left + 24, top + 434, 82, 21, "KICK",
                    online,
                    online ? "/kick " + selectedName : "",
                    online ? "Insert /kick " + selectedName + " into chat."
                           : "Only available for online players.",
                    true
            );

            addActionButton(
                    left + 112, top + 434, 82, 21, "DAMAGE",
                    online,
                    "",
                    online
                            ? "Run /damage " + selectedName + " 0.1 minecraft:player_attack."
                            : "Only available for online players.",
                    false
            );

            addActionButton(
                    left + 200, top + 434, 82, 21, "MSG",
                    online,
                    online ? "/msg " + selectedName + " " : "",
                    online
                            ? "Insert /msg " + selectedName + " into chat."
                            : "Only available for online players.",
                    true
            );
        }

        private void addActionButton(
                int x, int y, int width, int height, @Nonnull String label,
                boolean enabled, @Nonnull String command, @Nonnull String tooltip, boolean insert
        ) {
            Button button = new CenteredTextButton(
                    x, y, width, height,
                    colored(label, TEXT),
                    ignored -> {
                        PlayerRef selected = selectedPlayer();
                        if (selected == null) return;

                        boolean online = isPlayerOnline(selected);
                        if ((label.equals("INVSEE") || label.equals("KICK")
                                || label.equals("DAMAGE") || label.equals("MSG")) && !online) {
                            return;
                        }

                        if (label.equals("PUNISH")) {
                            openPunishModal();
                            return;
                        }

                        if (label.equals("KICK") || label.equals("MSG")) {
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
                    }
            );
            button.active = enabled;
            if (tooltip != null && !tooltip.isBlank()) {
                button.setTooltip(Tooltip.create(literal(tooltip)));
            }
            actionButtons.add(button);
            addRenderableWidget(Objects.requireNonNull(button));
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

        private static boolean bool(JsonObject object, String key) {
            return object != null && object.has(key) && object.get(key).getAsBoolean();
        }


        private void openChat(@Nonnull String command) {
            if (command.isBlank()) return;
            String safeCommand = Objects.requireNonNull(command);
            Minecraft.getInstance().setScreen(new ChatScreen(safeCommand));
        }

        private void runClientCommand(@Nonnull String command) {
            Minecraft minecraft = Minecraft.getInstance();
            var connection = minecraft.getConnection();
            if (minecraft.player != null && connection != null) {
                connection.sendCommand(command);
            }
        }

        private void rebuildInfoWidgets() {
            for (PlainTextButton widget : infoWidgets) removeWidget(Objects.requireNonNull(widget));
            infoWidgets.clear();
            AdminGuiDetailWidgets.build(
                    detail, players.size(), baseLeft(), baseTop(), nonNullFont(font),
                    tsaScroll, assScroll, teamScroll, discordScroll, punishScroll, 0, noteScroll
            ).forEach(widget -> {
                infoWidgets.add(widget);
                addRenderableWidget(Objects.requireNonNull(widget));
            });

            for (PlainTextButton button : noteButtons) removeWidget(Objects.requireNonNull(button));
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
                            left + 817, row + 4, 32, 18,
                            colored("EDIT", ACCENT),
                            b -> editNote(note), nonNullFont(font));
                    PlainTextButton remove = new PlainTextButton(
                            left + 853, row + 4, 27, 18,
                            colored("DEL", DANGER),
                            b -> removeNote(note), nonNullFont(font));
                    edit.setTooltip(Tooltip.create(literal("Edit your note.")));
                    remove.setTooltip(Tooltip.create(literal("Remove your note.")));
                    noteButtons.add(edit);
                    noteButtons.add(remove);
                    addRenderableWidget(Objects.requireNonNull(edit));
                    addRenderableWidget(Objects.requireNonNull(remove));
                }
            }
        }

        private void openPunishModal() {
            if (detail == null || !detail.has("punish")) return;
            JsonObject punish = detail.getAsJsonObject("punish");
            if (punish == null || !bool(punish, "available")) return;

            JsonArray offenses = punish.getAsJsonArray("offenses");
            if (offenses == null || offenses.isEmpty()) return;

            PlayerRef selected = selectedPlayer();
            if (selected == null) return;

            // Switching to the modal removes this screen temporarily. Do not send
            // CLOSE in removed(), otherwise the server forgets which player is selected.
            switchingToPunish = true;
            Minecraft.getInstance().setScreen(new AdminGuiPunishScreen(
                    this,
                    selected.uuid(),
                    selected.name(),
                    offenses
            ));
        }

        private void selectPlayer(@Nonnull String uuid) {
            selectedUuid = uuid;
            rebuildActionButtons();
            AdminGuiNetworkSelect.send(uuid);
        }

        private void saveNote() {
            if (detail == null) return;
            EditBox input = Objects.requireNonNull(noteInput);
            String value = input.getValue();
            if (value.isBlank()) return;

            String action = editingNote == null ? "add" : "edit";
            String noteId = editingNote == null ? "" : editingNote.toString();
            AdminGuiNetworkNote.send(action, detail.get("uuid").getAsString(), noteId, value);
            editingNote = null;
            input.setValue("");
            if (addNoteButton != null) {
                Objects.requireNonNull(addNoteButton).setMessage(colored("ADD NOTE", TEXT));
            }
        }

        private void editNote(JsonObject note) {
            editingNote = UUID.fromString(note.get("id").getAsString());
            EditBox input = Objects.requireNonNull(noteInput);
            input.setValue(note.get("text").getAsString());
            if (addNoteButton != null) {
                Objects.requireNonNull(addNoteButton).setMessage(colored("UPDATE", TEXT));
            }
            input.setFocused(true);
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
            Font guiFont = nonNullFont(font);
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
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TSA, guiFont) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 613 && rx < 885 && ry >= 128 && ry < 214) {
                    assScroll = clampScroll(
                            assScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.ASS, guiFont) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 321 && rx < 593 && ry >= 224 && ry < 302) {
                    teamScroll = clampScroll(
                            teamScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TEAM, guiFont) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 613 && rx < 885 && ry >= 224 && ry < 302) {
                    discordScroll = clampScroll(
                            discordScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.DISCORD, guiFont) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 313 && rx < 593 && ry >= 312 && ry < 480) {
                    punishScroll = clampScroll(
                            punishScroll - delta,
                            Math.max(0, AdminGuiDetailWidgets.lineCount(
                                    detail, AdminGuiDetailWidgets.Section.PUNISH, guiFont) - 3)
                    );
                    rebuildInfoWidgets();
                    return true;
                }

                if (rx >= 593 && rx < 885 && ry >= 312 && ry < 480) {
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
        public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font guiFont = nonNullFont(font);
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
            g.drawString(guiFont, "ADMIN GUI", left + 22, top + 17, TEXT, true);
            g.drawString(guiFont, "Server administration", left + 110, top + 17, MUTED, false);

            // Player sidebar
            panel(g, left + 12, top + 58, 282, 332, PANEL, BORDER, 1);
            g.drawString(guiFont, "PLAYERS", left + 24, top + 69, MUTED, true);
            panel(g, left + 24, top + 94, 258, 26, BG, BORDER, 1);
            drawSearchIcon(g, left + 32, top + 101, MUTED);

            List<PlayerRef> filtered = filteredPlayers();
            for (int i = 0; i < Math.min(9, filtered.size()); i++) {
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
                g.drawString(guiFont, p.name(), left + 42, rowY + 8, TEXT, false);
            }
            drawScrollBar(g, left + 286, top + 126, 261, filtered.size(), 9, playerScroll, logicalX, logicalY);

            panel(g, left + 12, top + 399, 282, 62, PANEL, BORDER, 1);

            for (Button action : actionButtons) {
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
                g.drawString(guiFont, "Select a player", left + 330, top + 90, TEXT, true);
                g.drawString(guiFont, "Player information will appear here.", left + 330, top + 110, MUTED, false);
            } else {
                // Cards are drawn as flat surfaces; detail widgets only render text/actions.
                card(g, left + 321, top + 128, 272, 86, "TSA ANTICHEAT");
                card(g, left + 613, top + 128, 272, 86, "AIRPORT SECURITY");
                card(g, left + 321, top + 224, 272, 78, "FTB TEAM");
                card(g, left + 613, top + 224, 272, 78, "DISCORD");
                drawCardScrollBar(g, left + 586, top + 158, 48,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TSA, guiFont),
                        3, tsaScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 878, top + 158, 48,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.ASS, guiFont),
                        3, assScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 586, top + 254, 42,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.TEAM, guiFont),
                        3, teamScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 878, top + 254, 42,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.DISCORD, guiFont),
                        3, discordScroll, logicalX, logicalY);
                card(g, left + 321, top + 312, 272, 168, "RECORDED OFFENSES");
                card(g, left + 613, top + 312, 272, 168, "ADMIN NOTES");

                drawCardScrollBar(g, left + 586, top + 342, 126,
                        AdminGuiDetailWidgets.lineCount(detail, AdminGuiDetailWidgets.Section.PUNISH, guiFont),
                        3, punishScroll, logicalX, logicalY);
                drawCardScrollBar(g, left + 878, top + 342, 126,
                        detail.has("notes") ? detail.getAsJsonArray("notes").size() : 0,
                        4, noteScroll, logicalX, logicalY);
            }

            renderWidgetIfVisible(g, search, logicalX, logicalY, partialTick);
            for (PlainTextButton button : playerButtons) renderWidgetIfVisible(g, button, logicalX, logicalY, partialTick);
            for (Button action : actionButtons) renderWidgetIfVisible(g, action, logicalX, logicalY, partialTick);
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

        private void renderWidgetIfVisible(@Nonnull GuiGraphics g, net.minecraft.client.gui.components.AbstractWidget widget,
                                           double mouseX, double mouseY, float partialTick) {
            if (widget != null && widget.visible) {
                widget.render(g, (int) Math.round(mouseX), (int) Math.round(mouseY), partialTick);
            }
        }

        private boolean canEditNote(JsonObject note) {
            if (note == null || !note.has("authorUuid")) return false;
            if (note.has("system") && note.get("system").getAsBoolean()) return false;
            if (note.has("canEdit")) return note.get("canEdit").getAsBoolean();
            var player = Minecraft.getInstance().player;
            return player != null
                    && player.getUUID().toString().equalsIgnoreCase(note.get("authorUuid").getAsString());
        }

        private List<PlayerRef> filteredPlayers() {
            if (search == null) return players;
            String query = search.getValue().trim().toLowerCase(Locale.ROOT);
            return players.stream().filter(p -> query.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        private void panel(@Nonnull GuiGraphics g, int x, int y, int width, int height, int fill, int border, int thickness) {
            g.fill(x, y, x + width, y + height, fill);
            g.fill(x, y, x + width, y + thickness, border);
            g.fill(x, y + height - thickness, x + width, y + height, border);
            g.fill(x, y, x + thickness, y + height, border);
            g.fill(x + width - thickness, y, x + width, y + height, border);
        }

        private void card(@Nonnull GuiGraphics g, int x, int y, int width, int height, @Nonnull String title) {
            panel(g, x, y, width, height, PANEL, BORDER, 1);
            g.fill(x + 1, y + 1, x + 4, y + height - 1, ACCENT);
            g.drawString(nonNullFont(font), title, x + 12, y + 9, TEXT, false);
        }


        private void buttonSurface(@Nonnull GuiGraphics g, int x, int y, int width, int height, boolean hovered) {
            panel(g, x, y, width, height, hovered ? 0xFF1F6FEB : 0xFF238636,
                    hovered ? ACCENT : 0xFF2EA043, 1);
        }

        private void drawSearchIcon(@Nonnull GuiGraphics g, int x, int y, int color) {
            // Small pixel-art magnifying glass.
            g.fill(x + 2, y, x + 7, y + 1, color);
            g.fill(x + 1, y + 1, x + 8, y + 2, color);
            g.fill(x, y + 2, x + 1, y + 7, color);
            g.fill(x + 1, y + 7, x + 2, y + 8, color);
            g.fill(x + 2, y + 8, x + 7, y + 9, color);
            g.fill(x + 7, y + 7, x + 8, y + 8, color);
            g.fill(x + 8, y + 6, x + 9, y + 7, color);
            g.fill(x + 9, y + 7, x + 11, y + 9, color);
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


        @Override
        public void removed() {
            if (!switchingToPunish) {
                AdminGuiNetworkClose.send();
            }
            super.removed();
        }

        @Override
        public boolean isPauseScreen() { return false; }

        private record PlayerRef(String uuid, String name, boolean online) {}
    }

    private static Component literal(@Nonnull String text) {
        return Objects.requireNonNull(Component.literal(text));
    }

    private static Component colored(@Nonnull String text, int color) {
        return Objects.requireNonNull(Component.literal(text).withStyle(style -> style.withColor(color)));
    }

    private static Font nonNullFont(Font font) {
        return Objects.requireNonNull(font, "Screen font is not initialized");
    }

    private static final class PlayerListButton extends PlainTextButton {
        private PlayerListButton(
                int x,
                int y,
                int width,
                int height,
                @Nonnull Component text,
                @Nonnull Button.OnPress onPress,
                @Nonnull Font font
        ) {
            super(x, y, width, height, text, onPress, font);
        }

        @Override
        public void renderString(@Nonnull GuiGraphics g, @Nonnull Font font, int color) {
            Component message = Objects.requireNonNull(getMessage());
            int textX = getX() + 18;
            int textY = getY() + Math.max(0, (getHeight() - font.lineHeight) / 2);
            g.drawString(font, message, textX, textY, color, false);
        }
    }

    private static final class CenteredTextButton extends Button {
        private CenteredTextButton(
                int x,
                int y,
                int width,
                int height,
                @Nonnull Component text,
                @Nonnull Button.OnPress onPress
        ) {
            super(x, y, width, height, text, onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;

            Component message = Objects.requireNonNull(getMessage());
            Font guiFont = Objects.requireNonNull(Minecraft.getInstance().font, "Minecraft font is not initialized");
            int textWidth = guiFont.width(message);
            int textX = getX() + (getWidth() - textWidth) / 2;
            int textY = getY() + (getHeight() - guiFont.lineHeight) / 2;

            int color = 0xFFFFFFFF;
            if (!active) {
                color = 0xFF6E7681;
            } else {
                var messageColor = message.getStyle().getColor();
                if (messageColor != null) {
                    color = messageColor.getValue();
                }
            }

            g.drawString(guiFont, message, textX, textY, color, false);
        }
    }

    private static final class AdminGuiNetworkPunish {
        static void send(@Nonnull String playerUuid, @Nonnull String offense) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new AdminGuiNetwork.PunishActionPayload(playerUuid, offense));
            }
        }
    }

    private static final class AdminGuiNetworkClose {
        static void send() {
            Minecraft minecraft = Minecraft.getInstance();
            var connection = minecraft.getConnection();
            if (connection != null) {
                connection.send(new AdminGuiNetwork.ClosePayload());
            }
        }
    }

    private static final class AdminGuiNetworkSelect {
        static void send(@Nonnull String uuid) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new AdminGuiNetwork.SelectPayload(uuid));
            }
        }
    }

    private static final class AdminGuiNetworkNote {
        static void send(
                @Nonnull String action,
                @Nonnull String player,
                @Nonnull String note,
                @Nonnull String text
        ) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new AdminGuiNetwork.NoteActionPayload(action, player, note, text));
            }
        }
    }
}
