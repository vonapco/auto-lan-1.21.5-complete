package org.wsm.autolan;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.GameMode;

public class LanSettings {
    public static final boolean DEFAULT_ONLINE_MODE = true;
    public static final TunnelType DEFAULT_TUNNEL = TunnelType.NGROK;
    public static final int DEFAULT_PORT = 25565;
    public static final int DEFAULT_MAX_PLAYERS = 5;
    public static final String DEFAULT_MOTD = "${username} - ${world}";

    public GameMode gameMode;
    public boolean onlineMode;
    public TunnelType tunnel;
    public int port;
    public int maxPlayers;
    public String motd;

    public LanSettings(GameMode gameMode, boolean onlineMode, TunnelType tunnel, int port,
                       int maxPlayers, String motd) {
        this.gameMode = gameMode;
        this.onlineMode = onlineMode;
        this.tunnel = tunnel;
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.motd = motd;
    }

    public static LanSettings defaults() {
        return new LanSettings(GameMode.SURVIVAL, DEFAULT_ONLINE_MODE, DEFAULT_TUNNEL, DEFAULT_PORT,
                DEFAULT_MAX_PLAYERS, DEFAULT_MOTD);
    }

    public static LanSettings systemDefaults(MinecraftServer server) {
        LanSettings defaults = defaults();
        return new LanSettings(server.getDefaultGameMode(), defaults.onlineMode, defaults.tunnel, defaults.port,
                defaults.maxPlayers, defaults.motd);
    }

    public static LanSettings systemDefaults(GameMode gameMode) {
        LanSettings defaults = defaults();
        return new LanSettings(gameMode, defaults.onlineMode, defaults.tunnel, defaults.port,
                defaults.maxPlayers, defaults.motd);
    }
}
