package ru.gft.decentralizedmessenger.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/** Mirrors api.udp.UDPClient.UDPClient. */
public class UdpClient {
    private volatile boolean started = false;
    private final DatagramSocket socket;
    public final String targetAddr;
    public final int targetPort;
    public final int sizeSyncP;
    public final String username;

    public UdpClient(String addr, int port, int mssp) throws Exception {
        this.socket = new DatagramSocket();
        this.targetAddr = addr;
        this.targetPort = port;
        this.sizeSyncP = mssp;
        this.username = "";
    }

    public void send(byte[] rawPacket) throws Exception {
        socket.send(new DatagramPacket(rawPacket, rawPacket.length,
                new InetSocketAddress(targetAddr, targetPort)));
    }

    public void send(String addr, int port, byte[] rawPacket) throws Exception {
        socket.send(new DatagramPacket(rawPacket, rawPacket.length,
                new InetSocketAddress(addr, port)));
    }

    public byte[] read(int buffer) throws Exception {
        byte[] buf = new byte[buffer];
        DatagramPacket dp = new DatagramPacket(buf, buffer);
        socket.receive(dp);
        return java.util.Arrays.copyOf(dp.getData(), dp.getLength());
    }

    public interface Handler { void handle(UdpClient client); }

    private Handler handler;
    public void setThread(Handler h) { this.handler = h; }

    public void start() {
        try {
            socket.connect(new InetSocketAddress(targetAddr, targetPort));
        } catch (Exception e) {
            stop();
            return;
        }
        started = true;
        if (handler != null) handler.handle(this);
        stop();
    }

    public void stop() {
        if (socket != null) socket.close();
        started = false;
    }

    public boolean isStarted() { return started; }
    public DatagramSocket getSocket() { return socket; }
}
