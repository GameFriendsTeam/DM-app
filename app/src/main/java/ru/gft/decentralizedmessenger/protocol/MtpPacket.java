package ru.gft.decentralizedmessenger.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * Mirrors api.protocol.packet.Packet — a 16-byte header binary MTP packet.
 *
 * Header layout (big-endian / network byte order):
 *   1B version | 1B flags | 2B stream_id | 4B seq | 4B ack | 2B length | 2B checksum
 */
public class MtpPacket {
    public static final int HEADER_SIZE = 16;
    public static final int VERSION = 1;
    public static final int MAX_PAYLOAD = 1200;
    private static final byte[] ZERO_CHECKSUM = new byte[]{0, 0};

    public final int streamId;   // uint16
    public final int seq;        // uint32
    public final int ack;        // uint32
    public final int flags;
    public final byte[] payload;

    public MtpPacket(int streamId, int seq, int ack, int flags, byte[] payload) {
        this.streamId = streamId & 0xFFFF;
        this.seq = seq;
        this.ack = ack;
        this.flags = flags;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public boolean isReliable() { return Flags.has(flags, Flags.REL); }
    public boolean isOrdered() { return Flags.has(flags, Flags.ORD); }

    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) VERSION);
        buf.put((byte) flags);
        buf.putShort((short) streamId);
        buf.putInt(seq);
        buf.putInt(ack);
        buf.putShort((short) payload.length);
        buf.putShort((short) 0); // checksum placeholder
        buf.put(payload);
        byte[] data = buf.array();

        // Python computes zlib.crc32 over the FULL header (with the two zero
        // checksum bytes at offset 14-15) + payload. Zero bytes are NOT no-ops
        // in CRC32, so we must include them — otherwise every packet's checksum
        // mismatches and both sides silently drop each other's traffic.
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length); // data[14..16) are still 0 here
        int checksum = (int) (crc.getValue() & 0xFFFF);
        data[14] = (byte) ((checksum >> 8) & 0xFF);
        data[15] = (byte) (checksum & 0xFF);
        return data;
    }

    public static MtpPacket decode(byte[] data) {
        if (data.length < HEADER_SIZE) throw new IllegalArgumentException("Too short: " + data.length);
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int version = buf.get() & 0xFF;
        int flags = buf.get() & 0xFF;
        int streamId = buf.getShort() & 0xFFFF;
        long seq = buf.getInt() & 0xFFFFFFFFL;
        long ack = buf.getInt() & 0xFFFFFFFFL;
        int length = buf.getShort() & 0xFFFF;
        int checksum = buf.getShort() & 0xFFFF;
        if (version != VERSION) throw new IllegalArgumentException("Unknown version: " + version);

        // Verify checksum the same way Python does: zero out bytes 14-15, then
        // CRC the whole buffer (header + payload).
        CRC32 crc = new CRC32();
        crc.update(data, 0, 14);
        crc.update(ZERO_CHECKSUM, 0, 2); // the two zeroed checksum bytes
        crc.update(data, 16, data.length - 16);
        int expected = (int) (crc.getValue() & 0xFFFF);
        if (checksum != expected) throw new IllegalArgumentException("Checksum mismatch");

        if (data.length - HEADER_SIZE < length) throw new IllegalArgumentException("Truncated payload");
        byte[] payload = new byte[length];
        System.arraycopy(data, HEADER_SIZE, payload, 0, length);
        return new MtpPacket(streamId, (int) seq, (int) ack, flags, payload);
    }

    @Override
    public String toString() {
        return "Packet(stream=" + streamId + ", seq=" + seq + ", ack=" + ack
                + ", flags=" + flags + ", payload=" + payload.length + "B)";
    }
}
