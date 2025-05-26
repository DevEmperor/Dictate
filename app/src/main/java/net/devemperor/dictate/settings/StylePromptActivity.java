package net.devemperor.dictate.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.devemperor.dictate.R;
import net.devemperor.dictate.SimpleTextWatcher;

public class StylePromptActivity extends AppCompatActivity {

    private SharedPreferences sp;
    private RadioButton nothingRb;
    private RadioButton predefinedRb;
    private RadioButton customRb;
    private EditText customEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_style_prompt);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_style_prompt), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_style_prompt);
        }

        sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);
        nothingRb = findViewById(R.id.style_prompt_nothing_rb);
        predefinedRb = findViewById(R.id.style_prompt_predefined_rb);
        customRb = findViewById(R.id.style_prompt_custom_rb);
        customEt = findViewById(R.id.style_prompt_custom_et);
        ImageView helpIv = findViewById(R.id.style_prompt_help_iv);

        changeSelection(sp.getInt("net.devemperor.dictate.style_prompt_selection", 1));
        customEt.setText(sp.getString("net.devemperor.dictate.style_prompt_custom_text", ""));

        nothingRb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) changeSelection(0);
        });
        predefinedRb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) changeSelection(1);
        });
        customRb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) changeSelection(2);
        });

        //open browser with prompting explanation page
        helpIv.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/docs/guides/speech-to-text#prompting"));
            startActivity(browserIntent);
        });

        //save custom prompt on every change in the EditText
        customEt.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                sp.edit().putString("net.devemperor.dictate.style_prompt_custom_text", s.toString()).apply();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void changeSelection(int selection) {
        nothingRb.setChecked(selection == 0);
        predefinedRb.setChecked(selection == 1);
        customRb.setChecked(selection == 2);
        customEt.setEnabled(selection == 2);
        sp.edit().putInt("net.devemperor.dictate.style_prompt_selection", selection).apply();
    }
}
