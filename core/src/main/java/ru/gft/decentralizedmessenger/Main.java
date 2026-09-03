package ru.gft.decentralizedmessenger;

import ru.gft.decentralizedmessenger.commands.CommandManager;
import ru.gft.decentralizedmessenger.commands.client.*;
import ru.gft.decentralizedmessenger.ctp.Client;
import ru.gft.decentralizedmessenger.ctp.Server;
import ru.gft.decentralizedmessenger.packet.Packet;
import ru.gft.decentralizedmessenger.protocol.Stream;
import ru.gft.decentralizedmessenger.util.Base64Util;
import ru.gft.decentralizedmessenger.util.Config;
import ru.gft.decentralizedmessenger.util.Logger;
import ru.gft.decentralizedmessenger.util.Validate;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Standalone CLI entry point — mirrors main.py. */
public class Main {
    private static final String pidUuid = UUID.randomUUID().toString();
    private static final int MAX_SIZE_SYNC_PACKET = 128;

    // server-side state (mirrors main.py globals)
    private static final Map<String, String> nnLs = new ConcurrentHashMap<>();      // name -> "ip:port"
    private static final Map<String, Stream> nnConn = new ConcurrentHashMap<>();    // name -> stream
    private static final Map<String, Integer> thIds = new ConcurrentHashMap<>();    // name -> thId
    private static volatile boolean srvDisableEncryption = false;

    // client-side state
    private static CommandManager cmdm;
    private static String currentGetter = "server";
    // Single shared stdin scanner — creating two Scanners over System.in makes the
    // second one miss input that the first one buffered ahead (lost messages).
    private static final java.util.Scanner STDIN = new java.util.Scanner(System.in);

    public static void main(String[] args) {
        Args parsed = parseArgs(args);
        if (parsed.debug) Logger.setLevel(Logger.Level.DEBUG);
        try {
            run(parsed);
        } catch (Exception e) {
            Logger.error("Fatal: " + e.getMessage());
        }
    }

    // ---- arg parsing (replacement for argparse) ----
    static class Args {
        boolean noUseConfig = false;
        boolean server = false;
        boolean webui = false;
        boolean udpHolePunching = false;
        boolean disableEncryption = false;
        String host = "127.0.0.1";
        int port = 1414;
        boolean debug = false;
    }

