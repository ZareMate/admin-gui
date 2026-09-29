package com.zaremate.admin_gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AdminGuiNetwork {
    private static final Map<UUID, UUID> OPEN_GUI_SELECTIONS = new HashMap<>();

    private AdminGuiNetwork() {}

    public static final CustomPacketPayload.Type<OpenPayload> OPEN_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "open"));

    public static final CustomPacketPayload.Type<DetailPayload> DETAIL_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "detail"));

    public static final CustomPacketPayload.Type<ListUpdatePayload> LIST_UPDATE_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "list_update"));

    public static final CustomPacketPayload.Type<ClosePayload> CLOSE_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "close"));

    public static final CustomPacketPayload.Type<SelectPayload> SELECT_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "select"));

    public static final CustomPacketPayload.Type<NoteActionPayload> NOTE_ACTION_TYPE =
            new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AdminGui.MOD_ID, "note_action"));

    private static final StreamCodec<ByteBuf, String> STRING_CODEC = ByteBufCodecs.STRING_UTF8;

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> OPEN_CODEC =
            StreamCodec.composite(STRING_CODEC, OpenPayload::data, OpenPayload::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, DetailPayload> DETAIL_CODEC =
            StreamCodec.composite(STRING_CODEC, DetailPayload::data, DetailPayload::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ListUpdatePayload> LIST_UPDATE_CODEC =
            StreamCodec.composite(STRING_CODEC, ListUpdatePayload::data, ListUpdatePayload::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ClosePayload> CLOSE_CODEC =
            StreamCodec.unit(new ClosePayload());

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectPayload> SELECT_CODEC =
            StreamCodec.composite(STRING_CODEC, SelectPayload::uuid, SelectPayload::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteActionPayload> NOTE_ACTION_CODEC =
            StreamCodec.composite(
                    STRING_CODEC, NoteActionPayload::action,
                    STRING_CODEC, NoteActionPayload::playerUuid,
                    STRING_CODEC, NoteActionPayload::noteId,
                    STRING_CODEC, NoteActionPayload::text,
                    NoteActionPayload::new
            );

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(OPEN_TYPE, OPEN_CODEC, (payload, context) ->
                AdminGuiClientBridge.open(payload.data()));
        registrar.playToClient(DETAIL_TYPE, DETAIL_CODEC, (payload, context) ->
                AdminGuiClientBridge.detail(payload.data()));
        registrar.playToClient(LIST_UPDATE_TYPE, LIST_UPDATE_CODEC, (payload, context) ->
                AdminGuiClientBridge.listUpdate(payload.data()));

        registrar.playToServer(CLOSE_TYPE, CLOSE_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        OPEN_GUI_SELECTIONS.remove(player.getUUID());
                    }
                }));
        registrar.playToServer(SELECT_TYPE, SELECT_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.hasPermissions(3)
                            && clientHasAdminGui(player)) {
                        UUID selected = parseUuid(payload.uuid());
                        OPEN_GUI_SELECTIONS.put(player.getUUID(), selected);
                        sendDetail(player, selected);
                    }
                }));
        registrar.playToServer(NOTE_ACTION_TYPE, NOTE_ACTION_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.hasPermissions(3)
                            && clientHasAdminGui(player)) {
                        handleNoteAction(player, payload);
                    }
                }));
    }

    public static boolean clientHasAdminGui(ServerPlayer player) {
        return player != null && player.connection.hasChannel(OPEN_TYPE.id());
    }

    public static void open(ServerPlayer player) {
        if (!clientHasAdminGui(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Admin GUI: You need the admin_gui client mod installed to open this interface."));
            return;
        }

        OPEN_GUI_SELECTIONS.put(player.getUUID(), null);

        String list = AdminGuiData.buildPlayerList(player.server);
        PacketDistributor.sendToPlayer(player, new OpenPayload(list));
    }

    private static void sendDetail(ServerPlayer player, UUID uuid) {
        if (uuid == null) return;
        PacketDistributor.sendToPlayer(
                player,
                new DetailPayload(AdminGuiData.buildPlayerDetail(player.server, uuid, player.getUUID()))
        );
    }

    public static void close(ServerPlayer player) {
        if (player != null) {
            OPEN_GUI_SELECTIONS.remove(player.getUUID());
        }
    }

    public static void refreshOpenGuis(net.minecraft.server.MinecraftServer server) {
        Iterator<Map.Entry<UUID, UUID>> iterator = OPEN_GUI_SELECTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());

            if (viewer == null || !clientHasAdminGui(viewer)) {
                iterator.remove();
                continue;
            }

            UUID selected = entry.getValue();

            try {
                PacketDistributor.sendToPlayer(
                        viewer,
                        new ListUpdatePayload(AdminGuiData.buildPlayerList(server))
                );

                if (selected != null) {
                    sendDetail(viewer, selected);
                }
            } catch (Throwable ex) {
                AdminGui.LOGGER.warn(
                        "Failed to refresh Admin GUI for {}.",
                        viewer.getGameProfile().getName(),
                        ex
                );
            }
        }
    }

    private static void handleNoteAction(ServerPlayer player, NoteActionPayload payload) {
        UUID target = parseUuid(payload.playerUuid());
        if (target == null) return;

        String action = payload.action();
        try {
            if (action.equals("add")) {
                if (payload.text().isBlank()) return;
                AdminGuiData.addNote(target, player.getUUID(), player.getGameProfile().getName(), payload.text());
            } else if (action.equals("edit")) {
                UUID note = parseUuid(payload.noteId());
                if (note == null || payload.text().isBlank()
                        || !AdminGuiData.canEditNote(target, note, player.getUUID())) {
                    return;
                }
                AdminGuiData.editNote(target, note, payload.text());
            } else if (action.equals("remove")) {
                UUID note = parseUuid(payload.noteId());
                if (note == null || !AdminGuiData.canEditNote(target, note, player.getUUID())) {
                    return;
                }
                AdminGuiData.removeNote(target, note);
            }
            sendDetail(player, target);
        } catch (Throwable ex) {
            AdminGui.LOGGER.warn("Admin GUI note action failed.", ex);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record OpenPayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return OPEN_TYPE; }
    }

    public record DetailPayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return DETAIL_TYPE; }
    }

    public record ListUpdatePayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return LIST_UPDATE_TYPE; }
    }

    public record ClosePayload() implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return CLOSE_TYPE; }
    }

    public record SelectPayload(String uuid) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return SELECT_TYPE; }
    }

    public record NoteActionPayload(String action, String playerUuid, String noteId, String text)
            implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return NOTE_ACTION_TYPE; }
    }
}
