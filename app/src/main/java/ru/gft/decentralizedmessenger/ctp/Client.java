package ru.gft.decentralizedmessenger.ctp;

import ru.gft.decentralizedmessenger.commands.ClientContext;
import ru.gft.decentralizedmessenger.commands.CommandSender;
import ru.gft.decentralizedmessenger.crypto.Encryption;
import ru.gft.decentralizedmessenger.crypto.SecureEncryption;
import ru.gft.decentralizedmessenger.packet.EncryptedPacket;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.protocol.Endpoint;
import ru.gft.decentralizedmessenger.protocol.Stream;
import ru.gft.decentralizedmessenger.util.Base64Util;
import ru.gft.decentralizedmessenger.util.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors api.ctp.Client.Client — the CTP client over an MTP stream (stream 0).
 * Implements CommandSender so client commands can drive it.
 */
public class Client implements CommandSender {
    private Stream stream;
    private volatile boolean started = false;
    public final String targetAddr;
    public final int targetPort;
    private String username;
    private final String password;
    public final int sizeSyncP;
    private final Map<String, SecureEncryption> encripts = new ConcurrentHashMap<>();
    private Encryption srvEnc = new Encryption();
    private final Map<String, Object[]> packets = new ConcurrentHashMap<>();
    private Thread packetThread;
    private ClientThread thread;
    private final ClientContext context = new ClientContext();

    @FunctionalInterface
    public interface ClientThread { void run(Client client); }

    public Client(String addr, int port, String username, String password, int mssp) throws Exception {
        Endpoint ep = Endpoint.createNew("127.0.0.1".equals(addr) ? "127.0.0.1" : "0.0.0.0", 0);
        InetSocketAddress resolved = resolve(ep, addr, port);
        var conn = ep.connect(resolved);
        this.stream = conn.openStream(0, true, true);
        try {
            stream.sync();
        } catch (Exception e) {
            throw new RuntimeException("Timeout while waiting for sending sync packet to " + addr + ":" + port, e);
        }
        this.targetAddr = addr;
        this.targetPort = port;
        this.sizeSyncP = mssp;
        this.username = username;
        this.password = password;
    }

    private InetSocketAddress resolve(Endpoint ep, String addr, int port) throws Exception {
        java.net.InetAddress ia = java.net.InetAddress.getByName(addr);
        return new InetSocketAddress(ia, port);
    }

    public String getUsername() { return username; }

    @Override
    public ClientContext getContext() { return context; }

    public void sendUsername() {
        if (!started || username.isEmpty()) return;
        send(new Packet(Map.of("name", username, "password", password)), true);
    }

    public void setUsername(String username) {
        if (username == null || username.isEmpty()) return;
        this.username = username;
        sendUsername();
    }

    @Override
    public void send(Packet packet, boolean encrypt) {
        try {
            if (encrypt && connectionIsSecure()) {
                sendEncryptedPkt(packet.getAll());
                return;
            }
            int packetLen = packet.length();
            String lenPacket = Packet.staticPacket(Map.of("len", packetLen), sizeSyncP);
            stream.send(lenPacket.getBytes(StandardCharsets.UTF_8));
            stream.send(packet.getBytes());
        } catch (Exception e) {
            Logger.debug("Client send error: " + e.getMessage());
        }
    }

    private void sendEncryptedPkt(Map<String, Object> data) {
        try {
            EncryptedPacket packet = new EncryptedPacket(data, srvEnc);
            int packetLen = packet.length();
            String lenPacket = EncryptedPacket.staticPacket(Map.of("len", packetLen), sizeSyncP, srvEnc);
            stream.send(lenPacket.getBytes(StandardCharsets.UTF_8));
            stream.send(packet.getBytes());
        } catch (Exception e) {
            Logger.debug("Client sendEncryptedPkt error: " + e.getMessage());
        }
    }

