package net.devemperor.dictate.rewording;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

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

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.devemperor.dictate.R;

import java.util.List;

public class PromptsOverviewActivity extends AppCompatActivity {

    PromptsDatabaseHelper db;
    List<PromptModel> data;
    RecyclerView recyclerView;
    PromptsOverviewAdapter adapter;

    ActivityResultLauncher<Intent> addEditPromptLauncher;

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

        findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);

        FloatingActionButton addPromptFab = findViewById(R.id.prompts_overview_add_fab);
        addPromptFab.setOnClickListener(v -> {
            Intent intent = new Intent(PromptsOverviewActivity.this, PromptEditActivity.class);
            addEditPromptLauncher.launch(intent);
        });

        addEditPromptLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        int updatedId = 0;
                        int addedId = 0;
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
                            adapter.updateMoveButtons(recyclerView);
                            findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                }
        );
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