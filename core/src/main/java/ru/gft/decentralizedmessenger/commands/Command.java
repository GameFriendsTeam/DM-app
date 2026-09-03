package ru.gft.decentralizedmessenger.commands;

/** Mirrors api.commands.Command.Command. */
public abstract class Command {
    public abstract void execute(CommandSender cs);
}
