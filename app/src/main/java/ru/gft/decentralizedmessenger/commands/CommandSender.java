package ru.gft.decentralizedmessenger.commands;

import ru.gft.decentralizedmessenger.crypto.SecureEncryption;
import ru.gft.decentralizedmessenger.packet.Packet;

/** Mirrors api.commands.CommandSender.CommandSender. */
public interface CommandSender {
    Object[] read(int timeoutSeconds);
    void send(Packet packet, boolean encrypt);
    void stop();
    void transmit(Packet packet, boolean encrypt);
    SecureEncryption getEncript(String to);
    boolean[] checkConnection(int timeoutSeconds);
    boolean connectionIsSecure();
    void sendKey(String to);
    void readKey(String sender, int timeoutSeconds);
    Object[] waitPacket(String type, double timeoutSeconds);
    Packet getPacket(String type);
    String getUsername();
    ClientContext getContext();
}
