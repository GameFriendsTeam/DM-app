package ru.gft.decentralizedmessenger.hp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mirrors api.hp.UDP — rendezvous helpers + UDP NAT hole punching. */
public class HolePuncher {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    public static Map<String, Object> rendezvousRequest(String host, int port, int localPort, Map<String, Object> payload) throws Exception {
        Socket s = new Socket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress("0.0.0.0", localPort));
        s.connect(new InetSocketAddress(host, port));
        s.getOutputStream().write((GSON.toJson(payload) + "\n").getBytes());
        s.getOutputStream().flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String line = r.readLine();
        s.close();
        return GSON.fromJson(line, MAP_TYPE);
    }

    public static String[] getOwnAddress(String host, int port, int localPort) throws Exception {
        Map<String, Object> data = rendezvousRequest(host, port, localPort, Map.of("cmd", "whoami"));
        Object you = data.get("you");
        if (you instanceof java.util.List) {
            java.util.List<?> l = (java.util.List<?>) you;
            return new String[]{l.get(0).toString(), l.get(1).toString()};
        }
        return new String[]{null, "0"};
    }

    public static String[][] getPeerViaRoom(String host, int port, String room, String password, int localPort) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>(Map.of("cmd", "room", "room", room, "local_port", localPort));
        if (password != null) payload.put("password", password);
        Map<String, Object> data = rendezvousRequest(host, port, localPort, payload);
        if (data.containsKey("error")) throw new RuntimeException("Rendezvous refused: " + data.get("error"));
        java.util.List<?> you = (java.util.List<?>) data.get("you");
        java.util.List<?> peer = (java.util.List<?>) data.get("peer");
        return new String[][]{
                new String[]{you.get(0).toString(), you.get(1).toString()},
                new String[]{peer.get(0).toString(), peer.get(1).toString()}
        };
    }

    /** Continuously send empty "punch" packets to the peer until the channel is open. */
    public static void punch(DatagramSocket sock, InetSocketAddress peerAddr, AtomicBoolean stopEvent) {
        Thread t = new Thread(() -> {
            byte[] msg = "punch".getBytes();
            while (!stopEvent.get() && !Thread.currentThread().isInterrupted()) {
                try { sock.send(new DatagramPacket(msg, msg.length, peerAddr)); } catch (Exception ignored) {}
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }, "udp-punch");
        t.setDaemon(true);
        t.start();
    }

    public static int[] parseAddr(String s) {
        if (s == null || s.isEmpty()) return new int[]{0, 0};
        int idx = s.lastIndexOf(':');
        String host = s.substring(0, idx);
        int port = Integer.parseInt(s.substring(idx + 1));
        return new int[]{0, port}; // host returned separately
    }

    public static String[] parseAddrStr(String s) {
        if (s == null || s.isEmpty()) return new String[]{null, "0"};
        int idx = s.lastIndexOf(':');
        return new String[]{s.substring(0, idx), s.substring(idx + 1)};
    }
}
