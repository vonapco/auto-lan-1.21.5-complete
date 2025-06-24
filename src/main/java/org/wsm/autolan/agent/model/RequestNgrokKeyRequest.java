package org.wsm.autolan.agent.model;

public class RequestNgrokKeyRequest {
    private String clientId;
    private boolean hasCustomKey;

    public RequestNgrokKeyRequest(String clientId, boolean hasCustomKey) {
        this.clientId = clientId;
        this.hasCustomKey = hasCustomKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isHasCustomKey() {
        return hasCustomKey;
    }

    public void setHasCustomKey(boolean hasCustomKey) {
        this.hasCustomKey = hasCustomKey;
    }
}
