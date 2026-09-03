package ru.gft.decentralizedmessenger.protocol;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors api.protocol.connection.Connection — multiplexer over a single UDP path.
 * Manages streams, reliable retransmission and delayed ACKs.
 */
public class Connection {
    private static final long RETRANSMIT_TIMEOUT_MS = 200;
    private static final int MAX_RETRANSMITS = 8;
    private static final long ACK_DELAY_MS = 10;

    public final InetSocketAddress remoteAddr;
    private final DatagramTransport transport;

    private final Map<Integer, Stream> streams = new ConcurrentHashMap<>();
    private final Map<Long, PendingPacket> pending = new HashMap<>();
    private final Map<Integer, Set<Integer>> ackPending = new HashMap<>();
    private final Map<Integer, LinkedBlockingQueue<Stream>> streamWaiters = new HashMap<>();

    private volatile boolean closed = false;
    private Thread retransmitThread;
    private Thread ackThread;

    public interface DatagramTransport { void send(InetSocketAddress addr, byte[] data); }

    private static final class PendingPacket {
        final MtpPacket pkt;
        final int streamId;
        final int seq;
        volatile long deadline;
        int attempts;
        PendingPacket(MtpPacket pkt, int streamId, int seq, long deadline) {
            this.pkt = pkt; this.streamId = streamId; this.seq = seq; this.deadline = deadline; attempts = 0;
        }
    }

    public Connection(InetSocketAddress remoteAddr, DatagramTransport transport) {
        this.remoteAddr = remoteAddr;
        this.transport = transport;
    }

    public Stream openStream(int streamId, boolean reliable, boolean ordered) {
        if (streams.containsKey(streamId))
            throw new IllegalArgumentException("Stream " + streamId + " already open");
        Stream s = new Stream(streamId, reliable, ordered, this::sendPacket);
        streams.put(streamId, s);
        return s;
    }

    public Stream getStream(int streamId, long timeoutMillis) throws InterruptedException {
        Stream s = streams.get(streamId);
        if (s != null) return s;
        LinkedBlockingQueue<Stream> q = streamWaiters.computeIfAbsent(streamId, k -> new LinkedBlockingQueue<>());
        return q.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public void start() {
        retransmitThread = new Thread(this::retransmitLoop, "mtp-retransmit-" + remoteAddr);
        retransmitThread.setDaemon(true);
        retransmitThread.start();
        ackThread = new Thread(this::ackFlushLoop, "mtp-ack-" + remoteAddr);
        ackThread.setDaemon(true);
        ackThread.start();
    }

    public void close() {
        closed = true;
        if (retransmitThread != null) retransmitThread.interrupt();
        if (ackThread != null) ackThread.interrupt();
        for (var q : streamWaiters.values()) q.clear();
        for (Stream s : streams.values()) {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    // ---- send ----

    private synchronized void sendPacket(MtpPacket pkt) throws Exception {
        Set<Integer> seqs = ackPending.remove(pkt.streamId);
        if (seqs != null && !seqs.isEmpty()) {
            Iterator<Integer> it = seqs.iterator();
            int first = it.next();
            // piggyback one ack
            MtpPacket withAck = new MtpPacket(pkt.streamId, pkt.seq, first, pkt.flags | Flags.ACK, pkt.payload);
            transport.send(remoteAddr, withAck.encode());
            // ack the rest explicitly
            while (it.hasNext()) sendAck(pkt.streamId, it.next());
        } else {
            transport.send(remoteAddr, pkt.encode());
        }

        if (pkt.isReliable() && pkt.payload.length > 0) {
            long key = ((long) pkt.streamId << 32) | (pkt.seq & 0xFFFFFFFFL);
            pending.put(key, new PendingPacket(pkt, pkt.streamId, pkt.seq,
                    System.currentTimeMillis() + RETRANSMIT_TIMEOUT_MS));
        }
    }

    private synchronized void sendAck(int streamId, int ackSeq) {
        MtpPacket pkt = new MtpPacket(streamId, 0, ackSeq, Flags.ACK, new byte[0]);
        transport.send(remoteAddr, pkt.encode());
    }

    // ---- dispatch ----

    public synchronized void packetReceived(MtpPacket pkt) {
        if (Flags.has(pkt.flags, Flags.ACK)) processAck(pkt.streamId, pkt.ack);
        if (Flags.has(pkt.flags, Flags.SYN)) handleSyn(pkt);
        if (pkt.payload.length == 0) return;

        if (pkt.isReliable() && pkt.payload.length > 0) {
            ackPending.computeIfAbsent(pkt.streamId, k -> new HashSet<>()).add(pkt.seq);
        }

        Stream s = streams.get(pkt.streamId);
        if (s != null) {
            s.receivePacket(pkt);
        } else if (pkt.payload.length > 0) {
            s = new Stream(pkt.streamId, pkt.isReliable(), pkt.isOrdered(), this::sendPacket);
            streams.put(pkt.streamId, s);
            s.receivePacket(pkt);
        }
    }

    private void handleSyn(MtpPacket pkt) {
        Stream s = streams.get(pkt.streamId);
        if (s == null) {
            s = new Stream(pkt.streamId, pkt.isReliable(), pkt.isOrdered(), this::sendPacket);
            streams.put(pkt.streamId, s);
        }
        LinkedBlockingQueue<Stream> q = streamWaiters.remove(pkt.streamId);
        if (q != null) q.offer(s);
    }

    private void processAck(int streamId, int ackSeq) {
        long key = ((long) streamId << 32) | (ackSeq & 0xFFFFFFFFL);
        pending.remove(key);
    }

    // ---- retransmit ----

    private void retransmitLoop() {
        while (!closed) {
            try { Thread.sleep(50); } catch (InterruptedException e) { return; }
            long now = System.currentTimeMillis();
            synchronized (this) {
                Iterator<Map.Entry<Long, PendingPacket>> it = pending.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Long, PendingPacket> e = it.next();
                    PendingPacket pp = e.getValue();
                    if (now >= pp.deadline) {
                        if (pp.attempts >= MAX_RETRANSMITS) { it.remove(); continue; }
                        pp.attempts++;
                        pp.deadline = now + (long) (RETRANSMIT_TIMEOUT_MS * Math.pow(1.5, pp.attempts));
                        try { transport.send(remoteAddr, pp.pkt.encode()); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    // ---- ack flush ----

    private void ackFlushLoop() {
        while (!closed) {
            try { Thread.sleep(ACK_DELAY_MS); } catch (InterruptedException e) { return; }
            synchronized (this) {
                for (Map.Entry<Integer, Set<Integer>> e : ackPending.entrySet()) {
                    for (int ackSeq : e.getValue()) {
                        try { sendAck(e.getKey(), ackSeq); } catch (Exception ignored) {}
                    }
                }
                ackPending.clear();
            }
        }
    }

    @Override
    public String toString() { return "Connection(remote=" + remoteAddr + ", streams=" + streams.keySet() + ")"; }
}
