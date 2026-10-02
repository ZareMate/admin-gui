package com.zaremate.admin_gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class AdminGuiPunishScreen extends Screen {
    private static final int WIDTH = 520;
    private static final int HEIGHT = 430;
    private static final int VISIBLE = 9;

    private static final int BG = 0xFF0E1117;
    private static final int PANEL = 0xFF161B22;
    private static final int PANEL_ALT = 0xFF1C2128;
    private static final int BORDER = 0xFF30363D;
    private static final int BORDER_HOVER = 0xFF484F58;
    private static final int TEXT = 0xFFF0F3F6;
    private static final int MUTED = 0xFF8B949E;
    private static final int ACCENT = 0xFF58A6FF;

    private final Screen parent;
    private final String targetUuid;
    private final String targetName;
    private final List<OffenseRef> offenses;
    private final List<PlainTextButton> offenseButtons = new ArrayList<>();

    private EditBox search;
    private int scroll;

    AdminGuiPunishScreen(
            Screen parent,
            @Nonnull String targetUuid,
            @Nonnull String targetName,
            JsonArray offenseData
    ) {
        super(Component.literal("Punish " + targetName));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.targetUuid = Objects.requireNonNull(targetUuid, "targetUuid");
        this.targetName = Objects.requireNonNull(targetName, "targetName");
        this.offenses = parse(offenseData);
    }

    @Override
    protected void init() {
        Font guiFont = Objects.requireNonNull(font, "Screen font is not initialized");
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        search = new EditBox(guiFont, left + 18, top + 45, WIDTH - 36, 22, Component.literal("Search offenses"));
        search.setHint(Component.literal("Search offenses..."));
        search.setMaxLength(64);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        addRenderableWidget(search);

        rebuildOffenseButtons();
    }

    private void rebuildOffenseButtons() {
        for (PlainTextButton button : offenseButtons) removeWidget(Objects.requireNonNull(button));
        offenseButtons.clear();

        List<OffenseRef> filtered = filteredOffenses();
        int start = Math.min(scroll, Math.max(0, filtered.size() - VISIBLE));
        int end = Math.min(filtered.size(), start + VISIBLE);

        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        String previousGroup = null;
        int visualRow = 0;

        for (int i = start; i < end; i++) {
            OffenseRef offense = filtered.get(i);

            if (!Objects.equals(previousGroup, offense.group())) {
                previousGroup = offense.group();
            }

            int y = top + 78 + visualRow * 34;
            visualRow++;

            String label = offense.name() + "  [" + offense.id() + "]";
            PlainTextButton button = new PlainTextButton(
                    left + 18,
                    y,
                    WIDTH - 36,
                    28,
                    Component.literal(label),
                    ignored -> select(offense),
                    Objects.requireNonNull(font)
            );
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.literal(
                            offense.group()
                                    + "\nSteps: " + String.join(" → ", offense.steps())
                                    + (offense.aliases().isEmpty()
                                    ? ""
                                    : "\nAliases: " + String.join(", ", offense.aliases()))
                    )
            ));
            offenseButtons.add(button);
            addRenderableWidget(Objects.requireNonNull(button));
        }
    }

    private void select(OffenseRef offense) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && Minecraft.getInstance().player != null) {
            // Execute the normal /punish command silently. The command's
            // no-rest form only previews the punishment plan, so -s is used
            // to enter the actual execution path without opening chat.
            connection.sendCommand("punish " + targetName + " " + offense.id() + " -s");
        }
        Minecraft.getInstance().setScreen(parent);
    }

    private List<OffenseRef> filteredOffenses() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return offenses.stream()
                .filter(o -> query.isBlank()
                        || o.id().toLowerCase(Locale.ROOT).contains(query)
                        || o.name().toLowerCase(Locale.ROOT).contains(query)
                        || o.group().toLowerCase(Locale.ROOT).contains(query)
                        || o.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query)))
                .toList();
    }

    private static List<OffenseRef> parse(JsonArray array) {
        List<OffenseRef> result = new ArrayList<>();
        if (array == null) return result;

        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            String id = text(o, "id", "");
            if (id.isBlank()) continue;

            result.add(new OffenseRef(
                    id,
                    text(o, "group", "Other"),
                    text(o, "name", id),
                    strings(o.getAsJsonArray("steps")),
                    strings(o.getAsJsonArray("aliases"))
            ));
        }

        result.sort(Comparator
                .comparing(OffenseRef::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(OffenseRef::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private static List<String> strings(JsonArray array) {
        if (array == null || array.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) result.add(element.getAsString());
        return List.copyOf(result);
    }

    private static String text(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) ? object.get(key).getAsString() : fallback;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search != null && search.isFocused()) {
            boolean result = super.keyPressed(keyCode, scanCode, modifiers);
            scroll = Math.min(scroll, Math.max(0, filteredOffenses().size() - VISIBLE));
            rebuildOffenseButtons();
            return result;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(
                0,
                Math.min(
                        Math.max(0, filteredOffenses().size() - VISIBLE),
                        scroll - (int) Math.signum(scrollY)
                )
        );
        rebuildOffenseButtons();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font guiFont = Objects.requireNonNull(font, "Screen font is not initialized");
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        g.fill(0, 0, width, height, 0xCC000000);

        panel(g, left, top, WIDTH, HEIGHT, BG, BORDER, 1);
        panel(g, left + 1, top + 1, WIDTH - 2, 36, PANEL_ALT, BORDER, 1);
        g.drawString(guiFont, "PUNISH PLAYER", left + 18, top + 13, TEXT, true);
        g.drawString(guiFont, targetName, left + WIDTH - 180, top + 13, MUTED, false);

        panel(g, left + 18, top + 43, WIDTH - 36, 26, PANEL, BORDER, 1);

        List<OffenseRef> filtered = filteredOffenses();
        int start = Math.min(scroll, Math.max(0, filtered.size() - VISIBLE));
        String group = null;
        int visibleIndex = 0;

        for (int i = start; i < Math.min(filtered.size(), start + VISIBLE); i++) {
            OffenseRef offense = filtered.get(i);
            if (!Objects.equals(group, offense.group())) {
                group = offense.group();
                int y = top + 73 + visibleIndex * 34;
                g.drawString(guiFont, offense.group(), left + 22, y - 10, ACCENT, false);
            }
            visibleIndex++;
        }

        if (filtered.isEmpty()) {
            g.drawString(guiFont, "No offenses found.", left + 22, top + 90, MUTED, false);
        }

        if (offenses.isEmpty()) {
            g.drawString(guiFont, "Punish is not installed or no offenses are configured.",
                    left + 22, top + 110, MUTED, false);
        }

        for (PlainTextButton button : offenseButtons) {
            boolean hovered = button.isHoveredOrFocused();
            panel(g, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                    hovered ? PANEL_ALT : PANEL, hovered ? BORDER_HOVER : BORDER, 1);
            button.render(g, mouseX, mouseY, partialTick);
        }

        int max = Math.max(0, filtered.size() - VISIBLE);
        if (max > 0) {
            int barX = left + WIDTH - 10;
            int barY = top + 78;
            int barHeight = HEIGHT - 110;
            g.fill(barX, barY, barX + 4, barY + barHeight, 0xFF21262D);
            int thumbHeight = Math.max(18, barHeight * VISIBLE / Math.max(VISIBLE, filtered.size()));
            int travel = Math.max(0, barHeight - thumbHeight);
            int thumbY = barY + travel * scroll / max;
            g.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, MUTED);
        }

        if (search != null) search.render(g, mouseX, mouseY, partialTick);
    }

    private static void panel(GuiGraphics g, int x, int y, int width, int height, int fill, int border, int thickness) {
        g.fill(x, y, x + width, y + height, fill);
        g.fill(x, y, x + width, y + thickness, border);
        g.fill(x, y + height - thickness, x + width, y + height, border);
        g.fill(x, y, x + thickness, y + height, border);
        g.fill(x + width - thickness, y, x + width, y + height, border);
    }

    private record OffenseRef(
            String id,
            String group,
            String name,
            List<String> steps,
            List<String> aliases
    ) {}
}
