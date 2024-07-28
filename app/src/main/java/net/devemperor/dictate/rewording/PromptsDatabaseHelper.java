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

    public PromptsDatabaseHelper(@Nullable Context context) {
        super(context, "prompts.db", null, 1);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL("CREATE TABLE PROMPTS (ID INTEGER PRIMARY KEY, POS INTEGER, NAME TEXT, PROMPT TEXT, REQUIRES_SELECTION BOOLEAN)");

        if (context == null) return;
        ContentValues cv = new ContentValues();
        cv.put("POS", 0);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_one_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_one_prompt));
        cv.put("REQUIRES_SELECTION", 1);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 1);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_two_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_two_prompt));
        cv.put("REQUIRES_SELECTION", 1);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 2);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_three_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_three_prompt));
        cv.put("REQUIRES_SELECTION", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);

        cv = new ContentValues();
        cv.put("POS", 3);
        cv.put("NAME", context.getString(R.string.dictate_example_prompt_four_name));
        cv.put("PROMPT", context.getString(R.string.dictate_example_prompt_four_prompt));
        cv.put("REQUIRES_SELECTION", 0);
        sqLiteDatabase.insert("PROMPTS", null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) { }

    public int add(PromptModel model) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("POS", model.getPos());
        cv.put("NAME", model.getName());
        cv.put("PROMPT", model.getPrompt());
        cv.put("REQUIRES_SELECTION", model.requiresSelection());
        return (int) db.insert("PROMPTS", null, cv);
    }

    public void update(PromptModel model) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("POS", model.getPos());
        cv.put("NAME", model.getName());
        cv.put("PROMPT", model.getPrompt());
        cv.put("REQUIRES_SELECTION", model.requiresSelection());
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
            model = new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1);
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
                models.add(new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return models;
    }

    public List<PromptModel> getAll(boolean requiresSelection) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM PROMPTS WHERE REQUIRES_SELECTION = " + (requiresSelection ? 1 : 0) + " ORDER BY POS ASC", null);

        List<PromptModel> models = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                models.add(new PromptModel(cursor.getInt(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4) == 1));
            } while (cursor.moveToNext());
        }
        models.add(new PromptModel(-1, Integer.MAX_VALUE, null, null, false));  // Add empty model for add button
        cursor.close();
        db.close();
        return models;
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
