package net.devemperor.dictate.onboarding;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.R;
import net.devemperor.dictate.settings.DictateSettingsActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;


public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

    Activity activity;
    int[] layoutIds;

    public OnboardingAdapter(Activity activity, int[] layoutIds) {
        this.activity = activity;
        this.layoutIds = layoutIds;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutIds[viewType], parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position == 1) {
            TextView microphoneStatusTv = holder.itemView.findViewById(R.id.onboarding_permissions_microphone_status_tv);
            TextView keyboardStatusTv = holder.itemView.findViewById(R.id.onboarding_permissions_keyboard_status_tv);
            Button microphoneBtn = holder.itemView.findViewById(R.id.onboarding_permissions_microphone_btn);
            Button keyboardBtn = holder.itemView.findViewById(R.id.onboarding_permissions_keyboard_btn);

            if (activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                microphoneStatusTv.setText(activity.getString(R.string.dictate_microphone_permission_granted));
                microphoneBtn.setEnabled(false);
            }

            microphoneBtn.setOnClickListener(v -> activity.requestPermissions(new String[]{ android.Manifest.permission.RECORD_AUDIO }, 1337));

            List<InputMethodInfo> inputMethodsList = ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).getEnabledInputMethodList();
            for (InputMethodInfo inputMethod : inputMethodsList) {
                if (inputMethod.getPackageName().equals(activity.getPackageName())) {
                    keyboardStatusTv.setText(activity.getString(R.string.dictate_keyboard_enabled));
                    keyboardBtn.setEnabled(false);
                }
            }

            keyboardBtn.setOnClickListener(v -> activity.startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)));
        } else if (position == 2) {
            TextView requestApiKeyTv = holder.itemView.findViewById(R.id.onboarding_api_key_request_tv);
            EditText apiKeyEt = holder.itemView.findViewById(R.id.onboarding_api_key_et);
            Button finishBtn = holder.itemView.findViewById(R.id.onboarding_api_key_finish_btn);

            StringBuilder stringBuilder = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        activity.getAssets().open("dictate_api_key_info_" + (Locale.getDefault().getLanguage().equals("de") ? "de" : "en") + ".html")));
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                    stringBuilder.append("\n");
                }
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            requestApiKeyTv.setMovementMethod(LinkMovementMethod.getInstance());
            requestApiKeyTv.setText(Html.fromHtml(stringBuilder.toString(), Html.FROM_HTML_MODE_LEGACY));

            apiKeyEt.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    finishBtn.setEnabled(s.toString().startsWith("sk-") || s.toString().startsWith("gsk_"));
                }

                @Override
                public void afterTextChanged(Editable s) { }
            });

            finishBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.dictate_onboarding_complete_dialog_title)
                    .setMessage(R.string.dictate_onboarding_complete_dialog_message)
                    .setPositiveButton(R.string.dictate_okay, (dialog, which) -> {
                        SharedPreferences sp = activity.getSharedPreferences("net.devemperor.dictate", Context.MODE_PRIVATE);
                        // if user decided to use Groq, switch to all Groq settings
                        if (apiKeyEt.getText().toString().startsWith("gsk_")) {
                            sp.edit().putInt("net.devemperor.dictate.transcription_provider", 1).apply();
                            sp.edit().putInt("net.devemperor.dictate.rewording_provider", 1).apply();
                        }

                        sp.edit().putString("net.devemperor.dictate.transcription_api_key", apiKeyEt.getText().toString()).apply();
                        sp.edit().putString("net.devemperor.dictate.rewording_api_key", apiKeyEt.getText().toString()).apply();
                        sp.edit().putBoolean("net.devemperor.dictate.onboarding_complete", true).apply();
                        activity.startActivity(new Intent(activity, DictateSettingsActivity.class));
                        activity.finish();
                    })
                    .show());
        }
    }

    @Override
    public int getItemCount() {
        return layoutIds.length;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

}