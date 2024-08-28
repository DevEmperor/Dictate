package net.devemperor.dictate.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.R;
import net.devemperor.dictate.rewording.PromptsOverviewActivity;
import net.devemperor.dictate.usage.UsageActivity;
import net.devemperor.dictate.usage.UsageDatabaseHelper;

import org.apache.commons.validator.routines.UrlValidator;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class PreferencesFragment extends PreferenceFragmentCompat {

    SharedPreferences sp;
    UsageDatabaseHelper usageDatabaseHelper;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("net.devemperor.dictate");
        setPreferencesFromResource(R.xml.fragment_preferences, null);
        sp = getPreferenceManager().getSharedPreferences();
        usageDatabaseHelper = new UsageDatabaseHelper(requireContext());

        Preference editPrompts = findPreference("net.devemperor.dictate.edit_custom_rewording_prompts");
        if (editPrompts != null) {
            editPrompts.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), PromptsOverviewActivity.class));
                return true;
            });
        }

        EditTextPreference overlayCharacters = findPreference("net.devemperor.dictate.overlay_characters");
        if (overlayCharacters != null) {
            overlayCharacters.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String text = preference.getText();
                if (TextUtils.isEmpty(text)) {
                    return getString(R.string.dictate_default_overlay_characters);
                }
                return text.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining(" "));
            });

            overlayCharacters.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                editText.setSingleLine(true);
                editText.setHint(R.string.dictate_default_overlay_characters);
                editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(8)});
                editText.setSelection(editText.getText().length());
            });

            overlayCharacters.setOnPreferenceChangeListener((preference, newValue) -> {
                String text = (String) newValue;
                if (text.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.dictate_overlay_characters_empty, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
        }

        SwitchPreference instantOutput = findPreference("net.devemperor.dictate.instant_output");
        SeekBarPreference outputSpeed = findPreference("net.devemperor.dictate.output_speed");
        if (instantOutput != null && outputSpeed != null) {
            instantOutput.setOnPreferenceChangeListener((preference, newValue) -> {
                outputSpeed.setEnabled(!(Boolean) newValue);
                return true;
            });
            outputSpeed.setEnabled(!instantOutput.isChecked());
        }

        Preference usage = findPreference("net.devemperor.dictate.usage");
        if (usage != null) {
            usage.setSummary(getString(R.string.dictate_usage_total_cost, usageDatabaseHelper.getTotalCost()));

            usage.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), UsageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            });
        }

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
                editText.setHint(R.string.dictate_api_key_hint);
            });
        }

        Preference promptPreference = findPreference("net.devemperor.dictate.prompt");
        if (promptPreference != null) {
            promptPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), StylePromptActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            });
        }

        EditTextPreference customHostPreference = findPreference("net.devemperor.dictate.custom_api_host");
        if (customHostPreference != null) {
            customHostPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String host = preference.getText();
                if (TextUtils.isEmpty(host)) return getString(R.string.dictate_custom_host_hint);
                return host;
            });

            customHostPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
                editText.setSingleLine(true);
                editText.setHint(R.string.dictate_custom_host_hint);
            });

            customHostPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String host = (String) newValue;
                if (new UrlValidator().isValid(host) && host.endsWith("/")) return true;
                else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.dictate_custom_host_invalid_title)
                            .setMessage(R.string.dictate_custom_host_invalid_message)
                            .setPositiveButton(R.string.dictate_okay, null)
                            .show();
                    return false;
                }
            });
        }

        Preference cache = findPreference("net.devemperor.dictate.cache");
        File[] cacheFiles = requireContext().getCacheDir().listFiles();
        if (cache != null) {
            if (cacheFiles != null) {
                long cacheSize = Arrays.stream(cacheFiles).mapToLong(File::length).sum();
                cache.setTitle(getString(R.string.dictate_settings_cache, cacheFiles.length, cacheSize / 1024f / 1024f));
            }

            cache.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dictate_cache_clear_title)
                        .setMessage(R.string.dictate_cache_clear_message)
                        .setPositiveButton(R.string.dictate_yes, (dialog, which) -> {
                            if (cacheFiles != null) {
                                for (File file : cacheFiles) {
                                    file.delete();
                                }
                            }
                            cache.setTitle(getString(R.string.dictate_settings_cache, 0, 0f));
                            Toast.makeText(requireContext(), R.string.dictate_cache_cleared, Toast.LENGTH_SHORT).show();
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
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.dictate_feedback_subject));
                emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.dictate_feedback_body)
                        + "\n\nDictate User-ID: " + sp.getString("net.devemperor.dictate.user_id", "null"));
                startActivity(Intent.createChooser(emailIntent, getString(R.string.dictate_feedback_title)));
                return true;
            });
        }

        Preference donate = findPreference("net.devemperor.dictate.donate");
        if (donate != null) {
            donate.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/DevEmperor"));
                startActivity(browserIntent);
                return true;
            });
        }

        Preference about = findPreference("net.devemperor.dictate.about");
        if (about != null) {
            about.setTitle(getString(R.string.dictate_about, BuildConfig.VERSION_NAME));
            about.setOnPreferenceClickListener(preference -> {
                Toast.makeText(requireContext(), "User-ID: " + sp.getString("net.devemperor.dictate.user_id", "null"), Toast.LENGTH_LONG).show();
                return true;
            });
        }
    }
}
