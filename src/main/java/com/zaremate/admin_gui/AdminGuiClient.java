package com.zaremate.admin_gui;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
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
        private boolean suppressSearch;

        private static final int WIDTH = 900;
        private static final int HEIGHT = 520;
        private static final int MIN_MARGIN = 12;
        private static final double MAX_SCALE = 2.0;
        // Each major GUI component has its own texture so resource packs can
        // reskin the interface independently.
        private static final ResourceLocation BACKGROUND_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/background.png");
        private static final ResourceLocation BORDER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/border.png");
        private static final ResourceLocation HEADER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/header.png");
        private static final ResourceLocation PLAYER_ITEM_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_item.png");
        private static final ResourceLocation PLAYER_ITEM_SELECTED_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_item_selected.png");
        private static final ResourceLocation WIDGET_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget.png");
        private static final ResourceLocation WIDGET_FRAME_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget_frame.png");
        private static final ResourceLocation NOTE_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note.png");
        private static final ResourceLocation BUTTON_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/button.png");
        private static final ResourceLocation BUTTON_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/button_hover.png");
        private static final ResourceLocation INPUT_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/input.png");
        private static final ResourceLocation SCROLLBAR_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/scrollbar.png");

        private static final ResourceLocation GUI_BACKGROUND_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/background.png");
        private static final ResourceLocation PLAYER_LIST_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_list.png");
        private static final ResourceLocation PLAYER_ENTRY_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_entry.png");
        private static final ResourceLocation PLAYER_ENTRY_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_entry_hover.png");
        private static final ResourceLocation PLAYER_ENTRY_SELECTED_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_item_selected.png");
        private static final ResourceLocation WIDGET_270X88_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget_270x88.png");
        private static final ResourceLocation WIDGET_270X80_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget_270x80.png");
        private static final ResourceLocation WIDGET_270X45_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget_270x45.png");
        private static final ResourceLocation WIDGET_ENTRY_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/widget_entry.png");
        private static final ResourceLocation NOTES_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/notes.png");
        private static final ResourceLocation NOTE_ENTRY_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_entry.png");
        private static final ResourceLocation NOTE_ENTRY_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_entry_hover.png");
        private static final ResourceLocation NOTE_EDIT_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_edit.png");
        private static final ResourceLocation NOTE_EDIT_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_edit_hover.png");
        private static final ResourceLocation NOTE_REMOVE_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_remove.png");
        private static final ResourceLocation NOTE_REMOVE_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_remove_hover.png");
        private static final ResourceLocation PLAYER_SCROLL_TRACK_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_scroll_track.png");
        private static final ResourceLocation PLAYER_SCROLL_THUMB_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_scroll_thumb.png");
        private static final ResourceLocation PLAYER_SCROLL_THUMB_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/player_scroll_thumb_hover.png");
        private static final ResourceLocation NOTES_SCROLL_TRACK_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/notes_scroll_track.png");
        private static final ResourceLocation NOTES_SCROLL_THUMB_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/notes_scroll_thumb.png");
        private static final ResourceLocation NOTES_SCROLL_THUMB_HOVER_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/notes_scroll_thumb_hover.png");
        private static final ResourceLocation NOTE_INPUT_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/note_input.png");

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

            search = new EditBox(font, left + 18, top + 37, 252, 20, Component.literal("Search players"));
            search.setHint(Component.literal("Search online/offline players..."));
            search.setMaxLength(64);
            search.setBordered(false);
            search.setTextColor(0xFFE5DED0);
            search.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 325, top + 490, 254, 20, Component.literal("Note"));
            noteInput.setMaxLength(512);
            noteInput.setBordered(false);
            noteInput.setTextColor(0xFFE5DED0);
            noteInput.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(noteInput);

            addNoteButton = new PlainTextButton(
                    left + 585, top + 490, 84, 20,
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
            int visiblePlayers = 10;
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - visiblePlayers));
            int end = Math.min(filtered.size(), start + visiblePlayers);

            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int y = top + 70 + (i - start) * 32;
                Component label = Component.literal((p.online() ? "● " : "○ ") + p.name())
                        .withStyle(s -> s.withColor(p.online() ? 0xFF72C96B : 0xFF9A9A91));

                PlainTextButton b = new PlainTextButton(
                        left + 16, y, 256, 27, label,
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
                if (widget.visible && widget.getY() >= top + 90) {
                    boolean hovered = logicalMouseX >= widget.getX()
                            && logicalMouseX <= widget.getX() + widget.getWidth()
                            && logicalMouseY >= widget.getY()
                            && logicalMouseY <= widget.getY() + widget.getHeight();

                    // Headers already live inside their full widget background.
                    boolean widgetHeader = widget.getY() == top + 101
                            || widget.getY() == top + 197
                            || widget.getY() == top + 285
                            || widget.getY() == top + 344;

                    if (!widgetHeader && widget.getWidth() == 405) {
                        drawExactTexture(
                                g,
                                hovered ? NOTE_ENTRY_HOVER_TEXTURE : NOTE_ENTRY_TEXTURE,
                                widget.getX(),
                                widget.getY(),
                                405,
                                26,
                                405,
                                26
                        );
                    } else if (!widgetHeader && widget.getWidth() <= 260) {
                        drawExactTexture(
                                g,
                                WIDGET_ENTRY_TEXTURE,
                                widget.getX(),
                                widget.getY(),
                                Math.min(widget.getWidth(), 254),
                                widget.getHeight(),
                                254,
                                18
                        );
                    }
                }
                renderWidgetIfVisible(g, widget, logicalMouseX, logicalMouseY, partialTick);
            }

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
            int start = Math.min(noteScroll, Math.max(0, notes.size() - shown));
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int x = left + 325;
            int y = top + 331 + 50;

            for (int i = 0; i < shown; i++) {
                JsonObject note = notes.get(start + i).getAsJsonObject();
                int row = y + i * 36;
                if (canEditNote(note)) {
                    PlainTextButton edit = new PlainTextButton(
                            x + 415, row - 1, 45, 18,
                            Component.literal("EDIT").withStyle(s -> s.withColor(0xFF5A4028)),
                            b -> editNote(note), font);
                    PlainTextButton remove = new PlainTextButton(
                            x + 465, row - 1, 20, 18,
                            Component.literal("×").withStyle(s -> s.withColor(0xFFC43E32)),
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
            double logicalX = logicalMouseX(mouseX);
            double logicalY = logicalMouseY(mouseY);
            int left = baseLeft();
            int top = baseTop();

            // Scroll the player list independently.
            if (logicalX >= left && logicalX <= left + 280
                    && logicalY >= top + 55 && logicalY <= top + 450) {
                int max = Math.max(0, filteredCount() - 14);
                playerScroll = clampScroll(
                        playerScroll - (int) Math.signum(scrollY),
                        max
                );
                rebuildPlayerButtons();
                return true;
            }

            // Scroll admin notes independently.
            if (detail != null
                    && logicalX >= left + 305
                    && logicalX <= left + 875
                    && logicalY >= top + 331
                    && logicalY <= top + 448) {
                int noteCount = detail.has("notes")
                        ? detail.getAsJsonArray("notes").size()
                        : 0;
                int max = Math.max(0, noteCount - 3);
                noteScroll = clampScroll(
                        noteScroll - (int) Math.signum(scrollY),
                        max
                );
                rebuildInfoWidgets();
                return true;
            }

            return super.mouseScrolled(logicalX, logicalY, scrollX, scrollY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return super.mouseClicked(
                    logicalMouseX(mouseX),
                    logicalMouseY(mouseY),
                    button
            );
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            return super.mouseReleased(
                    logicalMouseX(mouseX),
                    logicalMouseY(mouseY),
                    button
            );
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            double scale = uiScale();
            return super.mouseDragged(
                    logicalMouseX(mouseX),
                    logicalMouseY(mouseY),
                    button,
                    dragX / scale,
                    dragY / scale
            );
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

        private int baseLeft() {
            return (width - WIDTH) / 2;
        }

        private int baseTop() {
            return (height - HEIGHT) / 2;
        }

        private int clampScroll(int value, int max) {
            return Math.max(0, Math.min(max, value));
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
            // Fully opaque background; no world blur.
            renderBackground(g, mouseX, mouseY, partialTick);

            double scale = uiScale();
            int centerX = width / 2;
            int centerY = height / 2;
            int left = baseLeft();
            int top = baseTop();
            int right = left + WIDTH;
            int bottom = top + HEIGHT;

            double logicalMouseX = logicalMouseX(mouseX);
            double logicalMouseY = logicalMouseY(mouseY);

            g.pose().pushPose();
            g.pose().translate(centerX, centerY, 0);
            g.pose().scale((float) scale, (float) scale, 1.0F);
            g.pose().translate(-centerX, -centerY, 0);

            // Complete 900x520 clipboard background. The texture already contains
            // the transparent outside area and brass/wood border.
            drawExactTexture(g, GUI_BACKGROUND_TEXTURE, left, top, WIDTH, HEIGHT, 900, 520);

            // Complete player-list component, including its search-box frame.
            drawExactTexture(g, PLAYER_LIST_TEXTURE, left + 4, top + 20, 280, 375, 280, 375);

            // Player rows.
            List<PlayerRef> filtered = filteredPlayers();
            final int visiblePlayers = 10;
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - visiblePlayers));
            int end = Math.min(filtered.size(), start + visiblePlayers);

            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int rowY = top + 70 + (i - start) * 32;
                boolean hovered = logicalMouseX >= left + 16 && logicalMouseX <= left + 272
                        && logicalMouseY >= rowY && logicalMouseY <= rowY + 27;
                boolean selected = p.uuid().equals(selectedUuid);

                drawPostIt(
                        g,
                        left + 16,
                        rowY,
                        256,
                        27,
                        selected,
                        hovered,
                        i
                );
            }

            // Player list scrollbar.
            drawScrollBar(
                    g,
                    left + 272,
                    top + 70,
                    290,
                    filtered.size(),
                    10,
                    playerScroll,
                    logicalMouseX,
                    logicalMouseY
            );

            // Detail panels.
            if (detail != null) {
                drawCreateCard(g, left + 313, top + 94, 270, 88);
                drawCreateCard(g, left + 605, top + 94, 270, 88);
                drawCreateCard(g, left + 313, top + 190, 270, 80);
                drawCreateCard(g, left + 605, top + 190, 270, 80);
                drawCreateCard(g, left + 313, top + 278, 270, 45);
                drawExactTexture(g, NOTES_TEXTURE, left + 313, top + 331, 562, 156, 562, 156);

                int noteCount = detail.has("notes")
                        ? detail.getAsJsonArray("notes").size()
                        : 0;
                drawScrollBar(
                        g,
                        left + 862,
                        top + 379,
                        78,
                        noteCount,
                        3,
                        noteScroll,
                        logicalMouseX,
                        logicalMouseY
                );

                if (noteInput != null && noteInput.visible) {
                    drawExactTexture(g, NOTE_INPUT_TEXTURE, left + 325, top + 490, 254, 26, 254, 26);
                    drawCopperButtonFrame(
                            g,
                            left + 585,
                            top + 490,
                            84,
                            26,
                            addNoteButton != null && addNoteButton.isHoveredOrFocused()
                    );
                }
            }

            // All widgets use the logical/base coordinates above. Render them
            // with inverse-transformed mouse coordinates so hover and tooltips
            // continue to work at every window size.
            renderWidgetIfVisible(g, search, logicalMouseX, logicalMouseY, partialTick);

            for (PlainTextButton button : playerButtons) {
                renderWidgetIfVisible(g, button, logicalMouseX, logicalMouseY, partialTick);
            }

            for (PlainTextButton widget : infoWidgets) {
                if (widget.visible && widget.getY() >= top + 90) {
                    boolean hovered = logicalMouseX >= widget.getX()
                            && logicalMouseX <= widget.getX() + widget.getWidth()
                            && logicalMouseY >= widget.getY()
                            && logicalMouseY <= widget.getY() + widget.getHeight();

                    if (widget.getWidth() <= 260) {
                        drawTexturePanel(
                                g,
                                WIDGET_TEXTURE,
                                widget.getX(),
                                widget.getY(),
                                widget.getWidth(),
                                widget.getHeight()
                        );
                    } else if (widget.getY() >= top + 350 && widget.getWidth() >= 400) {
                        drawAdminNote(
                                g,
                                widget.getX(),
                                widget.getY(),
                                widget.getWidth(),
                                widget.getHeight(),
                                hovered,
                                widget.getY()
                        );
                    }
                }
                renderWidgetIfVisible(g, widget, logicalMouseX, logicalMouseY, partialTick);
            }

            for (PlainTextButton button : noteButtons) {
                if (button.visible) {
                    boolean hovered = logicalMouseX >= button.getX()
                            && logicalMouseX <= button.getX() + button.getWidth()
                            && logicalMouseY >= button.getY()
                            && logicalMouseY <= button.getY() + button.getHeight();
                    ResourceLocation actionTexture = button.getWidth() == 45
                            ? (hovered ? NOTE_EDIT_HOVER_TEXTURE : NOTE_EDIT_TEXTURE)
                            : (hovered ? NOTE_REMOVE_HOVER_TEXTURE : NOTE_REMOVE_TEXTURE);
                    drawExactTexture(
                            g,
                            actionTexture,
                            button.getX(),
                            button.getY(),
                            button.getWidth(),
                            button.getHeight(),
                            button.getWidth(),
                            button.getHeight()
                    );
                }
                renderWidgetIfVisible(g, button, logicalMouseX, logicalMouseY, partialTick);
            }

            renderWidgetIfVisible(g, noteInput, logicalMouseX, logicalMouseY, partialTick);
            renderWidgetIfVisible(g, addNoteButton, logicalMouseX, logicalMouseY, partialTick);

            g.pose().popPose();
        }

        private void renderWidgetIfVisible(
                GuiGraphics g,
                net.minecraft.client.gui.components.AbstractWidget widget,
                double mouseX,
                double mouseY,
                float partialTick
        ) {
            if (widget != null && widget.visible) {
                widget.render(g, (int) Math.round(mouseX), (int) Math.round(mouseY), partialTick);
            }
        }

        private boolean canEditNote(JsonObject note) {
            if (note == null || !note.has("authorUuid")) {
                return false;
            }

            if (note.has("system") && note.get("system").getAsBoolean()) {
                return false;
            }

            if (note.has("canEdit")) {
                return note.get("canEdit").getAsBoolean();
            }

            return Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.getUUID().toString()
                    .equalsIgnoreCase(note.get("authorUuid").getAsString());
        }

        private void drawPostIt(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height,
                boolean selected,
                boolean hovered,
                int variant
        ) {
            ResourceLocation texture = selected
                    ? PLAYER_ENTRY_SELECTED_TEXTURE
                    : (hovered ? PLAYER_ENTRY_HOVER_TEXTURE : PLAYER_ENTRY_TEXTURE);
            drawExactTexture(g, texture, x, y, width, height, 256, 27);
        }

        private void drawAdminNote(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height,
                boolean hovered,
                int variant
        ) {
            drawExactTexture(
                    g,
                    hovered ? NOTE_ENTRY_HOVER_TEXTURE : NOTE_ENTRY_TEXTURE,
                    x,
                    y,
                    width,
                    height,
                    405,
                    26
            );
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

        private void drawScrollBar(
                GuiGraphics g,
                int x,
                int y,
                int height,
                int total,
                int visible,
                int offset,
                double mouseX,
                double mouseY
        ) {
            if (total <= visible || height <= 0) {
                return;
            }

            boolean notes = height <= 100;
            ResourceLocation track = notes ? NOTES_SCROLL_TRACK_TEXTURE : PLAYER_SCROLL_TRACK_TEXTURE;
            ResourceLocation thumb = notes ? NOTES_SCROLL_THUMB_TEXTURE : PLAYER_SCROLL_THUMB_TEXTURE;
            ResourceLocation thumbHover = notes ? NOTES_SCROLL_THUMB_HOVER_TEXTURE : PLAYER_SCROLL_THUMB_HOVER_TEXTURE;

            int trackWidth = 8;
            drawExactTexture(
                    g,
                    track,
                    x,
                    y,
                    trackWidth,
                    height,
                    8,
                    notes ? 78 : 290
            );

            int maxOffset = total - visible;
            int thumbHeight = Math.max(12, height * visible / total);
            int travel = height - thumbHeight;
            int thumbY = y + (travel * clampScroll(offset, maxOffset) / maxOffset);
            boolean hovered = mouseX >= x && mouseX <= x + trackWidth
                    && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;

            drawExactTexture(
                    g,
                    hovered ? thumbHover : thumb,
                    x,
                    thumbY,
                    trackWidth,
                    thumbHeight,
                    8,
                    notes ? 26 : 42
            );
        }

        private void drawCreateCard(GuiGraphics g, int x, int y, int width, int height) {
            ResourceLocation texture;
            if (width == 270 && height == 88) {
                texture = WIDGET_270X88_TEXTURE;
            } else if (width == 270 && height == 80) {
                texture = WIDGET_270X80_TEXTURE;
            } else {
                texture = WIDGET_270X45_TEXTURE;
            }
            drawExactTexture(g, texture, x, y, width, height, width, height);
        }

        private void drawExactTexture(
                GuiGraphics g,
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height,
                int textureWidth,
                int textureHeight
        ) {
            g.blit(
                    texture,
                    x,
                    y,
                    0,
                    0,
                    width,
                    height,
                    textureWidth,
                    textureHeight
            );
        }

        private void drawTexturePanel(
                GuiGraphics g,
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height
        ) {
            drawExactTexture(g, texture, x, y, width, height, 32, 32);
        }

        private void drawTexturePanel(
                GuiGraphics g,
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height
        ) {
            // All normal GUI components use a 32x32 nine-slice texture.
            // Corners remain native-size; edges and center adapt to the
            // destination size. This gives us full component textures instead
            // of a 16x16 pattern being stamped over the whole widget.
            final int size = 32;
            final int slice = 6;
            int centerW = Math.max(0, width - slice * 2);
            int centerH = Math.max(0, height - slice * 2);
            int sourceCenter = size - slice * 2;

            // Center.
            if (centerW > 0 && centerH > 0) {
                g.blit(texture, x + slice, y + slice, centerW, centerH,
                        slice, slice, sourceCenter, sourceCenter, size, size);
            }

            // Top / bottom.
            if (centerW > 0) {
                g.blit(texture, x + slice, y, centerW, slice,
                        slice, 0, sourceCenter, slice, size, size);
                g.blit(texture, x + slice, y + height - slice, centerW, slice,
                        slice, size - slice, sourceCenter, slice, size, size);
            }

            // Left / right.
            if (centerH > 0) {
                g.blit(texture, x, y + slice, slice, centerH,
                        0, slice, slice, sourceCenter, size, size);
                g.blit(texture, x + width - slice, y + slice, slice, centerH,
                        size - slice, slice, slice, sourceCenter, size, size);
            }

            // Corners.
            g.blit(texture, x, y, slice, slice,
                    0, 0, slice, slice, size, size);
            g.blit(texture, x + width - slice, y, slice, slice,
                    size - slice, 0, slice, slice, size, size);
            g.blit(texture, x, y + height - slice, slice, slice,
                    0, size - slice, slice, slice, size, size);
            g.blit(texture, x + width - slice, y + height - slice, slice, slice,
                    size - slice, size - slice, slice, slice, size, size);
        }

        private void drawCopperButtonFrame(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height,
                boolean hovered
        ) {
            drawExactTexture(
                    g,
                    hovered ? BUTTON_HOVER_TEXTURE : BUTTON_TEXTURE,
                    x, y, width, height, 32, 32
            );
        }

        private void drawRivet(GuiGraphics g, int x, int y) {
            g.fill(x, y, x + 3, y + 3, 0xFF9A9B91);
            g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF4D4F4C);
        }

        @Override
        public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // Leave the world/background untouched outside the clipboard.
            // The GUI surface itself is drawn inside render().
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
