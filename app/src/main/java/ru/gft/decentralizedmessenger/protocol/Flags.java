package ru.gft.decentralizedmessenger.protocol;

/** Mirrors api.protocol.packet.Flags (bit field). */
public final class Flags {
    public static final int NONE = 0;
    public static final int SYN  = 1 << 0;
    public static final int FIN  = 1 << 1;
    public static final int ACK  = 1 << 2;
    public static final int RST  = 1 << 3;
    public static final int REL  = 1 << 4;
    public static final int ORD  = 1 << 5;

    private Flags() {}

    public static boolean has(int flags, int f) { return (flags & f) != 0; }
}
