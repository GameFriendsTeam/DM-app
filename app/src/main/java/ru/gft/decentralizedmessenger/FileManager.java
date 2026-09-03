package ru.gft.decentralizedmessenger;

import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;

public class FileManager {
    private ActivityResultLauncher<String> fpl;
    public FileManager(ActivityResultLauncher<String>  filePickerLauncher) {
        this.fpl = filePickerLauncher;
    }

    public void pickFile() {
        fpl.launch("*/*");
    }
    public interface FilePickerCallback {
        void onFilePicked(Uri uri);
    }
}
