package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandManager;
import ru.gft.decentralizedmessenger.commands.CommandSender;

/** Mirrors api.commands.client._HelpCMD.HelpCMD. */
public class HelpCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        CommandManager cmdm = cs.getContext().commandManager;
        if (cmdm == null) { System.out.println("Non-client environment!"); return; }
        System.out.println("Commands available:");
        for (String name : cmdm.getCMDs().keySet()) System.out.println(" - /" + name);
    }
}
