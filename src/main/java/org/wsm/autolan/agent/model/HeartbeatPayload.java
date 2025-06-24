package org.wsm.autolan.agent.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class HeartbeatPayload {
    @SerializedName("client_id")
    private String clientId;

    private Status status;

    @SerializedName("ngrok_urls")
    private Map<String, String> ngrokUrls;

    public HeartbeatPayload() {
    }

    public HeartbeatPayload(String clientId, Status status, Map<String, String> ngrokUrls) {
        this.clientId = clientId;
        this.status = status;
        this.ngrokUrls = ngrokUrls;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<String, String> getNgrokUrls() {
        return ngrokUrls;
    }

    public void setNgrokUrls(Map<String, String> ngrokUrls) {
        this.ngrokUrls = ngrokUrls;
    }
} 