package org.wsm.autolan.agent.model;

import java.util.Map;

public class Command {
    private String command;
    private Map<String, Object> args;

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }
} 