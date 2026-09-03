package ru.gft.decentralizedmessenger.hp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirrors api.hp.Server — a TCP rendezvous server for NAT hole punching.
 * Supports "whoami" and "room" (2-peer pairing) commands.
 */
public class RendezvousServer {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public static class Room {
        public String password;
        public final List<Peer> peers = new ArrayList<>();
    }
    public static class Peer {
        public final Socket conn;
        public final String ip;
        public final int port;
        public final int localPort;
        public Peer(Socket conn, String ip, int port, int localPort) {
            this.conn = conn; this.ip = ip; this.port = port; this.localPort = localPort;
        }
    }

    public static void start(String host, int port) throws Exception {
        ServerSocket srv = new ServerSocket();
        srv.setReuseAddress(true);
        srv.bind(new InetSocketAddress(host, port));
        System.out.println("[*] Rendezvous server listening on " + host + ":" + port);
        while (true) {
            Socket conn = srv.accept();
            new Thread(() -> handleClient(conn), "rendezvous-conn").start();
        }
    }

    public static void handleClient(Socket conn) {
        SocketAddress addr = conn.getRemoteSocketAddress();
        try {
            conn.setSoTimeout(30000);
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = r.readLine();
            if (line == null) { conn.close(); return; }
            Map<String, Object> req = GSON.fromJson(line, MAP_TYPE);
            String cmd = req.getOrDefault("cmd", "room").toString();

            if ("whoami".equals(cmd)) {
                sendJson(conn, Map.of("you", List.of(ipOf(addr), portOf(addr))));
                conn.close();
                System.out.println("[*] whoami for " + addr);
            } else if ("room".equals(cmd)) {
                handleRoom(conn, addr, req);
            } else {
                sendJson(conn, Map.of("error", "unknown cmd '" + cmd + "'"));
                conn.close();
            }
        } catch (Exception e) {
            System.out.println("[!] Error with " + addr + ": " + e.getMessage());
            try { conn.close(); } catch (Exception ignored) {}
        }
    }

    private static void handleRoom(Socket conn, SocketAddress addr, Map<String, Object> req) {
        String room = req.get("room").toString();
        Object pwObj = req.get("password");
        String password = pwObj == null ? null : pwObj.toString();
        int localPort = req.containsKey("local_port")
                ? ((Number) req.get("local_port")).intValue() : portOf(addr);

        Room entry = rooms.computeIfAbsent(room, k -> {
            Room r = new Room();
            r.password = password;
            return r;
        });

        if (!java.util.Objects.equals(entry.password, password)) {
            sendJson(conn, Map.of("error", "bad password"));
            try { conn.close(); } catch (Exception ignored) {}
            System.out.println("[!] " + addr + " rejected by password for room '" + room + "'");
            return;
        }

        synchronized (entry.peers) {
            entry.peers.add(new Peer(conn, ipOf(addr), portOf(addr), localPort));
            System.out.println("[+] " + addr + " joined room '" + room + "' (" + entry.peers.size() + "/2)");
            if (entry.peers.size() == 2) {
                Peer a = entry.peers.get(0);
                Peer b = entry.peers.get(1);
                Map<String, Object> payloadA = Map.of("you", List.of(a.ip, a.localPort), "peer", List.of(b.ip, b.localPort));
                Map<String, Object> payloadB = Map.of("you", List.of(b.ip, b.localPort), "peer", List.of(a.ip, a.localPort));
                sendJson(a.conn, payloadA);
                sendJson(b.conn, payloadB);
                try { a.conn.close(); b.conn.close(); } catch (Exception ignored) {}
                rooms.remove(room);
                System.out.println("[=] Room '" + room + "' closed, addresses sent");
            }
        }
    }

    private static void sendJson(Socket conn, Object obj) {
        try {
            conn.getOutputStream().write((GSON.toJson(obj) + "\n").getBytes());
            conn.getOutputStream().flush();
        } catch (Exception ignored) {}
    }

    private static String ipOf(SocketAddress addr) {
        String s = addr.toString().replaceFirst("^/", "");
        return s.substring(0, s.lastIndexOf(':'));
    }
    private static int portOf(SocketAddress addr) {
        String s = addr.toString().replaceFirst("^/", "");
        return Integer.parseInt(s.substring(s.lastIndexOf(':') + 1));
    }

    public static void main(String[] args) throws Exception {
        String host = "0.0.0.0";
        int port = 9000;
        for (int i = 0; i < args.length; i++) {
            if ("--host".equals(args[i])) host = args[++i];
            else if ("--port".equals(args[i])) port = Integer.parseInt(args[++i]);
        }
        start(host, port);
    }
}