    @Override
    public Object[] read(int timeoutSeconds) {
        try {
            byte[] raw = stream.recv(timeoutSeconds * 1000L);
            if (raw == null) return new Object[]{null, Boolean.FALSE};
            String rawLen = new String(raw, StandardCharsets.UTF_8);
            if (rawLen.isEmpty()) return new Object[]{null, Boolean.FALSE};
            int packetEnd = rawLen.lastIndexOf('}');
            if (packetEnd < 0 && connectionIsSecure()) {
                return new Object[]{readEncryptedPkt(rawLen), Boolean.TRUE};
            }
            rawLen = rawLen.substring(0, packetEnd + 1);
            Object lenVal = Packet.fromRaw(rawLen).get("len");
            int len = lenVal == null ? sizeSyncP : ((Number) lenVal).intValue();
            if (len < 1) return new Object[]{null, Boolean.FALSE};
            byte[] rawPacket = stream.recv(timeoutSeconds * 1000L);
            if (rawPacket == null) return new Object[]{null, Boolean.FALSE};
            return new Object[]{Packet.fromRaw(rawPacket), Boolean.FALSE};
        } catch (Exception e) {
            return new Object[]{null, Boolean.FALSE};
        }
    }

    private Packet readEncryptedPkt(String rawLen) {
        if (!connectionIsSecure()) return null;
        try {
            if (rawLen == null || rawLen.isEmpty() || !rawLen.contains(":encrypted")) return null;
            String payload = EncryptedPacket.extractPayload(rawLen);
            Object lenVal = EncryptedPacket.fromRaw(payload, srvEnc).get("len");
            if (lenVal == null) return null;
            int len = ((Number) lenVal).intValue();
            if (len < 1) return null;
            byte[] rawPacket = stream.recv(60000);
            if (rawPacket == null) return null;
            return EncryptedPacket.fromRaw(rawPacket, srvEnc);
        } catch (Exception e) {
            return null;
        }
    }

    public String decrypt(Packet pkt) {
        String sender = pkt.getString("from", null);
        if (sender == null) return null;
        SecureEncryption enc = getEncript(sender);
        if (enc == null) return null;
        if (!username.equals(pkt.getString("to", null))) return null;
        Object encryptedObj = pkt.get("encrypted");
        if (!(encryptedObj instanceof java.util.List)) return null;
        java.util.List<?> encrypted = (java.util.List<?>) encryptedObj;
        String nonce = (String) encrypted.get(0);
        String ciphertext = (String) encrypted.get(1);
        byte[] dec = enc.decryptMessage(Base64Util.base64ToBytes(nonce), Base64Util.base64ToBytes(ciphertext));
        return new String(dec, StandardCharsets.UTF_8);
    }

    public void setThread(ClientThread th) { this.thread = th; }

    public void start() {
        started = true;
        packetThread = new Thread(this::packetHandler, "client-packets");
        packetThread.setDaemon(true);
        packetThread.start();

        try {
            Object[] data = waitPacket("key_exchange", 15);
            if (data[0] == null) {
                Logger.error("No key exchange packet received. Secure connection will not be used.");
                srvEnc = null;
            } else {
                Packet srvKey = (Packet) data[0];
                if (srvKey.get("no_encryption") != null) {
                    throw new RuntimeException("Server has encryption disabled");
                }
                srvEnc.generateKeypair();
                byte[] keyBytes = srvEnc.serializePublicKey();
                send(new Packet(Map.of("type", "key_exchange", "key", Base64Util.bytesToBase64(keyBytes))), false);
                byte[] peerKey = Base64Util.base64ToBytes(srvKey.getString("key", ""));
                srvEnc.deriveSharedKey(Encryption.loadPublicKey(peerKey));
            }
        } catch (Exception exc) {
            Logger.error("Error establishing secure connection: " + exc.getMessage());
            srvEnc = null;
        }

        sendUsername();
        Object[] data = waitPacket("ready", 15);
        if (data[0] == null) {
            Logger.error("No ready packet received. Connection may not be fully established.");
            started = false;
            return;
        }
        boolean enc = (Boolean) data[1];
        send(new Packet(Map.of("type", "ready", "ready", true)), enc);
        Logger.info("Ready!");

        if (thread != null) thread.run(this);
        stop();
    }

    public void stop() {
        started = false;
        try {
            send(new Packet(Map.of("type", "connection_info", "disconnect", true)), true);
            if (packetThread != null) packetThread.join(2000);
        } catch (Exception e) {
            Logger.info("Stop exception: " + e.getMessage());
        }
    }

    public boolean isStarted() { return started; }

    @Override
    public void transmit(Packet packet, boolean encrypt) {
        if (!started) throw new RuntimeException("Client socket is closed");
        packet.set("transmit", true);
        send(packet, encrypt);
    }

