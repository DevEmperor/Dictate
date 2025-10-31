package net.devemperor.dictate.rewording;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.MenuItem;
import android.widget.EditText;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import net.devemperor.dictate.R;
import net.devemperor.dictate.SimpleTextWatcher;

public class PromptEditActivity extends AppCompatActivity {

    private PromptsDatabaseHelper db;
    private EditText promptNameEt;
    private EditText promptPromptEt;
    private MaterialSwitch promptRequiresSelectionSwitch;
    private MaterialSwitch promptAutoApplySwitch;
    private MaterialButton savePromptBtn;
    private int promptId;

    private String initialName = "";
    private String initialPrompt = "";
    private boolean initialRequiresSelection = true;
    private boolean initialAutoApply = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_prompt_edit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_prompt_edit), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_edit_prompt);
        }

        promptNameEt = findViewById(R.id.prompt_edit_name_et);
        promptPromptEt = findViewById(R.id.prompt_edit_prompt_et);
        promptRequiresSelectionSwitch = findViewById(R.id.prompt_edit_requires_selection_switch);
        promptAutoApplySwitch = findViewById(R.id.prompt_edit_auto_apply_switch);
        savePromptBtn = findViewById(R.id.prompt_edit_save_btn);

        db = new PromptsDatabaseHelper(this);

        promptId = getIntent().getIntExtra("net.devemperor.dictate.prompt_edit_activity_id", -1);
        if (promptId != -1) {
            PromptModel model = db.get(promptId);
            if (model != null) {
                promptNameEt.setText(model.getName());
                promptPromptEt.setText(model.getPrompt());
                promptRequiresSelectionSwitch.setChecked(model.requiresSelection());
                promptAutoApplySwitch.setChecked(model.isAutoApply());
                initialName = model.getName();
                initialPrompt = model.getPrompt();
                initialRequiresSelection = model.requiresSelection();
                initialAutoApply = model.isAutoApply();
            } else {
                promptId = -1;
            }
        }

        if (promptId == -1) {
            initialName = "";
            initialPrompt = "";
            initialRequiresSelection = true;
            initialAutoApply = false;
        }

        SimpleTextWatcher tw = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButtonState();
            }
        };
        promptNameEt.addTextChangedListener(tw);
        promptPromptEt.addTextChangedListener(tw);

        promptRequiresSelectionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateSaveButtonState());
        promptAutoApplySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updateSaveButtonState());

        updateSaveButtonState();

        savePromptBtn.setOnClickListener(v -> savePrompt());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        db.close();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            handleBackNavigation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void updateSaveButtonState() {
        boolean isValid = !promptNameEt.getText().toString().trim().isEmpty()
                && !promptPromptEt.getText().toString().trim().isEmpty();
        savePromptBtn.setEnabled(isValid);
    }

    private boolean hasUnsavedChanges() {
        String currentName = promptNameEt.getText().toString();
        String currentPrompt = promptPromptEt.getText().toString();
        boolean currentRequiresSelection = promptRequiresSelectionSwitch.isChecked();
        boolean currentAutoApply = promptAutoApplySwitch.isChecked();

        if (promptId == -1
                && currentName.isEmpty()
                && currentPrompt.isEmpty()
                && currentRequiresSelection
                && !currentAutoApply) {
            return false;
        }

        return !currentName.equals(initialName)
                || !currentPrompt.equals(initialPrompt)
                || currentRequiresSelection != initialRequiresSelection
                || currentAutoApply != initialAutoApply;
    }

    private void handleBackNavigation() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dictate_unsaved_changes_title)
                .setMessage(R.string.dictate_unsaved_changes_message)
                .setPositiveButton(R.string.dictate_save, (dialog, which) -> savePrompt())
                .setNegativeButton(R.string.dictate_discard_changes, (dialog, which) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setNeutralButton(R.string.dictate_cancel, (dialog, which) -> dialog.dismiss());

        androidx.appcompat.app.AlertDialog dialog = builder.show();
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(savePromptBtn.isEnabled());
    }

    private void savePrompt() {
        if (!savePromptBtn.isEnabled()) return;

        String name = promptNameEt.getText().toString();
        String prompt = promptPromptEt.getText().toString();
        boolean requiresSelection = promptRequiresSelectionSwitch.isChecked();
        boolean autoApply = promptAutoApplySwitch.isChecked();

        Intent result = new Intent();
        if (promptId == -1) {
            int addId = db.add(new PromptModel(0, db.count(), name, prompt, requiresSelection, autoApply));
            result.putExtra("added_id", addId);
        } else {
            PromptModel model = db.get(promptId);
            if (model != null) {
                model.setName(name);
                model.setPrompt(prompt);
                model.setRequiresSelection(requiresSelection);
                model.setAutoApply(autoApply);
                db.update(model);
            }
            result.putExtra("updated_id", promptId);
        }

        setResult(RESULT_OK, result);
        finish();
    }
}
