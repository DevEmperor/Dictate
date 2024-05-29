package net.devemperor.dictate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PreferencesFragment extends PreferenceFragmentCompat {

    SharedPreferences sp;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("net.devemperor.dictate");
        setPreferencesFromResource(R.xml.fragment_preferences, null);
        sp = getPreferenceManager().getSharedPreferences();

        EditTextPreference apiKeyPreference = findPreference("net.devemperor.dictate.api_key");
        if (apiKeyPreference != null) {
            apiKeyPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String key = preference.getText();
                if (TextUtils.isEmpty(key)) return getString(R.string.dictate_no_api_key);
                if (key.length() <= 10) return key;
                return key.substring(0, 8) + "..." + key.substring(key.length() - 8);
            });

            apiKeyPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                editText.setSingleLine(true);
            });
        }

        Preference usage = findPreference("net.devemperor.dictate.usage");
        if (usage != null) {
            float duration = sp.getFloat("net.devemperor.dictate.total_duration", 0f);
            usage.setTitle(getString(R.string.dictate_settings_usage, (int) (duration / 60), (int) (duration % 60), duration * 0.0001f));
            usage.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dictate_settings_reset_usage_title)
                        .setMessage(R.string.dictate_settings_reset_usage_message)
                        .setPositiveButton(R.string.dictate_yes, (dialog, which) -> {
                            sp.edit().putFloat("net.devemperor.dictate.total_duration", 0f).apply();
                            usage.setTitle(getString(R.string.dictate_settings_usage, 0, 0, 0f));
                        })
                        .setNegativeButton(R.string.dictate_no, null)
                        .show();
                return true;
            });
        }

        Preference feedback = findPreference("net.devemperor.dictate.feedback");
        if (feedback != null) {
            feedback.setOnPreferenceClickListener(preference -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:contact@devemperor.net"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Dictate Feedback");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "Write your feedback here");
                startActivity(Intent.createChooser(emailIntent, "Choose an email client:"));
                return true;
            });
        }
    }
}
