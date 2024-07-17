package net.devemperor.dictate.rewording;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import net.devemperor.dictate.R;

public class PromptEditActivity extends AppCompatActivity {

    private PromptsDatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_edit);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_edit_prompt);
        }

        EditText promptNameEt = findViewById(R.id.prompt_edit_name_et);
        EditText promptPromptEt = findViewById(R.id.prompt_edit_prompt_et);
        MaterialSwitch promptRequiresSelectionSwitch = findViewById(R.id.prompt_edit_requires_selection_switch);
        MaterialButton savePromptBtn = findViewById(R.id.prompt_edit_save_btn);

        db = new PromptsDatabaseHelper(this);

        int id = getIntent().getIntExtra("net.devemperor.dictate.prompt_edit_activity_id", -1);
        if (id != -1) {
            PromptModel model = db.get(id);
            promptNameEt.setText(model.getName());
            promptPromptEt.setText(model.getPrompt());
            promptRequiresSelectionSwitch.setChecked(model.requiresSelection());
            savePromptBtn.setEnabled(true);
        }

        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }
            @Override
            public void afterTextChanged(Editable s) {
                savePromptBtn.setEnabled(!(promptNameEt.getText().toString().isEmpty()) && !(promptPromptEt.getText().toString().isEmpty()));
            }
        };
        promptNameEt.addTextChangedListener(tw);
        promptPromptEt.addTextChangedListener(tw);

        savePromptBtn.setOnClickListener(v -> {
            String name = promptNameEt.getText().toString();
            String prompt = promptPromptEt.getText().toString();
            boolean requiresSelection = promptRequiresSelectionSwitch.isChecked();

            Intent result = new Intent();
            if (id == -1) {
                int addId = db.add(new PromptModel(0, db.count(), name, prompt, requiresSelection));
                result.putExtra("added_id", addId);
            } else {
                PromptModel model = db.get(id);
                model.setName(name);
                model.setPrompt(prompt);
                model.setRequiresSelection(requiresSelection);
                db.update(model);
                result.putExtra("updated_id", id);
            }

            setResult(RESULT_OK, result);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
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