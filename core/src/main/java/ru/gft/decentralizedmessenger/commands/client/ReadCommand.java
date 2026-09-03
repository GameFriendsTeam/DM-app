package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.crypto.FileEncryption;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.util.Base64Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Mirrors api.commands.client.ReadCMD.ReadCMD. */
public class ReadCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        System.out.println("Q for exit");
        boolean active = true;
        try {
            while (active) {
                Object[] data = cs.waitPacket("message", 5.0);
                if (data[0] == null) continue;
                Packet packet = (Packet) data[0];
                String sender = packet.getString("from", "[unknown]");
                String content = packet.getString("content", "[ERROR]");

                if ("Encrypted".equals(content)) {
                    var enc = cs.getEncript(cs.getContext().currentGetter);
                    if (enc == null) continue;
                    Object encObj = packet.get("encrypted");
                    if (!(encObj instanceof List)) continue;
                    List<?> encrypted = (List<?>) encObj;
                    String nonce = (String) encrypted.get(0);
                    String ciphertext = (String) encrypted.get(1);
                    byte[] dec = enc.decryptMessage(Base64Util.base64ToBytes(nonce), Base64Util.base64ToBytes(ciphertext));
                    System.out.println(sender + ": " + new String(dec));
                    continue;
                }

                if ("key2file".equals(content)) {
                    var enc = cs.getEncript(cs.getContext().currentGetter);
                    if (enc == null) continue;
                    Object[] fileData = cs.waitPacket("filedata", 5.0);
                    if (fileData[0] == null || !"filedata".equals(((Packet) fileData[0]).getString("type", ""))) {
                        System.out.println("Incorrect data");
                        continue;
                    }
                    Packet pkt1 = (Packet) fileData[0];
                    Object encKeyObj = packet.get("encrypted");
                    if (!(encKeyObj instanceof List)) continue;
                    List<?> encKey = (List<?>) encKeyObj;
                    byte[] key = enc.decryptMessage(
                            Base64Util.base64ToBytes((String) encKey.get(0)),
                            Base64Util.base64ToBytes((String) encKey.get(1)));
                    FileEncryption fe = new FileEncryption(key);
                    byte[] decrypted = fe.decrypt(Base64Util.base64ToBytes(pkt1.getString("encrypted", "")));

                    Object nameEncObj = pkt1.get("filename");
                    if (!(nameEncObj instanceof List)) continue;
                    List<?> nameEnc = (List<?>) nameEncObj;
                    byte[] filenameBytes = enc.decryptMessage(
                            Base64Util.base64ToBytes((String) nameEnc.get(0)),
                            Base64Util.base64ToBytes((String) nameEnc.get(1)));
                    String filename = new String(filenameBytes);
                    Files.write(Path.of(filename), decrypted);
                    continue;
                }

                System.out.println(sender + ": " + content);
            }
        } catch (Exception e) {
            active = false;
        }
    }
}
