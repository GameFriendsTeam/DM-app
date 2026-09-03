package ru.gft.decentralizedmessenger.protocol;

import ru.gft.decentralizedmessenger.util.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Mirrors api.protocol.endpoint.Endpoint — a UDP socket that demuxes packets
 * into per-peer Connection objects. Uses a blocking receive thread.
 */
public class Endpoint {
    private final Map<InetSocketAddress, Connection> connections = new HashMap<>();
    private final BlockingQueue<Connection> newConnQueue = new LinkedBlockingQueue<>();
    private java.net.DatagramSocket socket;
    private volatile boolean running = false;
    private Thread receiveThread;

    public Endpoint create(String host, int port) throws Exception {
        socket = new java.net.DatagramSocket(port, InetAddress.getByName(host));
        running = true;
        receiveThread = new Thread(this::receiveLoop, "mtp-endpoint");
        receiveThread.setDaemon(true);
        receiveThread.start();
        Logger.info("MTP Endpoint bound to " + socket.getLocalSocketAddress());
        return this;
    }

    public static Endpoint createNew(String host, int port) throws Exception {
        return new Endpoint().create(host, port);
    }

    public Connection connect(InetSocketAddress remoteAddr) {
        synchronized (connections) {
            Connection existing = connections.get(remoteAddr);
            if (existing != null) return existing;
            Connection conn = new Connection(remoteAddr, this::doSend);
            conn.start();
            connections.put(remoteAddr, conn);
            Logger.info("Connected to " + remoteAddr);
            return conn;
        }
    }

    public Connection connect(String host, int port) throws UnknownHostException {
        return connect(new InetSocketAddress(InetAddress.getByName(host), port));
    }

    public Connection accept() throws InterruptedException {
        return newConnQueue.take();
    }

    public Connection accept(long timeoutMillis) throws InterruptedException {
        return newConnQueue.poll(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void close() {
        running = false;
        synchronized (connections) {
            for (Connection c : connections.values()) c.close();
        }
        if (socket != null) socket.close();
        if (receiveThread != null) receiveThread.interrupt();
    }

    public InetSocketAddress getLocalAddr() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    private void doSend(InetSocketAddress addr, byte[] data) {
        try {
            socket.send(new java.net.DatagramPacket(data, data.length, addr));
        } catch (Exception e) {
            Logger.warning("UDP send error: " + e.getMessage());
        }
    }

    private void receiveLoop() {
        byte[] buf = new byte[MtpPacket.HEADER_SIZE + MtpPacket.MAX_PAYLOAD];
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                java.net.DatagramPacket dp = new java.net.DatagramPacket(buf, buf.length);
                socket.receive(dp);
                byte[] data = new byte[dp.getLength()];
                System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                MtpPacket pkt = MtpPacket.decode(data);
                InetSocketAddress addr = new InetSocketAddress(dp.getAddress(), dp.getPort());
                onPacket(pkt, addr);
            } catch (Exception e) {
                if (running) Logger.debug("Bad packet: " + e.getMessage());
            }
        }
    }

    private void onPacket(MtpPacket pkt, InetSocketAddress addr) {
        Connection conn;
        synchronized (connections) {
            conn = connections.get(addr);
            if (conn == null) {
                conn = new Connection(addr, this::doSend);
                conn.start();
                connections.put(addr, conn);
                Logger.info("New connection from " + addr);
                newConnQueue.offer(conn);
            }
        }
        conn.packetReceived(pkt);
    }

    @Override
    public String toString() { return "Endpoint(conns=" + connections.size() + ", addr=" + getLocalAddr() + ")"; }
}
