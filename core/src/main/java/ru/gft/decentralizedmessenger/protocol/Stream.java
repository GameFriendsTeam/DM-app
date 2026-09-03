package ru.gft.decentralizedmessenger.protocol;

import java.util.LinkedHashSet;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors api.protocol.stream.Stream — one logical channel inside a Connection.
 * Reliable/ordered delivery with a reorder buffer and a blocking delivery queue.
 */
public class Stream {
    public enum State { OPEN, CLOSING, CLOSED }

    public final int streamId;
    public final boolean reliable;
    public final boolean ordered;

    private final PacketSender sendFn;
    private volatile State state = State.OPEN;

    private int sendSeq = 0;
    private int recvSeq = 0;
    private final TreeMap<Integer, byte[]> reorderBuf = new TreeMap<>();
    private final LinkedHashSet<Integer> seenSeqs = new LinkedHashSet<>();

    private final BlockingQueue<byte[]> recvQueue = new LinkedBlockingQueue<>();

    @FunctionalInterface
    public interface PacketSender { void send(MtpPacket pkt) throws Exception; }

    public Stream(int streamId, boolean reliable, boolean ordered, PacketSender sendFn) {
        this.streamId = streamId;
        this.reliable = reliable;
        this.ordered = ordered;
        this.sendFn = sendFn;
    }

    public State getState() { return state; }

    /** Announce this stream to the remote side with a SYN packet. */
    public synchronized void sync() throws Exception {
        if (state != State.OPEN) throw new RuntimeException("Stream is not open");
        int flags = Flags.SYN;
        if (reliable) flags |= Flags.REL;
        sendFn.send(new MtpPacket(streamId, sendSeq, 0, flags, new byte[0]));
    }

    public synchronized void send(byte[] data) throws Exception {
        if (state != State.OPEN) throw new RuntimeException("Stream is not open");
        int flags = Flags.NONE;
        if (reliable) flags |= Flags.REL;
        if (ordered) flags |= Flags.ORD;
        sendFn.send(new MtpPacket(streamId, sendSeq, 0, flags, data));
        sendSeq = (sendSeq + 1) & 0xFFFFFFFF;
    }

    /** Called by Connection when a packet arrives for this stream. */
    public synchronized void receivePacket(MtpPacket pkt) {
        if (state == State.CLOSED) return;
        if (Flags.has(pkt.flags, Flags.FIN)) { state = State.CLOSED; return; }
        if (Flags.has(pkt.flags, Flags.SYN) && pkt.payload.length == 0) return;

        byte[] payload = pkt.payload;
        if (payload.length == 0) return;

        int seq = pkt.seq;

        if (!ordered) {
            if (reliable) {
                if (seenSeqs.contains(seq)) return;
                seenSeqs.add(seq);
                if (seenSeqs.size() > 1024) seenSeqs.remove(seenSeqs.iterator().next());
            }
            recvQueue.offer(payload);
            return;
        }

        if (!reliable) {
            if (seqGt(seq, recvSeq) || seq == recvSeq) {
                recvSeq = (seq + 1) & 0xFFFFFFFF;
                recvQueue.offer(payload);
            }
            return;
        }

        // reliable + ordered
        if (seq == recvSeq) {
            recvQueue.offer(payload);
            recvSeq = (recvSeq + 1) & 0xFFFFFFFF;
            flushReorderBuf();
        } else if (seqGt(seq, recvSeq)) {
            reorderBuf.put(seq, payload);
        }
    }

    private void flushReorderBuf() {
        while (reorderBuf.containsKey(recvSeq)) {
            recvQueue.offer(reorderBuf.remove(recvSeq));
            recvSeq = (recvSeq + 1) & 0xFFFFFFFF;
        }
    }

    public byte[] recv() throws InterruptedException {
        byte[] data = recvQueue.take();
        if (state == State.CLOSED && data == null) throw new RuntimeException("Stream closed");
        return data;
    }

    public byte[] recv(long timeoutMillis) throws InterruptedException {
        return recvQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public byte[] recvNowait() { return recvQueue.poll(); }

    public synchronized void close() throws Exception {
        if (state != State.OPEN) return;
        state = State.CLOSING;
        int flags = Flags.FIN;
        if (reliable) flags |= Flags.REL;
        sendFn.send(new MtpPacket(streamId, sendSeq, 0, flags, new byte[0]));
    }

    /** Sequence comparison with wrap-around (RFC 1982 style). a > b? */
    private static boolean seqGt(int a, int b) {
        if (a == b) return false;
        return Integer.compareUnsigned(a, b) > 0;
    }

    @Override
    public String toString() {
        String mode = (reliable ? "reliable" : "") + (ordered ? (reliable ? "|" : "") + "ordered" : "");
        if (mode.isEmpty()) mode = "unreliable+unordered";
        return "Stream(id=" + streamId + ", mode=" + mode + ", state=" + state + ")";
    }
}
