package ru.gft.decentralizedmessenger.commands.client;

import ru.gft.decentralizedmessenger.commands.Command;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.hp.HolePuncher;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.udp.UdpClient;
import ru.gft.decentralizedmessenger.udp.UdpServer;
import ru.gft.decentralizedmessenger.util.Audio;
import ru.gft.decentralizedmessenger.util.Base64Util;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mirrors api.commands.client.VoiceCMD.VoiceCMD — encrypted voice over UDP + NAT hole punching. */
public class VoiceCommand extends Command {
    @Override
    public void execute(CommandSender cs) {
        var enc = cs.getEncript(cs.getContext().currentGetter);
        if (enc == null) { System.out.println("Encryption not initialized"); return; }

        int chunk = 1024;
        int port = 4444;
        Audio audio;
        try {
            audio = new Audio(chunk, 1, 16000);
        } catch (Exception e) {
            System.out.println("Audio unavailable: " + e.getMessage());
            return;
        }

        Scanner stdin = new Scanner(System.in);
        System.out.print("Enter address of server with support UDP punch hole (format: ipV4:port): ");
        String[] addrData = stdin.nextLine().trim().split(":");
        if (addrData.length < 2) { System.out.println("Invalid format"); return; }
        String rhost = addrData[0];
        int rport;
        try { rport = Integer.parseInt(addrData[1]); } catch (Exception e) { System.out.println(e.getMessage()); return; }

        try {
            String[] you = HolePuncher.getOwnAddress(rhost, rport, port);
            String youStr = you[0] + ":" + you[1];

            byte[][] encAddr = enc.encryptMessage(youStr.getBytes());
            cs.transmit(new Packet(Map.of(
                    "type", "my_addr",
                    "my_addr", Arrays.asList(Base64Util.bytesToBase64(encAddr[0]), Base64Util.bytesToBase64(encAddr[1])),
                    "to", cs.getContext().currentGetter)), true);

            Object[] peerPkt = cs.waitPacket("my_addr", 5.0);
            if (peerPkt[0] == null) { System.out.println("Peer addr not gotten"); return; }
            Object myAddrObj = ((Packet) peerPkt[0]).get("my_addr");
            if (!(myAddrObj instanceof List)) { System.out.println("Peer addr not gotten"); return; }
            List<?> addrParts = (List<?>) myAddrObj;
            byte[] decAddr = enc.decryptMessage(
                    Base64Util.base64ToBytes((String) addrParts.get(0)),
                    Base64Util.base64ToBytes((String) addrParts.get(1)));
            String[] peer = HolePuncher.parseAddrStr(new String(decAddr));
            String targetAddr = peer[0];
            int peerPort = Integer.parseInt(peer[1]);
            if (targetAddr == null) { System.out.println("Peer addr not gotten"); return; }

            UdpServer udpS = new UdpServer(port, cs.getContext().maxSizeSyncPacket);
            UdpClient udpC = new UdpClient(targetAddr, peerPort, cs.getContext().maxSizeSyncPacket);

            udpC.setThread(client -> {
                while (client.isStarted()) {
                    try {
                        byte[] data = audio.readChunk();
                        if (data.length == 0) continue;
                        byte[][] encBytes = clientEnc(cs, data);
                        String toSend = Base64Util.bytesToBase64(encBytes[0]) + ":" + Base64Util.bytesToBase64(encBytes[1]);
                        client.send(targetAddr, port, toSend.getBytes());
                    } catch (Exception e) { break; }
                }
            });
            new Thread(udpC::start, "voice-send").start();

            udpS.setClientHandler((srv, sock) -> {
                while (srv.isStarted()) {
                    try {
                        java.net.DatagramPacket dp = srv.read(chunk * 2);
                        String[] parts = new String(dp.getData(), 0, dp.getLength()).split(":");
                        byte[] dec = enc.decryptMessage(Base64Util.base64ToBytes(parts[0]), Base64Util.base64ToBytes(parts[1]));
                        audio.speak(dec);
                    } catch (Exception e) { /* ignore */ }
                }
            });
            new Thread(udpS::start, "voice-recv").start();
        } catch (Exception e) {
            System.out.println("Voice error: " + e.getMessage());
        }
    }

    private byte[][] clientEnc(CommandSender cs, byte[] data) {
        var enc = cs.getEncript(cs.getContext().currentGetter);
        return enc.encryptMessage(data);
    }
}
