package net.devemperor.dictate.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.devemperor.dictate.R;
import net.devemperor.dictate.SimpleTextWatcher;

import java.util.stream.IntStream;

public class APISettingsActivity extends AppCompatActivity {

    private Spinner transcriptionProviderSpn;
    private Spinner transcriptionModelSpn;
    private EditText transcriptionAPIKeyEt;
    private EditText transcriptionCustomHostEt;
    private EditText transcriptionCustomModelEt;
    private Spinner rewordingProviderSpn;
    private EditText rewordingAPIKeyEt;
    private Spinner rewordingModelSpn;
    private EditText rewordingCustomHostEt;
    private EditText rewordingCustomModelEt;
    private LinearLayout transcriptionCustomFieldsWrapper;
    private LinearLayout rewordingCustomFieldsWrapper;

    private int transcriptionProvider;
    private String transcriptionOpenAIModel;
    private String transcriptionGroqModel;
    private String transcriptionAPIKey;
    private String transcriptionCustomHost;
    private String transcriptionCustomModel;
    private int rewordingProvider;
    private String rewordingOpenAIModel;
    private String rewordingGroqModel;
    private String rewordingAPIKey;
    private String rewordingCustomHost;
    private String rewordingCustomModel;

    private ArrayAdapter<CharSequence> transcriptionModelOpenAIAdapter;
    private ArrayAdapter<CharSequence> transcriptionModelGroqAdapter;
    private ArrayAdapter<CharSequence> transcriptionProviderAdapter;
    private ArrayAdapter<CharSequence> rewordingModelOpenAIAdapter;
    private ArrayAdapter<CharSequence> rewordingModelGroqAdapter;
    private ArrayAdapter<CharSequence> rewordingProviderAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_api_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_api_settings), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_api_settings);
        }

        SharedPreferences sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);
        transcriptionProviderSpn = findViewById(R.id.api_settings_transcription_provider_spn);
        transcriptionModelSpn = findViewById(R.id.api_settings_transcription_model_spn);
        transcriptionAPIKeyEt = findViewById(R.id.api_settings_transcription_api_key_et);
        transcriptionCustomHostEt = findViewById(R.id.api_settings_transcription_custom_host_et);
        transcriptionCustomModelEt = findViewById(R.id.api_settings_transcription_custom_model_et);
        rewordingProviderSpn = findViewById(R.id.api_settings_rewording_provider_spn);
        rewordingModelSpn = findViewById(R.id.api_settings_rewording_model_spn);
        rewordingAPIKeyEt = findViewById(R.id.api_settings_rewording_api_key_et);
        rewordingCustomHostEt = findViewById(R.id.api_settings_rewording_custom_host_et);
        rewordingCustomModelEt = findViewById(R.id.api_settings_rewording_custom_model_et);
        transcriptionCustomFieldsWrapper = findViewById(R.id.api_settings_transcription_custom_fields_wrapper);
        rewordingCustomFieldsWrapper = findViewById(R.id.api_settings_rewording_custom_fields_wrapper);


        // CONFIGURE TRANSCRIPTION API SETTINGS
        transcriptionProvider = sp.getInt("net.devemperor.dictate.transcription_provider", 0);
        transcriptionOpenAIModel = sp.getString("net.devemperor.dictate.transcription_openai_model", sp.getString("net.devemperor.dictate.transcription_model", "gpt-4o-mini-transcribe"));  // for upgrading: default is the old rewording model
        transcriptionGroqModel = sp.getString("net.devemperor.dictate.transcription_groq_model", "whisper-large-v3-turbo");
        transcriptionAPIKey = sp.getString("net.devemperor.dictate.transcription_api_key", sp.getString("net.devemperor.dictate.api_key", ""));  // for upgrading: default is the old rewording API key
        transcriptionCustomHost = sp.getString("net.devemperor.dictate.transcription_custom_host", "");
        transcriptionCustomModel = sp.getString("net.devemperor.dictate.transcription_custom_model", "");

        transcriptionModelOpenAIAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_transcription_models_openai, android.R.layout.simple_spinner_item);
        transcriptionModelOpenAIAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        transcriptionModelGroqAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_transcription_models_groq, android.R.layout.simple_spinner_item);
        transcriptionModelGroqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        transcriptionProviderAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_api_providers, android.R.layout.simple_spinner_item);
        transcriptionProviderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        transcriptionProviderSpn.setAdapter(transcriptionProviderAdapter);
        transcriptionProviderSpn.setSelection(transcriptionProvider);
        transcriptionProviderSpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sp.edit().putInt("net.devemperor.dictate.transcription_provider", position).apply();
                transcriptionProvider = position;
                updateTranscriptionModels(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        updateTranscriptionModels(transcriptionProvider);  // update the models based on the selected provider

        transcriptionModelSpn.setAdapter(transcriptionProvider != 1 ? transcriptionModelOpenAIAdapter : transcriptionModelGroqAdapter);
        transcriptionModelSpn.setSelection(transcriptionProvider != 1 ? transcriptionModelOpenAIAdapter.getPosition(transcriptionOpenAIModel) : transcriptionModelGroqAdapter.getPosition(transcriptionGroqModel));
        transcriptionModelSpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (transcriptionProvider == 0) {
                    String model = getResources().getStringArray(R.array.dictate_transcription_models_openai_values)[position];
                    sp.edit().putString("net.devemperor.dictate.transcription_openai_model", model).apply();
                    transcriptionOpenAIModel = model;
                } else {
                    String model = getResources().getStringArray(R.array.dictate_transcription_models_groq_values)[position];
                    sp.edit().putString("net.devemperor.dictate.transcription_groq_model", model).apply();
                    transcriptionGroqModel = model;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        transcriptionAPIKeyEt.setText(transcriptionAPIKey);
        transcriptionAPIKeyEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.transcription_api_key", editable.toString()).apply();
            }
        });

        transcriptionCustomHostEt.setText(transcriptionCustomHost);
        transcriptionCustomHostEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.transcription_custom_host", editable.toString()).apply();
            }
        });

        transcriptionCustomModelEt.setText(transcriptionCustomModel);
        transcriptionCustomModelEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.transcription_custom_model", editable.toString()).apply();
            }
        });


        // CONFIGURE REWORDING API SETTINGS
        rewordingProvider = sp.getInt("net.devemperor.dictate.rewording_provider", 0);
        rewordingOpenAIModel = sp.getString("net.devemperor.dictate.rewording_openai_model", sp.getString("net.devemperor.dictate.rewording_model", "gpt-4o-mini"));  // for upgrading: default is the old rewording model
        rewordingGroqModel = sp.getString("net.devemperor.dictate.rewording_groq_model", "llama-3.3-70b-versatile");
        rewordingAPIKey = sp.getString("net.devemperor.dictate.rewording_api_key", sp.getString("net.devemperor.dictate.api_key", ""));  // for upgrading: default is the old rewording API key
        rewordingCustomHost = sp.getString("net.devemperor.dictate.rewording_custom_host", "");
        rewordingCustomModel = sp.getString("net.devemperor.dictate.rewording_custom_model", "");

        rewordingModelOpenAIAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_rewording_models_openai, android.R.layout.simple_spinner_item);
        rewordingModelOpenAIAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rewordingModelGroqAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_rewording_models_groq, android.R.layout.simple_spinner_item);
        rewordingModelGroqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        rewordingProviderAdapter = ArrayAdapter.createFromResource(this, R.array.dictate_api_providers, android.R.layout.simple_spinner_item);
        rewordingProviderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        rewordingProviderSpn.setAdapter(rewordingProviderAdapter);
        rewordingProviderSpn.setSelection(rewordingProvider);
        rewordingProviderSpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sp.edit().putInt("net.devemperor.dictate.rewording_provider", position).apply();
                rewordingProvider = position;
                updateRewordingModels(position);
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        updateRewordingModels(rewordingProvider);  // update the models based on the selected provider

        rewordingModelSpn.setAdapter(rewordingProvider != 1 ? rewordingModelOpenAIAdapter : rewordingModelGroqAdapter);
        rewordingModelSpn.setSelection(rewordingProvider != 1 ? rewordingModelOpenAIAdapter.getPosition(rewordingOpenAIModel) : rewordingModelGroqAdapter.getPosition(rewordingGroqModel));
        rewordingModelSpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (rewordingProvider == 0) {
                    String model = getResources().getStringArray(R.array.dictate_rewording_models_openai_values)[position];
                    sp.edit().putString("net.devemperor.dictate.rewording_openai_model", model).apply();
                    rewordingOpenAIModel = model;
                } else {
                    String model = getResources().getStringArray(R.array.dictate_rewording_models_groq_values)[position];
                    sp.edit().putString("net.devemperor.dictate.rewording_groq_model", model).apply();
                    rewordingGroqModel = model;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        rewordingAPIKeyEt.setText(rewordingAPIKey);
        rewordingAPIKeyEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.rewording_api_key", editable.toString()).apply();
            }
        });

        rewordingCustomHostEt.setText(rewordingCustomHost);
        rewordingCustomHostEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.rewording_custom_host", editable.toString()).apply();
            }
        });

        rewordingCustomModelEt.setText(rewordingCustomModel);
        rewordingCustomModelEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                sp.edit().putString("net.devemperor.dictate.rewording_custom_model", editable.toString()).apply();
            }
        });
    }

    private void updateTranscriptionModels(int position) {
        transcriptionCustomFieldsWrapper.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        transcriptionModelSpn.setEnabled(position != 2);

        if (position == 0) {
            transcriptionModelSpn.setAdapter(transcriptionModelOpenAIAdapter);

            int pos = IntStream.range(0, transcriptionModelOpenAIAdapter.getCount())
                    .filter(i -> getResources().getStringArray(R.array.dictate_transcription_models_openai_values)[i].equals(transcriptionOpenAIModel))
                    .findFirst()
                    .orElse(0);
            transcriptionModelSpn.setSelection(pos);
        } else if (position == 1) {
            transcriptionModelSpn.setAdapter(transcriptionModelGroqAdapter);

            int pos = IntStream.range(0, transcriptionModelGroqAdapter.getCount())
                    .filter(i -> getResources().getStringArray(R.array.dictate_transcription_models_groq_values)[i].equals(transcriptionGroqModel))
                    .findFirst()
                    .orElse(0);
            transcriptionModelSpn.setSelection(pos);
        }
    }

    private void updateRewordingModels(int position) {
        rewordingCustomFieldsWrapper.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        rewordingModelSpn.setEnabled(position != 2);

        if (position == 0) {
            rewordingModelSpn.setAdapter(rewordingModelOpenAIAdapter);

            int pos = IntStream.range(0, rewordingModelOpenAIAdapter.getCount())
                    .filter(i -> getResources().getStringArray(R.array.dictate_rewording_models_openai_values)[i].equals(rewordingOpenAIModel))
                    .findFirst()
                    .orElse(0);
            rewordingModelSpn.setSelection(pos);
        } else if (position == 1) {
            rewordingModelSpn.setAdapter(rewordingModelGroqAdapter);

            int pos = IntStream.range(0, rewordingModelGroqAdapter.getCount())
                    .filter(i -> getResources().getStringArray(R.array.dictate_rewording_models_groq_values)[i].equals(rewordingGroqModel))
                    .findFirst()
                    .orElse(0);
            rewordingModelSpn.setSelection(pos);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
