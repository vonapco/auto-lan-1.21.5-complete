package org.wsm.autolan.agent.model;

import com.google.gson.annotations.SerializedName;

public class RegistrationResponse {
    @SerializedName("client_id")
    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
} 