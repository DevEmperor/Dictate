package net.devemperor.dictate.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.onboarding.OnboardingActivity;
import net.devemperor.dictate.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class DictateSettingsActivity extends AppCompatActivity {

    ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictate_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.dictate_settings, new PreferencesFragment())
                .commit();

        SharedPreferences sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);

        // start onboarding if this is the first time for the user to open Dictate
        if (!sp.getBoolean("net.devemperor.dictate.onboarding_complete", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();

        // open file picker if user wants to transcribe a file
        } else if (getIntent().getBooleanExtra("net.devemperor.dictate.open_file_picker", false)) {
            filePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        if (result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri == null) return;

                            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                            if (cursor == null) return;
                            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

                            String fileName = "";
                            long fileSize = 0;
                            if (cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex);
                                fileSize = cursor.getLong(sizeIndex);
                            }
                            cursor.close();

                            // check if fileSize is larger than 25MB
                            if (fileSize > 25 * 1024 * 1024) {
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.dictate_file_too_large_title)
                                        .setMessage(R.string.dictate_file_too_large_message)
                                        .setPositiveButton(R.string.dictate_okay, null)
                                        .show();
                                return;
                            }

                            // copy the inputFileUri file to app cache directory
                            Toast.makeText(this, getString(R.string.dictate_file_copying_to_cache), Toast.LENGTH_SHORT).show();
                            try {
                                InputStream inputStream = getContentResolver().openInputStream(uri);
                                FileOutputStream outputStream = new FileOutputStream(new File(getCacheDir(), fileName));
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                if (inputStream != null) {
                                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                                        outputStream.write(buffer, 0, bytesRead);
                                    }
                                    outputStream.close();
                                    inputStream.close();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            sp.edit().putString("net.devemperor.dictate.transcription_audio_file", fileName).apply();
                        }
                    }
                    finish();  // close the activity after the file has been picked
                }
            );

            // let the user choose an audio file used for transcription
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/mpeg", "audio/mp4", "audio/wav", "video/mp4", "video/mpeg", "video/webm"});
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.dictate_choose_audio_file)));

        } else if (sp.getInt("net.devemperor.dictate.last_version_code", 0) < BuildConfig.VERSION_CODE) {

            // show changelog if user has a new version
            String whatsNewMessage = getString(R.string.dictate_changelog_donate);
            int lastVersionCode = sp.getInt("net.devemperor.dictate.last_version_code", 0);

            if (lastVersionCode < 18) whatsNewMessage += getString(R.string.dictate_changelog_18);
            if (lastVersionCode < 17) whatsNewMessage += getString(R.string.dictate_changelog_17);
            if (lastVersionCode < 16) whatsNewMessage += getString(R.string.dictate_changelog_16);
            if (lastVersionCode < 15) whatsNewMessage += getString(R.string.dictate_changelog_15);
            if (lastVersionCode < 14) whatsNewMessage += getString(R.string.dictate_changelog_14);
            if (lastVersionCode < 13) whatsNewMessage += getString(R.string.dictate_changelog_13);
            if (lastVersionCode < 12) whatsNewMessage += getString(R.string.dictate_changelog_12);
            if (lastVersionCode < 11) whatsNewMessage += getString(R.string.dictate_changelog_11);
            if (lastVersionCode < 10) whatsNewMessage += getString(R.string.dictate_changelog_10);
            if (lastVersionCode < 9) whatsNewMessage += getString(R.string.dictate_changelog_9);
            if (lastVersionCode < 8) whatsNewMessage += getString(R.string.dictate_changelog_8);
            if (lastVersionCode < 7) whatsNewMessage += getString(R.string.dictate_changelog_7);
            if (lastVersionCode < 6) whatsNewMessage += getString(R.string.dictate_changelog_6);
            if (lastVersionCode < 5) whatsNewMessage += getString(R.string.dictate_changelog_5);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dictate_whats_new)
                    .setMessage(whatsNewMessage)
                    .setPositiveButton(R.string.dictate_okay, (di, i) -> sp.edit().putInt("net.devemperor.dictate.last_version_code", BuildConfig.VERSION_CODE).apply())
                    .show();

        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, 1337);

        } else {
            // check if keyboard is still enabled
            List<InputMethodInfo> inputMethodsList = ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).getEnabledInputMethodList();
            boolean keyboardEnabled = false;
            for (InputMethodInfo inputMethod : inputMethodsList) {
                if (inputMethod.getPackageName().equals(getPackageName())) {
                    keyboardEnabled = true;
                    break;
                }
            }
            if (!keyboardEnabled) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dictate_enable_keyboard_title)
                        .setMessage(R.string.dictate_enable_keyboard_message)
                        .setPositiveButton(R.string.dictate_yes, (dialog, which) -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)))
                        .setNegativeButton(R.string.dictate_no, null)
                        .show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1337) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.dictate_microphone_permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.dictate_microphone_permission_denied, Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    }
}