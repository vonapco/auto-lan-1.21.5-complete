package org.wsm.autolan.agent.model;

import com.google.gson.annotations.SerializedName;

public class Status {
    @SerializedName("server_running")
    private boolean serverRunning;

    @SerializedName("minecraft_client_active")
    private boolean minecraftClientActive;

    @SerializedName("system_stats")
    private ProcessStats systemStats;

    @SerializedName("server_process_stats")
    private ProcessStats serverProcessStats;

    public boolean isServerRunning() {
        return serverRunning;
    }

    public void setServerRunning(boolean serverRunning) {
        this.serverRunning = serverRunning;
    }

    public boolean isMinecraftClientActive() {
        return minecraftClientActive;
    }

    public void setMinecraftClientActive(boolean minecraftClientActive) {
        this.minecraftClientActive = minecraftClientActive;
    }

    public ProcessStats getSystemStats() {
        return systemStats;
    }

    public void setSystemStats(ProcessStats systemStats) {
        this.systemStats = systemStats;
    }

    public ProcessStats getServerProcessStats() {
        return serverProcessStats;
    }

    public void setServerProcessStats(ProcessStats serverProcessStats) {
        this.serverProcessStats = serverProcessStats;
    }
} 