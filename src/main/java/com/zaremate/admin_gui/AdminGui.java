package com.zaremate.admin_gui;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(AdminGui.MOD_ID)
public final class AdminGui {
    public static final String MOD_ID = "admin_gui";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public AdminGui(IEventBus modEventBus) {
        modEventBus.addListener(AdminGuiNetwork::register);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
        LOGGER.info("Admin GUI loaded.");
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AdminGuiNetwork.refreshOpenGuis(player.server);
        }
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AdminGuiNetwork.refreshOpenGuis(player.server);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            AdminGuiNetwork.refreshOpenGuis(event.getServer());
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        var command = Commands.literal("adm-gui")
                .requires(source -> source.isPlayer()
                        && AdminGuiNetwork.hasAdminPermission(source))
                .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("open")
                        .executes(ctx -> open(ctx.getSource().getPlayerOrException())));

        event.getDispatcher().register(command);
        event.getDispatcher().register(
                Commands.literal("admin-gui")
                        .requires(source -> source.isPlayer()
                                && AdminGuiNetwork.hasAdminPermission(source))
                        .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
        );
    }

    private int open(ServerPlayer player) {
        AdminGuiNetwork.open(player);
        return 1;
    }
}
