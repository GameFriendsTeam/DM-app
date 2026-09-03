package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.packet.Packet;

import java.util.Map;
import java.util.Scanner;

/** Mirrors api.commands.client.ToCMD.ToCMD — pick the message recipient (with online check). */
public class ToCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        System.out.print("Enter recipient's nickname(empty for server): ");
        String to = new Scanner(System.in).nextLine().trim();
        if (to.isEmpty()) cs.getContext().currentGetter = "server";

        cs.send(new Packet(Map.of("type", "online_check", "is_online", to)), cs.connectionIsSecure());
        Object[] data = cs.waitPacket("online_check", 5.0);
        if (data[0] == null) { System.out.println("data not gotten"); return; }
        Packet status = (Packet) data[0];
        System.out.println(status.getAll());

        if (!Boolean.TRUE.equals(status.get("online"))) {
            System.out.println("\"" + to + "\" is not online");
            return;
        }
        cs.getContext().currentGetter = to;
    }
}