    public boolean[] cc(int timeout) {
        long ts = System.currentTimeMillis();
        send(new Packet(Map.of("type", "cc", "ping", timeout, "timestamp", ts)), true);
        Object[] resp = waitPacket("cc", timeout);
        boolean serverStatus = resp[0] != null && Boolean.TRUE.equals(((Packet) resp[0]).get("ok"));
        long endTs = System.currentTimeMillis();
        return new boolean[]{serverStatus && (endTs - ts) <= timeout * 1000L};
    }

    @Override
    public boolean[] checkConnection(int timeout) {
        try {
            return cc(timeout);
        } catch (Exception e) {
            return new boolean[]{false};
        }
    }

    @Override
    public boolean connectionIsSecure() { return srvEnc != null; }

    private SecureEncryption initEncript(String to) {
        SecureEncryption enc = encripts.get(to);
        if (enc != null) return enc;
        enc = new SecureEncryption(username);
        enc.generateSigningKeypair();
        enc.generateKeypair();
        encripts.put(to, enc);
        return enc;
    }

    @Override
    public SecureEncryption getEncript(String to) { return encripts.get(to); }

    @Override
    public void sendKey(String to) {
        SecureEncryption enc = initEncript(to);
        byte[] xPub = enc.serializeX25519Public();
        byte[] edPub = enc.serializeEd25519Public();
        byte[] signature = enc.signMessage(concat(xPub, to.getBytes(StandardCharsets.UTF_8)));
        Map<String, Object> data = new HashMap<>();
        data.put("type", "key_exchange");
        data.put("x25519_pub", Base64Util.bytesToBase64(xPub));
        data.put("ed25519_pub", Base64Util.bytesToBase64(edPub));
        data.put("signature", Base64Util.bytesToBase64(signature));
        data.put("to", to);
        data.put("from", username);
        send(new Packet(data), true);
    }

    @Override
    public void readKey(String sender, int timeout) {
        SecureEncryption enc = initEncript(sender);
        Packet packetWithKey = null;
        while (packetWithKey == null) {
            Object[] data = waitPacket("key_exchange", timeout);
            if (data[0] == null) return;
            if (((Packet) data[0]).get("signature") != null) packetWithKey = (Packet) data[0];
        }
        byte[] peerX25519 = Base64Util.base64ToBytes(packetWithKey.getString("x25519_pub", ""));
        byte[] peerEd25519 = Base64Util.base64ToBytes(packetWithKey.getString("ed25519_pub", ""));
        byte[] peerSig = Base64Util.base64ToBytes(packetWithKey.getString("signature", ""));
        if (!enc.verifySignature(concat(peerX25519, username.getBytes(StandardCharsets.UTF_8)), peerSig, peerEd25519)) {
            Logger.info("Invalid signature!");
            return;
        }
        java.util.Scanner stdin = new java.util.Scanner(System.in);
        enc.verifyPeerManually(sender, peerEd25519, peerX25519, stdin);
        enc.deriveSharedKey(toX25519Public(peerX25519));
    }

    private static org.bouncycastle.crypto.params.X25519PublicKeyParameters toX25519Public(byte[] data) {
        return new org.bouncycastle.crypto.params.X25519PublicKeyParameters(data, 0);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private void packetHandler() {
        while (started) {
            try {
                Object[] data = read(6);
                if (data[0] == null) continue;
                String type = ((Packet) data[0]).getString("type", "unknown");
                packets.put(type, data);
            } catch (Exception e) {
                Logger.error("Error while reading packet: " + e.getMessage());
            }
        }
    }

    @Override
    public Packet getPacket(String type) {
        Object[] d = packets.get(type);
        return d == null ? null : (Packet) d[0];
    }

    @Override
    public Object[] waitPacket(String type, double timeoutSeconds) {
        long start = System.currentTimeMillis();
        long timeoutMs = (long) (timeoutSeconds * 1000);
        while (System.currentTimeMillis() - start < timeoutMs) {
            Object[] d = packets.remove(type);
            if (d != null) return new Object[]{d[0], d[1]};
            try { Thread.sleep(10); } catch (InterruptedException e) { return new Object[]{null, Boolean.FALSE}; }
        }
        return new Object[]{null, Boolean.FALSE};
    }
}
