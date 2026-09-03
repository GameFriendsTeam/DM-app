package ru.gft.decentralizedmessenger.commands;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mirrors api.commands.CommandManager.CommandManager. */
public class CommandManager {
    private final Map<String, Command> cmds = new LinkedHashMap<>();

    public void registerCMD(String name, Command cmd) { cmds.put(name, cmd); }

    public void registerCMDs(Map<String, Command> cmds) {
        for (Map.Entry<String, Command> e : cmds.entrySet()) registerCMD(e.getKey(), e.getValue());
    }

    public Command getCMD(String name) { return cmds.get(name); }

    public Map<String, Command> getCMDs() { return cmds; }
}
