package ru.gft.decentralizedmessenger.ctp;

import ru.gft.decentralizedmessenger.crypto.Encryption;
import ru.gft.decentralizedmessenger.packet.EncryptedPacket;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.protocol.Connection;
import ru.gft.decentralizedmessenger.protocol.Endpoint;
import ru.gft.decentralizedmessenger.protocol.Stream;
import ru.gft.decentralizedmessenger.util.Base64Util;
import ru.gft.decentralizedmessenger.util.Logger;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mirrors api.ctp.Server.Server — the CTP server over an MTP stream (stream 0).
 */
public class Server {
    private volatile boolean started = false;
    private Endpoint ep;
    private int targetPort;
    private final Map<Stream, Encryption> encryptes = new HashMap<>();
    private final Map<Integer, Thread> handlers = new HashMap<>();
    private int sizeSyncP;
    private String sharedUuid = UUID.randomUUID().toString();
    private final Map<Integer, Client> internalClients = new HashMap<>();
    private final Map<Integer, Thread> internalClientThreads = new HashMap<>();
    private ServerSocket hpServerSocket;
    private Thread hpThread;
    private ServerSocket tcpSocket;
    private volatile boolean disableEncryption = false;

    public interface ClientHandler {
        void handle(Server server, Stream client, InetSocketAddress addr, int thId);
    }

    private ClientHandler handler;

    public Server create(int port, int mssp, boolean runHp) throws Exception {
        this.ep = Endpoint.createNew("0.0.0.0", port);
        this.targetPort = port;
        this.sizeSyncP = mssp;
        return this;
    }

    public void setDisableEncryption(boolean v) { this.disableEncryption = v; }
    public boolean isEncryptionDisabled() { return disableEncryption; }
    public int getSizeSyncP() { return sizeSyncP; }

    public synchronized Encryption initEncrypt(Stream obj) throws Exception {
        Encryption existing = encryptes.get(obj);
        if (existing != null) return existing;
        Encryption enc = new Encryption();
        enc.generateKeypair();
        byte[] keyBytes = enc.serializePublicKey();
        Packet pkt = new Packet(Map.of("type", "key_exchange", "key", Base64Util.bytesToBase64(keyBytes)));
        send(obj, pkt, false);
        Object[] reply = read(obj);
        if (reply[0] == null) throw new RuntimeException("No key exchange reply");
        byte[] objKey = Base64Util.base64ToBytes(((Packet) reply[0]).getString("key", ""));
        enc.deriveSharedKey(Encryption.loadPublicKey(objKey));
        encryptes.put(obj, enc);
        return enc;
    }

    public void ssend(Stream obj, Packet packet, boolean encrypt) {
        try {
            send(obj, packet, encrypt);
        } catch (Exception e) {
            Logger.debug("ssend error: " + e.getMessage());
        }
    }

    public void send(Stream obj, Packet packet, boolean encrypt) throws Exception {
        int packetLen = packet.length();
        if (encrypt) {
            sendEncryptedPkt(obj, packet.getAll());
            return;
        }
        String lenPacket = Packet.staticPacket(Map.of("len", packetLen), sizeSyncP);
        obj.send(lenPacket.getBytes(StandardCharsets.UTF_8));
        obj.send(packet.getBytes());
    }

    private void sendEncryptedPkt(Stream obj, Map<String, Object> data) throws Exception {
        Encryption enc = initEncrypt(obj);
        EncryptedPacket packet = new EncryptedPacket(data, enc);
        int packetLen = packet.length();
        String lenPacket = EncryptedPacket.staticPacket(Map.of("len", packetLen), sizeSyncP, enc);
        obj.send(lenPacket.getBytes(StandardCharsets.UTF_8));
        obj.send(packet.getBytes());
    }

    /** Returns {Packet, Boolean encrypted}. */
    public Object[] read(Stream obj) throws Exception {
        byte[] raw = obj.recv(60000);
        if (raw == null) return new Object[]{null, Boolean.FALSE};
        String rawLen = new String(raw, StandardCharsets.UTF_8);
        if (rawLen.isEmpty()) return new Object[]{null, Boolean.FALSE};
        int packetEnd = rawLen.lastIndexOf('}');
        if (packetEnd < 0) {
            return new Object[]{readEncryptedPkt(obj, rawLen), Boolean.TRUE};
        }
        Object lenVal = Packet.fromRaw(rawLen.substring(0, packetEnd + 1)).get("len");
        if (lenVal == null) return new Object[]{null, Boolean.FALSE};
        int len = ((Number) lenVal).intValue();
        if (len < 1) return new Object[]{null, Boolean.FALSE};
        byte[] rawPacket = obj.recv(60000);
        if (rawPacket == null) return new Object[]{null, Boolean.FALSE};
        return new Object[]{Packet.fromRaw(rawPacket), Boolean.FALSE};
    }

