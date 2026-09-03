package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.util.Logger;

/** Mirrors api.commands.client.QCMD.QCMD. */
public class QCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        Logger.info("Closing connection...");
        cs.stop();
    }
}
