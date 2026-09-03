package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;

/** Mirrors api.commands.client.CCCMD.CCCMD. */
public class CcCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        boolean[] res = cs.checkConnection(5);
        if (res[0]) System.out.println("Ok!");
        else System.out.println("Error!");
    }
}
