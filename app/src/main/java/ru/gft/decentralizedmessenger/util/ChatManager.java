package ru.gft.decentralizedmessenger.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ru.gft.decentralizedmessenger.ChatInbox;
import ru.gft.decentralizedmessenger.Message;

public class ChatManager {
    private final Context ctx;

    public ChatManager(Context ctx) {
        this.ctx = ctx;
    }

    public JSONObject getChats() {
        return DataHelper.readJson(ctx, DataHelper.FILE_CHATS);
    }

    public List<ChatInbox> getChatList() {
        List<ChatInbox> list = new ArrayList<>();
        JSONObject chats = getChats();
        try {
            for (Iterator<String> it = chats.keys(); it.hasNext(); ) {
                JSONArray chat = chats.getJSONArray(it.next());
                list.add(new ChatInbox(
                        chat.getString(0),
                        chat.getString(1),
                        chat.getString(2),
                        chat.getInt(3)
                ));
            }
        } catch (JSONException e) {
            // ignore malformed entries
        }
        return list;
    }

    public boolean chatExists(String username) {
        return getChats().has(username);
    }

    public void ensureChat(String username) {
        if (chatExists(username)) return;
        JSONObject chats = getChats();
        JSONArray entry = new JSONArray();
        entry.put(username);
        entry.put("");
        entry.put("");
        entry.put(0);
        try {
            chats.put(username, entry);
            DataHelper.saveJson(ctx, chats, DataHelper.FILE_CHATS);
        } catch (JSONException e) {
            // ignore
        }
    }

    public List<Message> getMessages(String username) {
        List<Message> list = new ArrayList<>();
        JSONObject all = DataHelper.readJson(ctx, DataHelper.FILE_MESSAGES);
        if (!all.has(username)) return list;
        try {
            JSONArray arr = all.getJSONArray(username);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.getJSONObject(i);
                list.add(new Message(
                        m.getString("text"),
                        m.getLong("timestamp"),
                        m.getBoolean("mine")
                ));
            }
        } catch (JSONException e) {
            // ignore
        }
        return list;
    }

    public void addMessage(String username, String text, boolean mine) {
        ensureChat(username);
        JSONObject all = DataHelper.readJson(ctx, DataHelper.FILE_MESSAGES);
        JSONArray arr = all.optJSONArray(username);
        if (arr == null) arr = new JSONArray();
        JSONObject msg = new JSONObject();
        long now = System.currentTimeMillis();
        try {
            msg.put("text", text);
            msg.put("timestamp", now);
            msg.put("mine", mine);
            arr.put(msg);
            all.put(username, arr);
            DataHelper.saveJson(ctx, all, DataHelper.FILE_MESSAGES);
        } catch (JSONException e) {
            return;
        }
        updateChatSummary(username, text, now, mine ? 0 : 1);
    }

    private void updateChatSummary(String username, String lastMessage, long timestamp, int unreadDelta) {
        JSONObject chats = getChats();
        try {
            JSONArray entry = chats.getJSONArray(username);
            entry.put(1, lastMessage);
            entry.put(2, formatTime(timestamp));
            int unread = entry.getInt(3) + unreadDelta;
            if (unread < 0) unread = 0;
            entry.put(3, unread);
            chats.put(username, entry);
            DataHelper.saveJson(ctx, chats, DataHelper.FILE_CHATS);
        } catch (JSONException e) {
            // ignore
        }
    }

    public void markRead(String username) {
        JSONObject chats = getChats();
        try {
            JSONArray entry = chats.getJSONArray(username);
            entry.put(3, 0);
            chats.put(username, entry);
            DataHelper.saveJson(ctx, chats, DataHelper.FILE_CHATS);
        } catch (JSONException e) {
            // ignore
        }
    }

    private String formatTime(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
}
