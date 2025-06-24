package org.wsm.autolan.agent.model;

import com.google.gson.annotations.SerializedName;

public class ProcessStats {
    @SerializedName("cpu_percent")
    private double cpuPercent;

    @SerializedName("memory_percent")
    private double memoryPercent;

    public ProcessStats(double cpuPercent, double memoryPercent) {
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
    }

    public double getCpuPercent() {
        return cpuPercent;
    }

    public void setCpuPercent(double cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    public double getMemoryPercent() {
        return memoryPercent;
    }

    public void setMemoryPercent(double memoryPercent) {
        this.memoryPercent = memoryPercent;
    }
} 