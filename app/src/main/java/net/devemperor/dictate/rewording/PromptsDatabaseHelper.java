package net.devemperor.dictate.rewording;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import net.devemperor.dictate.R;

import java.util.ArrayList;
import java.util.List;

public class PromptsDatabaseHelper extends SQLiteOpenHelper {
    private final Context context;

    private static final String DATABASE_NAME = "prompts.db";
    private static final int DATABASE_VERSION = 2;

    public PromptsDatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE PROMPTS (ID INTEGER PRIMARY KEY, POS INTEGER, NAME TEXT, PROMPT TEXT, REQUIRES_SELECTION BOOLEAN, AUTO_APPLY BOOLEAN DEFAULT 0)");

        if (context == null) return;
        ContentValues cv = new ContentValues();
        cv.put("POS", 0);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_one_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_one_prompt));
        cv.put("REQUIRES_SELECTION", 1);
        cv.put("AUTO_APPLY", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 1);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_two_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_two_prompt));
        cv.put("REQUIRES_SELECTION", 1);
        cv.put("AUTO_APPLY", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 2);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_three_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_three_prompt));
        cv.put("REQUIRES_SELECTION", 0);
        cv.put("AUTO_APPLY", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 3);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_four_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_four_prompt));
        cv.put("REQUIRES_SELECTION", 0);
        cv.put("AUTO_APPLY", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 4);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_five_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_five_prompt));
        cv.put("REQUIRES_SELECTION", 0);
        cv.put("AUTO_APPLY", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            sqLiteDatabase.execSQL("ALTER TABLE PROMPTS ADD COLUMN AUTO_APPLY BOOLEAN DEFAULT 0");
        }
    }

    public int add(PromptModel model) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("POS", model.getPos());
        cv.put("NAME", model.getName());
        cv.put("PROMPT", model.getPrompt());
        cv.put("REQUIRES_SELECTION", model.requiresSelection());
        cv.put("AUTO_APPLY", model.isAutoApply());
        return (int) db.insert("PROMPTS", null, cv);
    }

    public void addAll(List<PromptModel> models) {
        if (models == null || models.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (PromptModel model : models) {
                ContentValues cv = new ContentValues();
                cv.put("POS", model.getPos());
                cv.put("NAME", model.getName());
                cv.put("PROMPT", model.getPrompt());
                cv.put("REQUIRES_SELECTION", model.requiresSelection());
                cv.put("AUTO_APPLY", model.isAutoApply());
                db.insert("PROMPTS", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public void replaceAll(List<PromptModel> models) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("PROMPTS", null, null);
            if (models != null) {
                for (PromptModel model : models) {
                    ContentValues cv = new ContentValues();
                    cv.put("POS", model.getPos());
                    cv.put("NAME", model.getName());
                    cv.put("PROMPT", model.getPrompt());
                    cv.put("REQUIRES_SELECTION", model.requiresSelection());
                    cv.put("AUTO_APPLY", model.isAutoApply());
                    db.insert("PROMPTS", null, cv);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public void update(PromptModel model) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("POS", model.getPos());
        cv.put("NAME", model.getName());
        cv.put("PROMPT", model.getPrompt());
        cv.put("REQUIRES_SELECTION", model.requiresSelection());
        cv.put("AUTO_APPLY", model.isAutoApply());
        db.update("PROMPTS", cv, "ID = " + model.getId(), null);
        db.close();
    }

    public void delete(int id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("PROMPTS", "ID = " + id, null);
        db.close();
    }

    public PromptModel get(int id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM PROMPTS WHERE ID = " + id, null);
        PromptModel model = null;
        if (cursor.moveToFirst()) {
            model = new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1, cursor.getInt(5) == 1);
        }
        cursor.close();
        db.close();
        return model;
    }

    public List<PromptModel> getAll() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM PROMPTS ORDER BY POS", null);

        List<PromptModel> models = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                models.add(new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1, cursor.getInt(5) == 1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return models;
    }

    public List<PromptModel> getAllForKeyboard() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM PROMPTS ORDER BY POS ASC", null);

        List<PromptModel> promptModels = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                promptModels.add(new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1, cursor.getInt(5) == 1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        List<PromptModel> models = new ArrayList<>(promptModels.size() + 2);
        models.add(new PromptModel(-1, Integer.MIN_VALUE, null, null, false, false));  // Add empty model for instant prompt
        models.add(new PromptModel(-2, Integer.MAX_VALUE, null, null, false, false));  // Add empty model for add button
        models.addAll(promptModels);
        return models;
    }

    public List<Integer> getAutoApplyIds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT ID FROM PROMPTS WHERE AUTO_APPLY = 1 ORDER BY POS ASC", null);
        List<Integer> autoApplyIds = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                autoApplyIds.add(cursor.getInt(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return autoApplyIds;
    }

    public int count() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM PROMPTS", null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }
}
