package org.wsm.autolan.agent.model;

public class ReleaseNgrokKeyRequest {
    private String clientId;
    private String key;

    public ReleaseNgrokKeyRequest(String clientId, String key) {
        this.clientId = clientId;
        this.key = key;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
