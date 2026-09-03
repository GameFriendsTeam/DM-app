package ru.gft.decentralizedmessenger.commands.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.packet.Packet;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/** Mirrors api.commands.client.RawCMD.RawCMD. */
public class RawCommand extends Command {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Object>>() {}.getType();

    @Override
    public void execute(CommandSender cs) {
        System.out.println("Send a self-written packet in JSON (Empty line for skip)");
        System.out.print(":");
        String raw = new Scanner(System.in).nextLine();
        Object data;
        try {
            data = GSON.fromJson(raw, Object.class);
        } catch (Exception e) {
            System.out.println("Input JSON format is not correct!");
            return;
        }
        if (data == null) return;
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) data;
            cs.send(new Packet(m), true);
        } else {
            cs.send(new Packet(Map.of("type", "raw", "data", data)), true);
        }
        Object[] resp = cs.waitPacket("status", 5.0);
        if (resp[0] != null) {
            System.out.println(GSON.toJson(((Packet) resp[0]).getAll()));
            System.out.println("Is encrypted: " + resp[1]);
        }
    }
}
