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
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.DictateUtils;
import net.devemperor.dictate.R;
import net.devemperor.dictate.rewording.PromptsOverviewActivity;
import net.devemperor.dictate.usage.UsageActivity;
import net.devemperor.dictate.usage.UsageDatabaseHelper;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
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

        Preference editPromptsPreference = findPreference("net.devemperor.dictate.edit_custom_rewording_prompts");
        if (editPromptsPreference != null) {
            editPromptsPreference.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), PromptsOverviewActivity.class));
                return true;
            });
        }

        MultiSelectListPreference inputLanguagesPreference = findPreference("net.devemperor.dictate.input_languages");
        if (inputLanguagesPreference != null) {
            inputLanguagesPreference.setSummaryProvider((Preference.SummaryProvider<MultiSelectListPreference>) preference -> {
                String[] selectedLanguagesValues = preference.getValues().toArray(new String[0]);
                return Arrays.stream(selectedLanguagesValues).map(DictateUtils::translateLanguageToEmoji).collect(Collectors.joining(" "));
            });

            inputLanguagesPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Set<String> selectedLanguages = (Set<String>) newValue;
                if (selectedLanguages.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.dictate_input_languages_empty, Toast.LENGTH_SHORT).show();
                    return false;
                }
                sp.edit().putInt("net.devemperor.dictate.input_language_pos", 0).apply();
                return true;
            });
        }

        EditTextPreference overlayCharactersPreference = findPreference("net.devemperor.dictate.overlay_characters");
        if (overlayCharactersPreference != null) {
            overlayCharactersPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String text = preference.getText();
                if (TextUtils.isEmpty(text)) {
                    return getString(R.string.dictate_default_overlay_characters);
                }
                return text.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining(" "));
            });

            overlayCharactersPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                editText.setSingleLine(true);
                editText.setHint(R.string.dictate_default_overlay_characters);
                editText.setFilters(new InputFilter[] {new InputFilter.LengthFilter(8)});
                editText.setSelection(editText.getText().length());
            });

            overlayCharactersPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String text = (String) newValue;
                if (text.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.dictate_overlay_characters_empty, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });
        }

        SwitchPreference instantOutputPreference = findPreference("net.devemperor.dictate.instant_output");
        SeekBarPreference outputSpeedPreference = findPreference("net.devemperor.dictate.output_speed");
        if (instantOutputPreference != null && outputSpeedPreference != null) {
            instantOutputPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                outputSpeedPreference.setEnabled(!(Boolean) newValue);
                return true;
            });
            outputSpeedPreference.setEnabled(!instantOutputPreference.isChecked());
        }

        Preference usagePreference = findPreference("net.devemperor.dictate.usage");
        if (usagePreference != null) {
            usagePreference.setSummary(getString(R.string.dictate_usage_total_cost, usageDatabaseHelper.getTotalCost()));

            usagePreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), UsageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            });
        }

        Preference apiSettingsPreference = findPreference("net.devemperor.dictate.api_settings");
        if (apiSettingsPreference != null) {
            apiSettingsPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), APISettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
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

        EditTextPreference proxyHostPreference = findPreference("net.devemperor.dictate.proxy_host");
        if (proxyHostPreference != null) {
            proxyHostPreference.setSummaryProvider((Preference.SummaryProvider<EditTextPreference>) preference -> {
                String host = preference.getText();
                if (TextUtils.isEmpty(host)) return getString(R.string.dictate_settings_proxy_hint);
                return host;
            });

            proxyHostPreference.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_URI);
                editText.setSingleLine(true);
                editText.setHint(R.string.dictate_settings_proxy_hint);
            });

            proxyHostPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String host = (String) newValue;
                if (DictateUtils.isValidProxy(host)) return true;
                else {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.dictate_proxy_invalid_title)
                            .setMessage(R.string.dictate_proxy_invalid_message)
                            .setPositiveButton(R.string.dictate_okay, null)
                            .show();
                    return false;
                }
            });
        }

        Preference howToPreference = findPreference("net.devemperor.dictate.how_to");
        if (howToPreference != null) {
            howToPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), HowToActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            });
        }

        Preference cachePreference = findPreference("net.devemperor.dictate.cache");
        File[] cacheFiles = requireContext().getCacheDir().listFiles();
        if (cachePreference != null) {
            if (cacheFiles != null) {
                long cacheSize = Arrays.stream(cacheFiles).mapToLong(File::length).sum();
                cachePreference.setTitle(getString(R.string.dictate_settings_cache, cacheFiles.length, cacheSize / 1024f / 1024f));
            }

            cachePreference.setOnPreferenceClickListener(preference -> {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.dictate_cache_clear_title)
                        .setMessage(R.string.dictate_cache_clear_message)
                        .setPositiveButton(R.string.dictate_yes, (dialog, which) -> {
                            if (cacheFiles != null) {
                                for (File file : cacheFiles) {
                                    file.delete();
                                }
                            }
                            cachePreference.setTitle(getString(R.string.dictate_settings_cache, 0, 0f));
                            Toast.makeText(requireContext(), R.string.dictate_cache_cleared, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.dictate_no, null)
                        .show();
                return true;
            });
        }

        Preference feedbackPreference = findPreference("net.devemperor.dictate.feedback");
        if (feedbackPreference != null) {
            feedbackPreference.setOnPreferenceClickListener(preference -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto:contact@devemperor.net"));
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.dictate_feedback_subject));
                emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.dictate_feedback_body)
                        + "\n\nDictate User-ID: " + sp.getString("net.devemperor.dictate.user_id", "null"));
                startActivity(Intent.createChooser(emailIntent, getString(R.string.dictate_feedback_title)));
                return true;
            });
        }

        Preference githubPreference = findPreference("net.devemperor.dictate.github");
        if (githubPreference != null) {
            githubPreference.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DevEmperor/Dictate"));
                startActivity(browserIntent);
                return true;
            });
        }

        Preference donatePreference = findPreference("net.devemperor.dictate.donate");
        if (donatePreference != null) {
            donatePreference.setOnPreferenceClickListener(preference -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/DevEmperor"));
                startActivity(browserIntent);
                return true;
            });
        }

        Preference aboutPreference = findPreference("net.devemperor.dictate.about");
        if (aboutPreference != null) {
            aboutPreference.setTitle(getString(R.string.dictate_about, BuildConfig.VERSION_NAME));
            aboutPreference.setOnPreferenceClickListener(preference -> {
                Toast.makeText(requireContext(), "User-ID: " + sp.getString("net.devemperor.dictate.user_id", "null"), Toast.LENGTH_LONG).show();
                return true;
            });
        }
    }
}
