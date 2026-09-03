package ru.gft.decentralizedmessenger;

public class Message {
    private final String text;
    private final long timestamp;
    private final boolean mine;

    public Message(String text, long timestamp, boolean mine) {
        this.text = text;
        this.timestamp = timestamp;
        this.mine = mine;
    }

    public String getText() { return text; }
    public long getTimestamp() { return timestamp; }
    public boolean isMine() { return mine; }
}
