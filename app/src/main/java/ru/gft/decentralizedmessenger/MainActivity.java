package ru.gft.decentralizedmessenger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import ru.gft.decentralizedmessenger.util.ChatManager;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatListAdapter adapter;
    private Toolbar toolbarC;
    private Toolbar toolbar;
    private FloatingActionButton fabNewChat;
    private ChatManager chatManager;
    private final List<ChatInbox> chats = new ArrayList<>();
    private FileManager.FilePickerCallback fileCallback;

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && fileCallback != null) {
                    // Передаем URI в интерфейс
                    fileCallback.onFilePicked(uri);
                }
            }
    );

    public final FileManager fm = new FileManager(this.filePickerLauncher);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fileCallback = uri -> {
            Toast.makeText(this, "Файл получен: " + uri.getPath(), Toast.LENGTH_SHORT).show();
        };

        toolbarC = findViewById(R.id.toolbarChatList);
        recyclerView = findViewById(R.id.recyclerViewChatList);
        fabNewChat = findViewById(R.id.fabNewChat);

        setSupportActionBar(toolbarC);
        chatManager = new ChatManager(this);
        setupRecyclerView();

        fabNewChat.setOnClickListener(v -> showNewChatDialog());

        SharedPreferences sharedPreferences = getSharedPreferences("user.json", MODE_PRIVATE);
        if (sharedPreferences.getString("username", "").isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshChats();
    }

    private void setupRecyclerView() {
        adapter = new ChatListAdapter(chats, chat -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_USERNAME, chat.getUsername());
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
    }

    private void refreshChats() {
        chats.clear();
        chats.addAll(chatManager.getChatList());
        adapter.notifyDataSetChanged();
    }

    private void showNewChatDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.hint_username);
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_chat)
                .setView(input)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) return;
                    chatManager.ensureChat(name);
                    refreshChats();
                    openChat(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openChat(String username) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_USERNAME, username);
        startActivity(intent);
    }
}
