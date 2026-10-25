package com.studycard.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Notları cihazda SQLite veritabanında saklar. */
public class NoteDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "studycard.db";
    private static final int DB_VERSION = 1;
    private static final String T = "notes";

    private static NoteDb instance;

    public static synchronized NoteDb get(Context c) {
        if (instance == null) instance = new NoteDb(c.getApplicationContext());
        return instance;
    }

    private NoteDb(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "subject TEXT NOT NULL DEFAULT '',"
                + "title TEXT NOT NULL DEFAULT '',"
                + "content TEXT NOT NULL DEFAULT '',"
                + "images TEXT NOT NULL DEFAULT '',"
                + "chart_type TEXT NOT NULL DEFAULT 'bar',"
                + "chart_title TEXT NOT NULL DEFAULT '',"
                + "chart_data TEXT NOT NULL DEFAULT '',"
                + "review_at INTEGER NOT NULL DEFAULT 0,"
                + "review_count INTEGER NOT NULL DEFAULT 0,"
                + "notified INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL DEFAULT 0,"
                + "updated_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_review ON " + T + "(review_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // İlk sürüm: yükseltme yok.
    }

    private ContentValues toValues(Note n) {
        ContentValues v = new ContentValues();
        v.put("subject", n.subject == null ? "" : n.subject.trim());
        v.put("title", n.title == null ? "" : n.title.trim());
        v.put("content", n.content == null ? "" : n.content);
        v.put("images", Note.joinImages(n.images));
        v.put("chart_type", n.chartType == null ? Note.CHART_BAR : n.chartType);
        v.put("chart_title", n.chartTitle == null ? "" : n.chartTitle.trim());
        v.put("chart_data", Note.serializeChart(n.chartEntries));
        v.put("review_at", n.reviewAt);
        v.put("review_count", n.reviewCount);
        v.put("notified", n.notified ? 1 : 0);
        v.put("created_at", n.createdAt);
        v.put("updated_at", n.updatedAt);
        return v;
    }

    private Note fromCursor(Cursor c) {
        Note n = new Note();
        n.id = c.getLong(c.getColumnIndexOrThrow("id"));
        n.subject = c.getString(c.getColumnIndexOrThrow("subject"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.content = c.getString(c.getColumnIndexOrThrow("content"));
        n.images = Note.splitImages(c.getString(c.getColumnIndexOrThrow("images")));
        n.chartType = c.getString(c.getColumnIndexOrThrow("chart_type"));
        n.chartTitle = c.getString(c.getColumnIndexOrThrow("chart_title"));
        n.chartEntries = Note.parseChart(c.getString(c.getColumnIndexOrThrow("chart_data")));
        n.reviewAt = c.getLong(c.getColumnIndexOrThrow("review_at"));
        n.reviewCount = c.getInt(c.getColumnIndexOrThrow("review_count"));
        n.notified = c.getInt(c.getColumnIndexOrThrow("notified")) == 1;
        n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        n.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return n;
    }

    /** Yeni notu ekler ya da var olanı günceller; notun id'sini döndürür. */
    public long save(Note n) {
        long now = System.currentTimeMillis();
        if (n.createdAt == 0) n.createdAt = now;
        n.updatedAt = now;
        SQLiteDatabase db = getWritableDatabase();
        if (n.id == 0) {
            n.id = db.insert(T, null, toValues(n));
        } else {
            db.update(T, toValues(n), "id=?", new String[]{String.valueOf(n.id)});
        }
        return n.id;
    }

    public Note get(long id) {
        try (Cursor c = getReadableDatabase().query(T, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<Note> getAll() {
        List<Note> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T, null, null, null, null, null,
                "updated_at DESC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public List<Note> getPendingReminders() {
        List<Note> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(T, null, "review_at > 0 AND notified = 0",
                null, null, null, "review_at ASC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public void setNotified(long id, boolean notified) {
        ContentValues v = new ContentValues();
        v.put("notified", notified ? 1 : 0);
        getWritableDatabase().update(T, v, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(T, "id=?", new String[]{String.valueOf(id)});
    }

    /** Otomatik tamamlama için daha önce kullanılan ders adları. */
    public List<String> getSubjects() {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT subject FROM " + T + " WHERE subject != ''", null)) {
            while (c.moveToNext()) set.add(c.getString(0));
        }
        return new ArrayList<>(set);
    }
}
