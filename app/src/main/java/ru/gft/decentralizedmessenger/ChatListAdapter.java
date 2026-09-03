package ru.gft.decentralizedmessenger;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    private List<ChatInbox> chatList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(ChatInbox chat);
    }

    public ChatListAdapter(List<ChatInbox> chatList, OnItemClickListener listener) {
        this.chatList = chatList;
        this.listener = listener;
    }

    public void setChats(List<ChatInbox> chats) {
        chatList.clear();
        chatList.addAll(chats);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_user, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatInbox chat = chatList.get(position);
        holder.textUserName.setText(chat.getUsername());
        holder.textLastMessage.setText(chat.getLastMessage());
        holder.textTimestamp.setText(chat.getTimestamp());

        if (chat.getUnreadCount() > 0) {
            holder.textUnreadCount.setVisibility(View.VISIBLE);
            holder.textUnreadCount.setText(String.valueOf(chat.getUnreadCount()));
        } else {
            holder.textUnreadCount.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(chat));
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView textUserName, textLastMessage, textTimestamp, textUnreadCount;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            textUserName = itemView.findViewById(R.id.textUserName);
            textLastMessage = itemView.findViewById(R.id.textLastMessage);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
            textUnreadCount = itemView.findViewById(R.id.textUnreadCount);
        }
    }
}
