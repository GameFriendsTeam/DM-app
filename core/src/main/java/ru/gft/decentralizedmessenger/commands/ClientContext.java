package ru.gft.decentralizedmessenger.commands;

/** Shared mutable state for client commands (replaces Python's __main__ globals). */
public class ClientContext {
    public String currentGetter = "server";
    public CommandManager commandManager;
    public int maxSizeSyncPacket = 128;
}
