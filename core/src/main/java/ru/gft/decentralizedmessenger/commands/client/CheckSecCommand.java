package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.util.Base64Util;
import ru.gft.decentralizedmessenger.util.Logger;

import java.util.Map;
import java.util.Scanner;

/** Mirrors api.commands.client.CheckSecCMD.CheckSecCMD. */
public class CheckSecCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        boolean firstCheck = cs.connectionIsSecure();
        cs.send(new Packet(Map.of("type", "cc", "ping", 5)), firstCheck);
        Object[] resp = cs.waitPacket("cc", 5.0);
        boolean secondCheck = resp[0] != null && (Boolean) resp[1];
        String currentGetter = cs.getContext().currentGetter;
        Boolean thirdCheck = null;
        if (!"server".equals(currentGetter)) thirdCheck = cs.getEncript(currentGetter) != null;

        String t0 = "Client<->Server connection is secure: " + (firstCheck ? "yes" : "no");
        String t1 = "Encryption can be used: " + (secondCheck ? "yes" : "no");
        String t2 = "Client<->Client connection is secure: "
                + (thirdCheck == null ? "unknown" : (thirdCheck ? "yes" : "no"));

        if (firstCheck) Logger.info(t0); else Logger.warning(t0);
        if (secondCheck) Logger.info(t1); else Logger.warning(t1);

        if (!"server".equals(currentGetter)) {
            var enc = cs.getEncript(currentGetter);
            if (enc != null) {
                byte[] xPub = enc.serializeX25519Public();
                byte[] edPub = enc.serializeEd25519Public();
                cs.send(new Packet(Map.of(
                        "type", "key_check",
                        "x25519_pub", Base64Util.bytesToBase64(xPub),
                        "ed25519_pub", Base64Util.bytesToBase64(edPub))), true);
                System.out.print("Press Enter to continue...");
                new Scanner(System.in).nextLine();
                Packet peerKeyPkt = null;
                while (peerKeyPkt == null) {
                    Object[] data = cs.waitPacket("key_check", 15);
                    if (data[0] == null) {
                        Logger.error("No key check packet received. Client<->Client connection is secure: unknown");
                        return;
                    }
                    peerKeyPkt = (Packet) data[0];
                }
                byte[] peerX25519 = Base64Util.base64ToBytes(peerKeyPkt.getString("x25519_pub", ""));
                byte[] peerEd25519 = Base64Util.base64ToBytes(peerKeyPkt.getString("ed25519_pub", ""));
                var trusted = enc.getTrustedPeerKey(currentGetter);
                boolean peerIsNotSus = false;
                if (trusted != null) {
                    boolean edMatch = java.util.Arrays.equals(peerEd25519, trusted.ed25519Public);
                    boolean xMatch = java.util.Arrays.equals(peerX25519, trusted.x25519Public);
                    if (!edMatch || !xMatch) peerIsNotSus = true;
                }
                t2 = "Client<->Client connection is secure: " + (peerIsNotSus ? "sus" : "yes");
                if (!peerIsNotSus) Logger.info(t2); else Logger.warning(t2);
            } else {
                Logger.warning("Client<->Client connection is secure: unknown (no peer selected)");
            }
        }
    }
}
