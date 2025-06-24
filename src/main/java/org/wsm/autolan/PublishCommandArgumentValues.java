package org.wsm.autolan;

import java.util.function.Function;

import org.wsm.autolan.util.ArgumentValueFunction;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.GameMode;

public class PublishCommandArgumentValues {
    public ArgumentValueFunction<Integer> getPort;
    public ArgumentValueFunction<Boolean> getOnlineMode;
    public ArgumentValueFunction<Integer> getMaxPlayers;
    public ArgumentValueFunction<GameMode> getGameMode;
    public ArgumentValueFunction<TunnelType> getTunnel;
    public ArgumentValueFunction<String> getMotd;

    private static <T> T getValue(
            CommandContext<ServerCommandSource> context, Function<LanSettings, T> getSetting)
            throws CommandSyntaxException {
        MinecraftServer server = context.getSource().getServer();
        return getSetting.apply(LanSettings.systemDefaults(server));
    }

    public PublishCommandArgumentValues() {
        this.getPort = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.port);
        this.getOnlineMode = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.onlineMode);
        this.getMaxPlayers = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.maxPlayers);
        this.getGameMode = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.gameMode);
        this.getTunnel = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.tunnel);
        this.getMotd = context -> getValue(context,
                defaultLanSettings -> defaultLanSettings.motd);
    }

    public PublishCommandArgumentValues(PublishCommandArgumentValues other) {
        this.getPort = other.getPort;
        this.getOnlineMode = other.getOnlineMode;
        this.getMaxPlayers = other.getMaxPlayers;
        this.getGameMode = other.getGameMode;
        this.getTunnel = other.getTunnel;
        this.getMotd = other.getMotd;
    }
}