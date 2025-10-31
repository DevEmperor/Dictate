package net.devemperor.dictate.rewording;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PromptsOverviewActivity extends AppCompatActivity {

    PromptsDatabaseHelper db;
    List<PromptModel> data;
    RecyclerView recyclerView;
    PromptsOverviewAdapter adapter;

    ActivityResultLauncher<Intent> addEditPromptLauncher;
    private ActivityResultLauncher<String> exportPromptsLauncher;
    private ActivityResultLauncher<String[]> importPromptsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_prompts_overview);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_prompts_overview), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_prompts);
        }

        db = new PromptsDatabaseHelper(this);
        data = db.getAll();

        recyclerView = findViewById(R.id.prompts_overview_rv);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PromptsOverviewAdapter(this, data, db, position -> {
            PromptModel model = data.get(position);

            Intent intent = new Intent(this, PromptEditActivity.class);
            intent.putExtra("net.devemperor.dictate.prompt_edit_activity_id", model.getId());
            addEditPromptLauncher.launch(intent);
        });
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        MaterialButton addPromptBtn = findViewById(R.id.prompts_overview_add_btn);
        addPromptBtn.setOnClickListener(v -> {
            Intent intent = new Intent(PromptsOverviewActivity.this, PromptEditActivity.class);
            addEditPromptLauncher.launch(intent);
        });

        MaterialButton exportBtn = findViewById(R.id.prompts_overview_export_btn);
        MaterialButton importBtn = findViewById(R.id.prompts_overview_import_btn);

        exportPromptsLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri != null) {
                        exportPrompts(uri);
                    }
                }
        );

        importPromptsLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importPrompts(uri);
                    }
                }
        );

        exportBtn.setOnClickListener(v -> exportPromptsLauncher.launch(getString(R.string.dictate_prompts_export_filename)));
        importBtn.setOnClickListener(v -> importPromptsLauncher.launch(new String[]{"application/json"}));

        addEditPromptLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        int updatedId = -1;
                        int addedId = -1;
                        if (result.getData() != null) {
                            updatedId = result.getData().getIntExtra("updated_id", -1);
                            addedId = result.getData().getIntExtra("added_id", -1);
                        }
                        if (updatedId != -1) {
                            PromptModel updatedPrompt = db.get(updatedId);
                            for (int i = 0; i < data.size(); i++) {
                                if (data.get(i).getId() == updatedId) {
                                    data.set(i, updatedPrompt);
                                    adapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                        } else if (addedId != -1) {
                            data.add(db.get(addedId));
                            adapter.notifyItemInserted(data.size() - 1);
                            updateEmptyState();
                        }
                    }
                }
        );
    }

    private void exportPrompts(Uri uri) {
        List<PromptModel> prompts = db.getAll();
        JSONObject root = new JSONObject();
        JSONArray promptsArray = new JSONArray();
        try {
            for (PromptModel model : prompts) {
                JSONObject promptObject = new JSONObject();
                promptObject.put("name", model.getName());
                promptObject.put("prompt", model.getPrompt());
                promptObject.put("requiresSelection", model.requiresSelection());
                promptObject.put("autoApply", model.isAutoApply());
                promptsArray.put(promptObject);
            }
            root.put("version", 1);
            root.put("prompts", promptsArray);
        } catch (JSONException e) {
            showToast(R.string.dictate_prompts_export_failed);
            return;
        }

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                showToast(R.string.dictate_prompts_export_failed);
                return;
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                writer.write(root.toString(2));
                writer.flush();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            showToast(R.string.dictate_prompts_export_success);
        } catch (IOException e) {
            showToast(R.string.dictate_prompts_export_failed);
        }
    }

    private void importPrompts(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                showToast(R.string.dictate_prompts_import_failed);
                return;
            }
            String json = readStream(inputStream);
            List<PromptModel> importedPrompts = parsePrompts(json);
            if (importedPrompts.isEmpty()) {
                showToast(R.string.dictate_prompts_import_no_prompts);
                return;
            }
            showImportModeDialog(importedPrompts);
        } catch (IOException | JSONException e) {
            showToast(R.string.dictate_prompts_import_failed);
        }
    }

    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private List<PromptModel> parsePrompts(String json) throws JSONException {
        JSONArray promptsArray = null;
        try {
            JSONObject root = new JSONObject(json);
            promptsArray = root.optJSONArray("prompts");
        } catch (JSONException ignored) {
        }

        if (promptsArray == null) {
            promptsArray = new JSONArray(json);
        }

        List<PromptModel> prompts = new ArrayList<>();
        for (int i = 0; i < promptsArray.length(); i++) {
            JSONObject promptObject = promptsArray.optJSONObject(i);
            if (promptObject == null) continue;

            String name = promptObject.optString("name", "");
            String prompt = promptObject.optString("prompt", "");
            if (name.isEmpty() || prompt.isEmpty()) continue;

            boolean requiresSelection = promptObject.optBoolean("requiresSelection", false);
            boolean autoApply = promptObject.optBoolean("autoApply", false);
            prompts.add(new PromptModel(0, prompts.size(), name, prompt, requiresSelection, autoApply));
        }
        return prompts;
    }

    private void showImportModeDialog(List<PromptModel> importedPrompts) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dictate_prompts_import_mode_title)
                .setMessage(R.string.dictate_prompts_import_mode_message)
                .setPositiveButton(R.string.dictate_prompts_import_mode_replace, (dialog, which) -> replacePrompts(importedPrompts))
                .setNegativeButton(R.string.dictate_prompts_import_mode_add, (dialog, which) -> appendPrompts(importedPrompts))
                .setNeutralButton(R.string.dictate_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void replacePrompts(List<PromptModel> importedPrompts) {
        List<PromptModel> sanitized = new ArrayList<>(importedPrompts.size());
        for (int i = 0; i < importedPrompts.size(); i++) {
            PromptModel model = importedPrompts.get(i);
            sanitized.add(new PromptModel(0, i, model.getName(), model.getPrompt(), model.requiresSelection(), model.isAutoApply()));
        }
        db.replaceAll(sanitized);
        reloadPrompts();
        showToast(R.string.dictate_prompts_import_success);
    }

    private void appendPrompts(List<PromptModel> importedPrompts) {
        int startPos = db.count();
        List<PromptModel> sanitized = new ArrayList<>(importedPrompts.size());
        for (int i = 0; i < importedPrompts.size(); i++) {
            PromptModel model = importedPrompts.get(i);
            sanitized.add(new PromptModel(0, startPos + i, model.getName(), model.getPrompt(), model.requiresSelection(), model.isAutoApply()));
        }
        db.addAll(sanitized);
        reloadPrompts();
        showToast(R.string.dictate_prompts_import_success);
    }

    @SuppressLint("NotifyDataSetChanged")
    private void reloadPrompts() {
        data.clear();
        data.addAll(db.getAll());
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        View emptyView = findViewById(R.id.prompts_overview_no_prompts_tv);
        if (emptyView != null) {
            emptyView.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void showToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
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
