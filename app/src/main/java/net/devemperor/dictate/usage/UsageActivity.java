package net.devemperor.dictate.usage;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.R;

import java.util.List;

public class UsageActivity extends AppCompatActivity {

    UsageDatabaseHelper db;

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usage);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_usage);
        }

        db = new UsageDatabaseHelper(this);
        List<UsageModel> data = db.getAll();

        RecyclerView recyclerView = findViewById(R.id.usage_rv);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        UsageAdapter adapter = new UsageAdapter(this, data, db);
        recyclerView.setAdapter(adapter);

        TextView totalCostTv = findViewById(R.id.usage_total_cost_tv);
        totalCostTv.setText(getString(R.string.dictate_usage_total_cost, db.getTotalCost()));

        MaterialButton buyCreditsBtn = findViewById(R.id.usage_buy_credits_btn);
        buyCreditsBtn.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/settings/organization/billing/overview"));
            startActivity(browserIntent);
        });

        MaterialButton resetUsageBtn = findViewById(R.id.usage_reset_btn);
        resetUsageBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dictate_usage_reset_usage_title)
                .setMessage(R.string.dictate_usage_reset_usage_message)
                .setPositiveButton(R.string.dictate_yes, (dialog, which) -> {
                    db.reset();
                    data.clear();
                    adapter.notifyDataSetChanged();
                    findViewById(R.id.usage_no_usage_tv).setVisibility(View.VISIBLE);
                    resetUsageBtn.setEnabled(false);
                    totalCostTv.setText(getString(R.string.dictate_usage_total_cost, db.getTotalCost()));
                })
                .setNegativeButton(R.string.dictate_no, null)
                .show());

        findViewById(R.id.usage_no_usage_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        resetUsageBtn.setEnabled(!data.isEmpty());
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