    public Object[] sread(Stream obj, long timeoutMillis) {
        try {
            return read(obj);
        } catch (Exception e) {
            Logger.debug("sread error: " + e.getMessage());
            return new Object[]{null, Boolean.FALSE};
        }
    }

    private Packet readEncryptedPkt(Stream obj, String rawLen) throws Exception {
        Encryption enc = initEncrypt(obj);
        if (rawLen == null || rawLen.isEmpty()) return null;
        if (!rawLen.contains(":encrypted")) return null;
        String payload = EncryptedPacket.extractPayload(rawLen);
        Object lenVal = EncryptedPacket.fromRaw(payload, enc).get("len");
        if (lenVal == null) return null;
        int len = ((Number) lenVal).intValue();
        if (len < 1) return null;
        byte[] rawPacket = obj.recv(60000);
        if (rawPacket == null) return null;
        return EncryptedPacket.fromRaw(rawPacket, enc);
    }

    public void setClientHandler(ClientHandler handler) { this.handler = handler; }

    public void start() throws Exception {
        Logger.info("Bind addr to server");
        started = true;
        Logger.info("Server listening on " + targetPort);
        hpThread = new Thread(this::hpSrv, "ctp-hp");
        hpThread.setDaemon(true);
        hpThread.start();

        while (started) {
            try {
                Connection conn = ep.accept();
                if (conn == null) continue;
                Stream stream = conn.getStream(0, 30000);
                if (stream == null) continue;
                stream.sync();
                int thId = handlers.size();
                final int fid = thId;
                Thread t = new Thread(() -> handler.handle(this, stream, conn.remoteAddr, fid), "ctp-handler-" + fid);
                handlers.put(thId, t);
                t.start();
            } catch (Exception ex) {
                if (started) Logger.info("Error accepting connection: " + ex.getMessage());
            }
        }
    }

    public void stopHandler(int thId) {
        Thread t = handlers.remove(thId);
        if (t != null && t != Thread.currentThread()) {
            try { t.join(1000); } catch (InterruptedException ignored) {}
        }
    }

    public void stop() {
        started = false;
        try { if (hpServerSocket != null) hpServerSocket.close(); } catch (Exception ignored) {}
        try { if (tcpSocket != null) tcpSocket.close(); } catch (Exception ignored) {}
        if (ep != null) ep.close();
    }

    public boolean isStarted() { return started; }

    // ---- internal clients ----

    public int addInternalClient(Client client) {
        int id = internalClients.size();
        client.setUsername("server-" + sharedUuid);
        client.setThread(c -> {
            while (c.isStarted()) {
                try {
                    Object[] in = c.waitPacket("message", 5000);
                    if (in[0] != null && "server".equals(((Packet) in[0]).getString("to", "server"))
                            && ((Packet) in[0]).getString("to", "server").equals(c.getUsername())) {
                        Logger.info(((Packet) in[0]).getString("from", "unknown") + ": "
                                + ((Packet) in[0]).getString("content", "[NULL]"));
                    } else if (in[0] != null) {
                        try { c.transmit((Packet) in[0], (Boolean) in[1]); } catch (Exception ex) { Logger.info(ex.getMessage()); }
                    }
                } catch (Exception ex) { Logger.info(ex.getMessage()); }
            }
        });
        internalClients.put(id, client);
        Thread th = new Thread(client::start, "internal-client-" + id);
        th.setDaemon(true);
        th.start();
        internalClientThreads.put(id, th);
        return id;
    }

    public void removeInternalClient(int id) { internalClients.remove(id); }

    public Client getInternalClient(Integer id) {
        if (id == null) return internalClients.values().stream().findFirst().orElse(null);
        return internalClients.get(id);
    }

    public Map<Integer, Client> getAllInternalClients() { return internalClients; }

    public boolean hasEncryption(Stream s) { return encryptes.containsKey(s); }

    private void hpSrv() {
        try {
            hpServerSocket = new ServerSocket(targetPort, 100, new InetSocketAddress("0.0.0.0", targetPort).getAddress());
            while (started) {
                Socket conn = hpServerSocket.accept();
                new Thread(() -> ru.gft.decentralizedmessenger.hp.RendezvousServer.handleClient(conn), "hp-conn").start();
            }
        } catch (Exception e) {
            if (started) Logger.warning("HP server stopped: " + e.getMessage());
        }
    }
}