    private static Args parseArgs(String[] args) {
        Args a = new Args();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--no-use-config", "-nuc" -> a.noUseConfig = true;
                case "--server", "-s" -> a.server = true;
                case "--webui", "-w" -> a.webui = true;
                case "--udp-hole-punching", "-u" -> a.udpHolePunching = true;
                case "--disable-encryption", "-de" -> a.disableEncryption = true;
                case "--host", "-H" -> a.host = args[++i];
                case "--port", "-p" -> a.port = Integer.parseInt(args[++i]);
                case "--debug", "-d" -> a.debug = true;
                default -> Logger.info("Unknown arg: " + args[i]);
            }
        }
        return a;
    }

    private static void run(Args args) throws Exception {
        boolean useCnf = !args.noUseConfig;
        int mode = args.server ? 0 : 1;
        int uiMode = args.webui ? 1 : 0;
        Config config = useCnf ? new Config("settings.conf") : null;
        srvDisableEncryption = args.disableEncryption;
        if (srvDisableEncryption)
            Logger.warning("Server encryption is disabled. This is not recommended for security reasons.");

        if (useCnf) {
            config.load();
            Integer confMode = config.getInt("mode", null);
            if (confMode != null) mode = confMode; else config.set("mode", mode);
            Integer confUi = config.getInt("ui_mode", null);
            if (confUi != null) uiMode = confUi; else config.set("ui_mode", uiMode);
        }

        Logger.info("Mode: " + mode + "; Use config: " + useCnf + "; UI mode: " + uiMode);

        if (mode == 0) {
            startServer(args);
        } else if (mode == 1) {
            startClient(args, useCnf, config);
        }
    }

    // ---- server ----
    private static void startServer(Args args) throws Exception {
        Server server = new Server().create(args.port, MAX_SIZE_SYNC_PACKET, false);
        server.setDisableEncryption(srvDisableEncryption);
        server.setClientHandler(Main::handleClient4srv);
        server.start();
    }

    private static void handleClient4srv(Server server, Stream client, InetSocketAddress addr, int thId) {
        String addrStr = addr.getAddress().getHostAddress() + ":" + addr.getPort();
        String nn = null;
        try {
            String suffix = "";
            if (!server.isEncryptionDisabled()) {
                server.ssend(client, new Packet(Map.of("type", "enc_type", "no_encryption", false)), false);
                server.initEncrypt(client);
            } else {
                server.ssend(client, new Packet(Map.of("type", "enc_type", "no_encryption", true)), false);
            }
            Object[] first = server.sread(client, 5000);
            nn = first[0] != null ? ((Packet) first[0]).getString("name", null) : null;
            if (nn != null && nnConn.containsKey(nn)) {
                String nnAddr = nnLs.get(nn);
                Logger.warning("Client " + nn + " already connected. Pinging " + nnAddr + "...");
                try {
                    Stream old = nnConn.get(nn);
                    Object[] ping = server.sread(old, 2000);
                    if (ping[0] == null) throw new RuntimeException("");
                    client.close();
                    server.stopHandler(thId);
                } catch (Exception e) {
                    Logger.warning("Connection of " + nnAddr + " out of date");
                    nnLs.remove(nn);
                    Stream old = nnConn.remove(nn);
                    thIds.remove(nn);
                    if (old != null) try { old.close(); } catch (Exception ignored) {}
                    Logger.info("Disconnected " + nnAddr);
                    suffix = "(Reconnect) ";
                }
            }

            if (nn != null) {
                nnLs.put(nn, addrStr);
                nnConn.put(nn, client);
                thIds.put(nn, thId);
            }
            server.ssend(client, new Packet(Map.of("type", "ready", "ok", true)), !server.isEncryptionDisabled());
            Object[] status = server.sread(client, 5000);
            Logger.info(suffix + "Client(" + nn + ") connected!");

            while (server.isStarted() && thIds.containsValue(thId) && nn != null && nnConn.containsKey(nn)) {
                Object[] data = server.sread(client, 60000);
                Packet packet = (Packet) data[0];
                boolean enc = data[1] != null && (Boolean) data[1];
                if (packet == null) continue;

                if (packet.get("ping") != null) {
                    double clientTs = ((Number) packet.get("timestamp")).doubleValue();
                    double serverTs = System.currentTimeMillis();
                    if (serverTs - clientTs > ((Number) packet.get("ping")).doubleValue() * 1000) {
                        server.ssend(client, new Packet(Map.of("type", "status", "ok", false)), enc && !server.isEncryptionDisabled());
                        continue;
                    }
                    server.ssend(client, new Packet(Map.of("type", "status", "ok", true)), enc && !server.isEncryptionDisabled());
                    Logger.info("Server gotten ping packet. Packet latency: " + (int) (serverTs - clientTs) + "ms");

                } else if (packet.get("stopsrv") != null) {
                    if (!"127.0.0.1".equals(addr.getAddress().getHostAddress())) {
                        server.ssend(client, new Packet(Map.of("type", "status", "ok", false, "error", "You are not host")), enc && !server.isEncryptionDisabled());
                        continue;
                    }
                    Logger.info("Stopping server...");
                    server.ssend(client, new Packet(Map.of("type", "status", "ok", true)), enc && !server.isEncryptionDisabled());
                    server.stop();

                } else if (packet.get("is_online") != null) {
                    String testNn = packet.getString("is_online", "");
                    if (nnConn.containsKey(testNn)) {
                        server.ssend(client, new Packet(Map.of("type", "online_check", "online", true)), enc && !server.isEncryptionDisabled());
                        continue;
                    }
                    Client internal = server.getInternalClient(null);
                    if (internal == null) {
                        server.ssend(client, new Packet(Map.of("type", "online_check", "online", false)), enc && !server.isEncryptionDisabled());
                        continue;
                    }
                    internal.send(new Packet(Map.of("type", "online_check", "is_online", testNn)), enc && !server.isEncryptionDisabled());
                    Object[] resp = internal.waitPacket("online_check", 5.0);
                    server.ssend(client, (Packet) resp[0], enc && !server.isEncryptionDisabled());

                } else if (packet.get("name") != null) {
                    String newName = packet.getString("name", "");
                    if (newName.isEmpty()) {
                        server.ssend(client, new Packet(Map.of("type", "status", "ok", false)), enc && !server.isEncryptionDisabled());
                        continue;
                    }
                    String oldName = nn;
                    nn = newName;
                    nnLs.put(nn, addrStr);
                    nnConn.put(nn, client);
                    nnConn.remove(oldName);
                    Logger.info("User change name: " + oldName + " -> " + nn);
                    server.ssend(client, new Packet(Map.of("type", "status", "ok", true)), enc && !server.isEncryptionDisabled());

                } else if (packet.get("get_address") != null) {
                    String testNn = packet.getString("get_address", "");
                    if (nnConn.containsKey(testNn)) {
                        server.ssend(client, new Packet(Map.of("type", "get_address", "address", nnLs.get(testNn))), enc && !server.isEncryptionDisabled());
                    } else {
                        server.ssend(client, new Packet(Map.of("type", "get_address", "address", false)), enc && !server.isEncryptionDisabled());
                    }

                } else if (packet.get("disconnect") != null) {
                    server.stopHandler(thId);

                } else {
                    Object getterObj = packet.get("to");
                    String getter = getterObj == null ? null : getterObj.toString();
                    if (getter == null) continue;

                    if ("server".equals(getter)) {
                        Logger.info("[" + nn + "] " + packet.getString("content", null));
                        continue;
                    }
                    if (nn != null && nn.equals(getter)) continue;

                    Stream conn = nnConn.get(getter);
                    boolean hasEnc = server.hasEncryption(conn);
                    if (conn != null) {
                        server.ssend(conn, packet, hasEnc && !server.isEncryptionDisabled());
                        continue;
                    }
                    if (Boolean.TRUE.equals(packet.get("transmit"))) continue;

                    Client internal = server.getInternalClient(null);
                    if (internal == null) continue;
                    internal.send(packet, true);
                }
            }
        } catch (Exception e) {
            Logger.debug("Server handler error: " + e.getMessage());
        } finally {
            String name = null;
            for (Map.Entry<String, String> e : nnLs.entrySet()) {
                if (e.getValue().equals(addrStr)) { name = e.getKey(); break; }
            }
            String displayName = name != null ? name : "UNKNOWN";
            Logger.info(displayName + " has been disconnected");
            if (name != null) {
                nnLs.remove(name);
                nnConn.remove(name);
                thIds.remove(name);
            }
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // ---- client ----
    private static void startClient(Args args, boolean useCnf, Config config) throws Exception {
        Scanner stdin = STDIN;
        String addr = null;
        Integer port = null;
        if (useCnf) { addr = config.getString("address", null); port = config.getInt("port", null); }
        if (addr == null || port == null) {
            System.out.print("Enter target (addr:port): ");
            String[] raw = stdin.nextLine().trim().split(":");
            if (raw.length < 2) { Logger.info("Invalid input format."); return; }
            addr = raw[0];
            port = Integer.parseInt(raw[1]);
        }
        if (!Validate.validateTarget(addr)) { Logger.info("Invalid IP address or domain"); return; }
        if (useCnf) { config.set("address", addr); config.set("port", port); }

        String nickname = null;
        if (useCnf) nickname = config.getString("nickname", null);
        if (nickname == null || nickname.isEmpty()) {
            System.out.print("Enter your name: ");
            nickname = stdin.nextLine().trim();
        }
        if (useCnf) config.set("nickname", nickname);
        System.out.print("Enter password: ");
        String password = stdin.nextLine();
        if (useCnf) config.save();

        Client client = new Client(addr, port, nickname, password, MAX_SIZE_SYNC_PACKET);
        client.setThread(Main::handleClient4clnt);
        client.start();
    }

    private static void handleClient4clnt(Client client) {
        cmdm = new CommandManager();
        client.getContext().commandManager = cmdm;
        registerCommands(cmdm);

        Scanner stdin = STDIN;
        while (client.isStarted()) {
            System.out.print("msg: ");
            if (!stdin.hasNextLine()) break;
            String msg = stdin.nextLine();
            if (msg.startsWith("/")) {
                String cmd = msg.toLowerCase().replace("/", "").split(" ")[0];
                var mbCmd = cmdm.getCMD(cmd);
                if (mbCmd != null) mbCmd.execute(client);
                else Logger.info("Command doesn't exist!");
            } else {
                var encript = client.getEncript(currentGetter);
                if (encript != null) {
                    byte[][] enc = encript.encryptMessage(msg.getBytes(StandardCharsets.UTF_8));
                    client.transmit(new Packet(Map.of(
                            "type", "message",
                            "content", "Encrypted",
                            "from", client.getUsername(),
                            "to", currentGetter,
                            "encrypted", java.util.Arrays.asList(
                                    Base64Util.bytesToBase64(enc[0]),
                                    Base64Util.bytesToBase64(enc[1])))), true);
                } else {
                    client.transmit(new Packet(Map.of(
                            "type", "message",
                            "content", msg,
                            "from", client.getUsername(),
                            "to", currentGetter)), true);
                }
            }
        }
    }

    private static void registerCommands(CommandManager cmdm) {
        Map<String, ru.gft.decentralizedmessenger.commands.Command> cmds = new LinkedHashMap<>();
        cmds.put("help", new HelpCommand());
        cmds.put("cc", new CcCommand());
        cmds.put("checksec", new CheckSecCommand());
        cmds.put("q", new QCommand());
        cmds.put("raw", new RawCommand());
        cmds.put("read", new ReadCommand());
        cmds.put("to", new ToCommand());
        cmds.put("synckeys", new SyncKeysCommand());
        cmds.put("sendfile", new SendFileCommand());
        cmds.put("voice", new VoiceCommand());
        cmdm.registerCMDs(cmds);
    }
}
