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

            search = new EditBox(font, left + 12, top + 32, 255, 20, Component.literal("Search players"));
            search.setHint(Component.literal("Search online/offline players..."));
            search.setMaxLength(64);
            search.setBordered(false);
            search.setTextColor(0xFFE5DED0);
            search.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(search);

            noteInput = new EditBox(font, left + 523, top + 456, 250, 20, Component.literal("Note"));
            noteInput.setMaxLength(512);
            noteInput.setBordered(false);
            noteInput.setTextColor(0xFFE5DED0);
            noteInput.setTextColorUneditable(0xFF77776F);
            addRenderableWidget(noteInput);

            addNoteButton = new PlainTextButton(
                    left + 779, top + 456, 84, 20,
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
                int y = top + 64 + (i - start) * 29;
                Component label = Component.literal((p.online() ? "● " : "○ ") + p.name())
                        .withStyle(s -> s.withColor(p.online() ? 0xFF72C96B : 0xFF9A9A91));

                PlainTextButton b = new PlainTextButton(
                        left + 14, y, 251, 21, label,
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
            AdminGuiDetailWidgets.build(
                    detail,
                    players.size(),
                    (width - WIDTH) / 2,
                    (height - HEIGHT) / 2,
                    font,
                    noteScroll
            )
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
            int start = Math.min(noteScroll, Math.max(0, notes.size() - shown));
            int left = (width - WIDTH) / 2;
            int top = (height - HEIGHT) / 2;
            int x = left + 325;
            int y = top + 331 + 28;

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

            // Cardboard is the actual dashboard surface. Keep the supplied
            // clipboard texture unchanged, but place a rounded brass trim
            // behind it so the brass sits on the outside of the wooden border.
            drawCardboardTextureScaled2x(g, left, top, WIDTH, HEIGHT);
            drawTexturePanel(g, BORDER_TEXTURE, left, top, WIDTH, HEIGHT);

            // Dedicated top/header surface.
            drawTexturePanel(g, HEADER_TEXTURE, left + 12, top + 4, WIDTH - 24, 24);

            // Player divider.
            g.fill(left + 280, top + 27, left + 282, bottom, 0xFF704B2E);

            // Search field.
            drawTexturePanel(g, INPUT_TEXTURE, left + 11, top + 31, 257, 22);

            // Player rows.
            List<PlayerRef> filtered = filteredPlayers();
            int start = Math.min(playerScroll, Math.max(0, filtered.size() - 1));
            int end = Math.min(filtered.size(), start + 14);

            for (int i = start; i < end; i++) {
                PlayerRef p = filtered.get(i);
                int rowY = top + 62 + (i - start) * 29;
                boolean hovered = logicalMouseX >= left + 12 && logicalMouseX <= left + 267
                        && logicalMouseY >= rowY && logicalMouseY <= rowY + 25;
                boolean selected = p.uuid().equals(selectedUuid);

                drawPostIt(
                        g,
                        left + 12,
                        rowY,
                        255,
                        25,
                        selected,
                        hovered,
                        i
                );
            }

            // Player list scrollbar.
            drawScrollBar(
                    g,
                    left + 271,
                    top + 62,
                    14 * 29 - 4,
                    filtered.size(),
                    14,
                    playerScroll
            );

            // Detail panels.
            if (detail != null) {
                drawCreateCard(g, left + 313, top + 94, 270, 88);
                drawCreateCard(g, left + 605, top + 94, 270, 88);
                drawCreateCard(g, left + 313, top + 190, 270, 80);
                drawCreateCard(g, left + 605, top + 190, 270, 80);
                drawCreateCard(g, left + 313, top + 278, 270, 45);
                drawCreateCard(g, left + 313, top + 331, 562, 112);

                int noteCount = detail.has("notes")
                        ? detail.getAsJsonArray("notes").size()
                        : 0;
                drawScrollBar(
                        g,
                        left + 862,
                        top + 359,
                        78,
                        noteCount,
                        3,
                        noteScroll
                );

                if (noteInput != null && noteInput.visible) {
                    drawTexturePanel(g, INPUT_TEXTURE, left + 521, top + 453, 254, 26);
                    drawCopperButtonFrame(
                            g,
                            left + 777,
                            top + 453,
                            88,
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
                    drawTexturePanel(
                            g,
                            hovered ? BUTTON_HOVER_TEXTURE : BUTTON_TEXTURE,
                            button.getX(),
                            button.getY(),
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

        private void drawCardboardTextureScaled2x(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height
        ) {
            final int tile = 32;
            int textureAreaWidth = (width + 1) / 2;
            int textureAreaHeight = (height + 1) / 2;

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);

            for (int yy = 0; yy < textureAreaHeight; yy += tile) {
                for (int xx = 0; xx < textureAreaWidth; xx += tile) {
                    int drawWidth = Math.min(tile, textureAreaWidth - xx);
                    int drawHeight = Math.min(tile, textureAreaHeight - yy);

                    g.blit(
                            BACKGROUND_TEXTURE,
                            xx,
                            yy,
                            0,
                            0,
                            drawWidth,
                            drawHeight,
                            tile,
                            tile
                    );
                }
            }

            g.pose().popPose();
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
            // Player rows have their own texture. Hover uses the same base
            // texture so there is no hard-coded recolour to fight a resource pack.
            drawTexturePanel(
                    g,
                    selected ? PLAYER_ITEM_SELECTED_TEXTURE : PLAYER_ITEM_TEXTURE,
                    x,
                    y,
                    width,
                    height
            );

            if (hovered) {
                g.fill(x, y, x + width, y + 1, 0x28FFFFFF);
            }
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
            drawTexturePanel(g, NOTE_TEXTURE, x, y, width, height);

            if (hovered) {
                g.fill(x, y, x + width, y + 1, 0x26FFFFFF);
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

        private void drawScrollBar(
                GuiGraphics g,
                int x,
                int y,
                int height,
                int total,
                int visible,
                int offset
        ) {
            if (total <= visible || height <= 0) {
                return;
            }

            int trackX = x + 3;
            int trackWidth = 6;
            drawTiledTexture(g, SCROLLBAR_TEXTURE, trackX - 1, y, trackWidth, height, 8, 32);

            int maxOffset = total - visible;
            int thumbHeight = Math.max(10, height * visible / total);
            int travel = height - thumbHeight;
            int thumbY = y + (travel * clampScroll(offset, maxOffset) / maxOffset);

            drawTiledTexture(g, SCROLLBAR_TEXTURE, trackX - 1, thumbY, trackWidth, thumbHeight, 8, 32);
        }

        private void drawCreateCard(GuiGraphics g, int x, int y, int width, int height) {
            // Complete panel frame is one 9-slice texture, just like a
            // vanilla-style GUI component. Its center is transparent.
            drawTexturePanel(g, WIDGET_FRAME_TEXTURE, x, y, width, height);
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

        private void drawTiledTexture(
                GuiGraphics g,
                ResourceLocation texture,
                int x,
                int y,
                int width,
                int height,
                int textureWidth,
                int textureHeight
        ) {
            for (int yy = 0; yy < height; yy += textureHeight) {
                for (int xx = 0; xx < width; xx += textureWidth) {
                    int w = Math.min(textureWidth, width - xx);
                    int h = Math.min(textureHeight, height - yy);
                    g.blit(texture, x + xx, y + yy, w, h,
                            0, 0, w, h, textureWidth, textureHeight);
                }
            }
        }

        private void drawBrassFrame(GuiGraphics g, int x, int y, int width, int height) {
            drawTexturePanel(g, INPUT_TEXTURE, x, y, width, height);
        }

        private void drawCopperButtonFrame(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height,
                boolean hovered
        ) {
            drawTexturePanel(
                    g,
                    hovered ? BUTTON_HOVER_TEXTURE : BUTTON_TEXTURE,
                    x,
                    y,
                    width,
                    height
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
