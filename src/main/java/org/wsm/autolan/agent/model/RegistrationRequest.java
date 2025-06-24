package org.wsm.autolan.agent.model;

public class RegistrationRequest {
    private String name;

    public RegistrationRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
} 