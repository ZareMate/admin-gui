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
        private static final ResourceLocation CARDBOARD_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("admin_gui", "textures/gui/cardboard.png");
        // Use Create's original clipboard GUI texture instead of maintaining a
        // copied/converted image in this mod. Create's 1.21.1 clipboard GUI
        // texture is 256x256 and is rendered by ClipboardScreen as-is.
        private static final ResourceLocation CREATE_CLIPBOARD_TEXTURE =
                ResourceLocation.fromNamespaceAndPath("create", "textures/gui/clipboard.png");

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
            int x = left + 305;
            int y = top + 331 + 28;

            for (int i = 0; i < shown; i++) {
                JsonObject note = notes.get(start + i).getAsJsonObject();
                int row = y + i * 36;
                if (canEditNote(note)) {
                    PlainTextButton edit = new PlainTextButton(
                            x + 440, row - 1, 45, 18,
                            Component.literal("EDIT").withStyle(s -> s.withColor(0xFF5A4028)),
                            b -> editNote(note), font);
                    PlainTextButton remove = new PlainTextButton(
                            x + 490, row - 1, 20, 18,
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

            // Cardboard is the actual dashboard surface. The outer clipboard
            // frame is taken directly from Create's original texture so its
            // wood, paper edge and metal clip stay pixel-identical to Create.
            drawCardboardTextureScaled2x(g, left, top, WIDTH, HEIGHT);
            drawCreateClipboardFrame(g, left, top, WIDTH, HEIGHT);

            // Player divider.
            g.fill(left + 280, top + 27, left + 282, bottom, 0xFF704B2E);

            // Search field.
            drawBrassFrame(g, left + 11, top + 31, 257, 22);
            g.fill(left + 13, top + 33, left + 266, top + 51, 0xFF0B0D0C);

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
                        drawPostIt(
                                g,
                                widget.getX(),
                                widget.getY(),
                                widget.getWidth(),
                                widget.getHeight(),
                                false,
                                hovered,
                                widget.getY()
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

        private void drawCreateClipboardFrame(
                GuiGraphics g,
                int x,
                int y,
                int width,
                int height
        ) {
            // Create's AllGuiTextures.CLIPBOARD is 256x256. We draw the
            // original texture as the outer frame, then paint the large center
            // back over with cardboard so only the parts we actually need
            // (border + top clip) remain visible.
            g.blit(
                    CREATE_CLIPBOARD_TEXTURE,
                    x,
                    y,
                    0,
                    0,
                    width,
                    height,
                    256,
                    256
            );

            // The Create clipboard's inner paper starts roughly 20-25 px from
            // the sides and ~40 px below the clip. Cover that paper area with
            // our dashboard cardboard rather than tinting or recreating it.
            int innerLeft = x + 24;
            int innerTop = y + 43;
            int innerRight = x + width - 24;
            int innerBottom = y + height - 12;

            if (innerRight > innerLeft && innerBottom > innerTop) {
                drawCardboardTextureScaled2x(
                        g,
                        innerLeft,
                        innerTop,
                        innerRight - innerLeft,
                        innerBottom - innerTop
                );
            }
        }

        private ResourceLocation cardboardTexture() {
            return CARDBOARD_TEXTURE;
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
            int textureAreaWidth = (width + 1) / 2;
            int textureAreaHeight = (height + 1) / 2;

            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);

            for (int yy = 0; yy < textureAreaHeight; yy += 16) {
                for (int xx = 0; xx < textureAreaWidth; xx += 16) {
                    int drawWidth = Math.min(16, textureAreaWidth - xx);
                    int drawHeight = Math.min(16, textureAreaHeight - yy);

                    g.blit(
                            CARDBOARD_TEXTURE,
                            xx,
                            yy,
                            0,
                            0,
                            drawWidth,
                            drawHeight,
                            16,
                            16
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
            int note = switch (Math.floorMod(variant, 3)) {
                case 0 -> 0xFFF5DE79;
                case 1 -> 0xFFF1D978;
                default -> 0xFFEBD17A;
            };
            if (hovered) note = 0xFFFFE99E;
            if (selected) note = 0xFFFFEDAA;

            g.fill(x + 3, y + 3, x + width + 2, y + height + 3, 0x36000000);
            g.fill(x, y, x + width, y + height, note);
            g.fill(x, y, x + width - 3, y + 1, 0x42FFFFFF);
            g.fill(x, y + height - 1, x + width - 3, y + height, 0x22000000);
            g.fill(x + width - 1, y + 3, x + width, y + height - 2, 0x18000000);

            int fold = Math.min(8, Math.max(4, height / 3));
            g.fill(x + width - fold, y + height - fold, x + width, y + height, 0x2A000000);
            g.fill(x + width - fold, y + height - fold, x + width - 1, y + height - fold + 1, 0x48000000);
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
            int note = (Math.floorMod(variant / 36, 2) == 0) ? 0xFFFFE58A : 0xFFF6D778;
            if (hovered) note = 0xFFFFEDA5;

            g.fill(x + 4, y + 4, x + width + 3, y + height + 4, 0x42000000);
            g.fill(x, y, x + width, y + height, note);
            g.fill(x, y, x + width, y + 2, 0x48FFFFFF);
            g.fill(x + 2, y + height - 2, x + width - 6, y + height, 0x22000000);

            int tapeWidth = Math.min(96, Math.max(56, width / 7));
            int tapeX = x + (width - tapeWidth) / 2;
            g.fill(tapeX, y - 2, tapeX + tapeWidth, y + 4, 0x32FFF9D8);
            g.fill(tapeX + 2, y - 1, tapeX + tapeWidth - 2, y + 3, 0x24FFF4B5);

            int fold = Math.min(14, Math.max(8, height / 5));
            g.fill(x + width - fold, y + height - fold, x + width, y + height, 0x2A000000);
            g.fill(x + width - fold, y + height - fold, x + width - 1, y + height - fold + 1, 0x4A000000);
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
            int trackWidth = 4;
            g.fill(trackX, y, trackX + trackWidth, y + height, 0x553D2A1B);

            int maxOffset = total - visible;
            int thumbHeight = Math.max(10, height * visible / total);
            int travel = height - thumbHeight;
            int thumbY = y + (travel * clampScroll(offset, maxOffset) / maxOffset);

            g.fill(trackX - 1, thumbY, trackX + trackWidth + 1, thumbY + thumbHeight, 0xB36B4A2F);
            g.fill(trackX, thumbY, trackX + trackWidth, thumbY + 1, 0xD6B77A52);
            g.fill(trackX, thumbY + thumbHeight - 1, trackX + trackWidth, thumbY + thumbHeight, 0x6A3B2818);
        }

        private void drawCreateCard(GuiGraphics g, int x, int y, int width, int height) {
            g.fill(x, y, x + width, y + height, 0xD94B3625);
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
