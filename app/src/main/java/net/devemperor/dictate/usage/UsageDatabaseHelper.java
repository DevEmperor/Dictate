package net.devemperor.dictate.usage;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import net.devemperor.dictate.DictateUtils;

import java.util.ArrayList;
import java.util.List;

public class UsageDatabaseHelper extends SQLiteOpenHelper {

    Context context;

    public UsageDatabaseHelper(@Nullable Context context) {
        super(context, "usage.db", null, 2);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE USAGE (MODEL_NAME TEXT PRIMARY KEY, AUDIO_TIME LONG, INPUT_TOKENS LONG, OUTPUT_TOKENS LONG, MODEL_PROVIDER LONG)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion <= 1 && newVersion >= 2) {
            db.execSQL("ALTER TABLE USAGE ADD COLUMN MODEL_PROVIDER LONG DEFAULT 0");
        }
    }

    public void edit(String model, long timeToAdd, long inputTokensToAdd, long outputTokensToAdd, long provider) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM USAGE WHERE MODEL_NAME='" + model + "'", null);

        boolean entryExists = cursor.moveToFirst();
        cursor.close();

        if (!entryExists) {
            ContentValues cv = new ContentValues();
            cv.put("MODEL_NAME", model);
            cv.put("AUDIO_TIME", timeToAdd);
            cv.put("INPUT_TOKENS", inputTokensToAdd);
            cv.put("OUTPUT_TOKENS", outputTokensToAdd);
            cv.put("MODEL_PROVIDER", provider);
            db.insert("USAGE", null, cv);
        } else {
            cursor = db.rawQuery("SELECT * FROM USAGE WHERE MODEL_NAME='" + model + "'", null);
            if (cursor.moveToFirst()) {
                ContentValues cv = new ContentValues();
                cv.put("AUDIO_TIME", cursor.getLong(1) + timeToAdd);
                cv.put("INPUT_TOKENS", cursor.getLong(2) + inputTokensToAdd);
                cv.put("OUTPUT_TOKENS", cursor.getLong(3) + outputTokensToAdd);
                db.update("USAGE", cv, "MODEL_NAME='" + model + "'", null);
            }
            cursor.close();
        }

        db.close();
    }

    public void reset() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM USAGE");
        db.close();
    }

    public List<UsageModel> getAll() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM USAGE", null);

        List<UsageModel> models = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                models.add(new UsageModel(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3), cursor.getLong(4)));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return models;
    }

    public double getCost(String modelName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM USAGE WHERE MODEL_NAME='" + modelName + "'", null);

        double cost = 0;
        if (cursor.moveToFirst()) {
            cost = DictateUtils.calcModelCost(cursor.getString(0), cursor.getLong(2), cursor.getLong(3), cursor.getLong(4));
        }
        cursor.close();
        return cost;
    }

    public double getTotalCost() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM USAGE", null);

        double totalCost = 0;
        if (cursor.moveToFirst()) {
            do {
                totalCost += DictateUtils.calcModelCost(cursor.getString(0), cursor.getLong(2), cursor.getLong(3), cursor.getLong(4));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return totalCost;
    }

    public long getTotalAudioTime() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM USAGE", null);

        long totalAudioTime = 0;
        if (cursor.moveToFirst()) {
            do {
                totalAudioTime += cursor.getLong(1);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return totalAudioTime;
    }
}
