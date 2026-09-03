package ru.gft.decentralizedmessenger;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import ru.gft.decentralizedmessenger.util.ChatManager;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME = "extra_username";

    private Toolbar toolbar;
    private RecyclerView recyclerMessages;
    private TextInputEditText editMessage;
    private MaterialButton btnSend;

    private MessageAdapter adapter;
    private ChatManager chatManager;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, R.string.error_no_chat, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        toolbar = findViewById(R.id.toolbarChat);
        recyclerMessages = findViewById(R.id.recyclerMessages);
        editMessage = findViewById(R.id.editMessage);
        btnSend = findViewById(R.id.btnSend);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(username);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        chatManager = new ChatManager(this);
        chatManager.ensureChat(username);
        chatManager.markRead(username);

        adapter = new MessageAdapter();
        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setAdapter(adapter);

        loadMessages();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void loadMessages() {
        List<Message> messages = chatManager.getMessages(username);
        adapter.setMessages(messages);
        if (!messages.isEmpty()) {
            recyclerMessages.scrollToPosition(messages.size() - 1);
        }
    }

    private void sendMessage() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) return;

        chatManager.addMessage(username, text, true);
        Message message = new Message(text, System.currentTimeMillis(), true);
        adapter.addMessage(message);
        recyclerMessages.scrollToPosition(adapter.getItemCount() - 1);
        editMessage.setText("");
    }
}
