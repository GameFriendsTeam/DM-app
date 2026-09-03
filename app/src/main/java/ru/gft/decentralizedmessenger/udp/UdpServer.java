package ru.gft.decentralizedmessenger.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/** Mirrors api.udp.UDPServer.UDPServer. */
public class UdpServer {
    private volatile boolean started = false;
    private final DatagramSocket socket;
    public final int targetPort;
    public final int sizeSyncP;

    public UdpServer(int port, int mssp) throws Exception {
        this.socket = new DatagramSocket(port);
        this.targetPort = port;
        this.sizeSyncP = mssp;
    }

    public void send(String targetAddr, int targetPort, byte[] rawPacket) throws Exception {
        socket.send(new DatagramPacket(rawPacket, rawPacket.length,
                new InetSocketAddress(targetAddr, targetPort)));
    }

    public DatagramPacket read(int buffer) throws Exception {
        byte[] buf = new byte[buffer];
        DatagramPacket dp = new DatagramPacket(buf, buffer);
        socket.receive(dp);
        return dp;
    }

    public interface Handler { void handle(UdpServer server, DatagramSocket sock); }

    private Handler handler;
    public void setClientHandler(Handler h) { this.handler = h; }

    public void start() {
        started = true;
        if (handler != null) {
            Thread t = new Thread(() -> handler.handle(this, socket), "udp-server");
            t.setDaemon(true);
            t.start();
        }
    }

    public void stop() {
        started = false;
        if (socket != null) socket.close();
    }

    public boolean isStarted() { return started; }
    public DatagramSocket getSocket() { return socket; }
}
