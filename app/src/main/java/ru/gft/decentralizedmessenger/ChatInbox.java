package ru.gft.decentralizedmessenger;

public class ChatInbox {
    private String username;
    private String lastMessage;
    private String timestamp;
    private int unreadCount;

    public ChatInbox(String username, String lastMessage, String timestamp, int unreadCount) {
        this.username = username;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.unreadCount = unreadCount;
    }

    public String getUsername() { return username; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public int getUnreadCount() { return unreadCount; }
}
