package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.crypto.FileEncryption;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.util.Base64Util;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;

/** Mirrors api.commands.client.SendFileCMD.SendFileCMD. */
public class SendFileCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        String filePath = "";
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a file");
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                filePath = chooser.getSelectedFile().getAbsolutePath();
            }
        } catch (Throwable e) {
            System.out.println(e.getMessage());
            System.out.println("Enter file path manually.");
            System.out.print("File path: ");
            filePath = new Scanner(System.in).nextLine().trim();
        }
        if (filePath.isEmpty()) return;

        String getter = cs.getContext().currentGetter;
        var enc = cs.getEncript(getter);
        if (enc == null) { System.out.println("Encryption is not activated"); return; }

        try {
            FileEncryption fe = new FileEncryption();
            byte[] key = fe.getKey();
            byte[] fileData = Files.readAllBytes(Path.of(filePath));
            byte[] ed = fe.encrypt(fileData);
            Path pathObj = Path.of(filePath);
            Files.write(pathObj.resolveSibling(pathObj.getFileName() + ".key"), key);

            byte[][] encKey = enc.encryptMessage(key);
            cs.transmit(new Packet(Map.of(
                    "content", "key2file",
                    "type", "message",
                    "from", cs.getUsername(),
                    "to", getter,
                    "encrypted", java.util.Arrays.asList(
                            Base64Util.bytesToBase64(encKey[0]),
                            Base64Util.bytesToBase64(encKey[1])))), true);
            cs.waitPacket("status", 5.0);

            byte[][] encName = enc.encryptMessage(pathObj.getFileName().toString().getBytes());
            cs.transmit(new Packet(Map.of(
                    "content", "filedata",
                    "type", "filedata",
                    "from", cs.getUsername(),
                    "to", getter,
                    "encrypted", Base64Util.bytesToBase64(ed),
                    "filename", java.util.Arrays.asList(
                            Base64Util.bytesToBase64(encName[0]),
                            Base64Util.bytesToBase64(encName[1])))), true);
            cs.waitPacket("status", 5.0);
            System.out.println("File sent");
        } catch (Exception e) {
            System.out.println("Send file error: " + e.getMessage());
        }
    }
}
