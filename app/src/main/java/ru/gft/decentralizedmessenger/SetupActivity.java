package ru.gft.decentralizedmessenger;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SetupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_setup);
        Button applyButton = findViewById(R.id.apply);
        applyButton.setOnClickListener(v -> {
            Apply();
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    public void Apply() {
        // Инициализация (контекст текущей Activity)
        SharedPreferences sharedPreferences = getSharedPreferences("user.json", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        android.widget.EditText username = findViewById(R.id.username);
        android.widget.EditText addr = findViewById(R.id.addr);
        editor.putString("username", username.getText().toString());
        editor.putString("addr", addr.getText().toString());

        editor.apply();
        finish();
    }
}