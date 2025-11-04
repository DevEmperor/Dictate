package net.devemperor.dictate.settings;

import android.os.Bundle;
import android.widget.ExpandableListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.devemperor.dictate.DictateUtils;
import net.devemperor.dictate.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HowToActivity extends AppCompatActivity {

    private List<String> listDataHeader;
    private HashMap<String, List<HowToItem>> listChildData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_how_to);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_how_to), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.dictate_how_to_use_dictate);
        }

        ExpandableListView howToElv = findViewById(R.id.how_to_elv);
        prepareListData();

        HowToAdapter howToAdapter = new HowToAdapter(this, listDataHeader, listChildData);
        howToElv.setAdapter(howToAdapter);
    }

    private void prepareListData() {
        listDataHeader = new ArrayList<>();
        listChildData = new HashMap<>();

        List<String> content = new ArrayList<>();
        BufferedReader reader = null;
        try {
            String suffix = DictateUtils.getAssetLanguageSuffix();
            reader = new BufferedReader(new InputStreamReader(
                    getAssets().open("dictate_how_to_" + suffix + ".txt")));
        } catch (IOException e) {
            try {
                reader = new BufferedReader(new InputStreamReader(
                        getAssets().open("dictate_how_to_en.txt")));
            } catch (IOException fallbackException) {
                e.printStackTrace();
            }
        }

        if (reader == null) {
            return;
        }

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                content.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < content.size(); i += 3) {
            listDataHeader.add(content.get(i));
            List<HowToItem> howToItemList = new ArrayList<>();
            howToItemList.add(new HowToItem(content.get(i + 1).replace("\\n", "\n"), getResources().getIdentifier(content.get(i + 2), "raw", getPackageName())));
            listChildData.put(listDataHeader.get(i / 3), howToItemList);
        }
    }
}
