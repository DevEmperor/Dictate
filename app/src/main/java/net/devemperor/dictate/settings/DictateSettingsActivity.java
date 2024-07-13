package net.devemperor.dictate.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.onboarding.OnboardingActivity;
import net.devemperor.dictate.R;

public class DictateSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictate_settings);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.dictate_settings, new PreferencesFragment())
                .commit();

        SharedPreferences sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);

        if (!sp.getBoolean("net.devemperor.dictate.onboarding_complete", false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
        } else if (sp.getInt("net.devemperor.dictate.last_version_code", 0) < BuildConfig.VERSION_CODE) {
            String whatsNewMessage = getString(R.string.dictate_changelog_donate);
            int lastVersionCode = sp.getInt("net.devemperor.dictate.last_version_code", 0);
            if (lastVersionCode < 6) whatsNewMessage += getString(R.string.dictate_changelog_6);
            if (lastVersionCode < 5) whatsNewMessage += getString(R.string.dictate_changelog_5);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.dictate_whats_new)
                    .setMessage(whatsNewMessage)
                    .setPositiveButton(R.string.dictate_okay, (di, i) -> sp.edit().putInt("net.devemperor.dictate.last_version_code", BuildConfig.VERSION_CODE).apply())
                    .show();
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ android.Manifest.permission.RECORD_AUDIO }, 1337);
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