package ru.gft.decentralizedmessenger.util;

import android.content.Context;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class DataHelper {
    public static final String FILE_CHATS = "chats.json";
    public static final String FILE_MESSAGES = "messages.json";

    public static void saveJson(Context context, JSONObject json, String filename) {
        File file = new File(context.getFilesDir(), filename);
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json.toString());
        } catch (IOException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static JSONObject readJson(Context context, String filename) {
        File file = new File(context.getFilesDir(), filename);
        if (!file.exists()) {
            return new JSONObject();
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            if (sb.length() == 0) {
                return new JSONObject();
            }
            return new JSONObject(sb.toString());
        } catch (IOException | JSONException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return new JSONObject();
    }

    public static boolean isExists(Context context, String filename) {
        return new File(context.getFilesDir(), filename).exists();
    }
}
