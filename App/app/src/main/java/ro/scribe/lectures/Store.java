package ro.scribe.lectures;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class Store extends SQLiteOpenHelper {
  public record Course(long id, String name) {}

  public record Lecture(
      String id,
      long course,
      long created,
      String title,
      String status,
      String language,
      boolean deleteAudio,
      long duration,
      String error) {}

  public record Chunk(
      long id,
      String lecture,
      int number,
      String path,
      long start,
      long duration,
      String state,
      long processed,
      boolean imported) {}

  public record Segment(
      long id,
      long chunk,
      long start,
      long end,
      String text,
      String path,
      long chunkStart,
      boolean starred) {}

  public record Mark(long id, long time) {}

  public record Piece(long start, long end, String text) {}

  private static Store instance;

  public static synchronized Store get(Context context) {
    if (instance == null) instance = new Store(context.getApplicationContext());
    return instance;
  }

  private Store(Context context) {
    this(context, "lectures.db");
  }

  // These String constants inline values accepted by OpenParams since API 28.
  @android.annotation.SuppressLint("InlinedApi")
  Store(Context context, String databaseName) {
    super(
        context,
        databaseName,
        2,
        new SQLiteDatabase.OpenParams.Builder()
            .addOpenFlags(SQLiteDatabase.CREATE_IF_NECESSARY)
            .setJournalMode(SQLiteDatabase.JOURNAL_MODE_WAL)
            .setSynchronousMode(SQLiteDatabase.SYNC_MODE_FULL)
            .build());
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    db.setForeignKeyConstraintsEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE courses(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE)");
    db.execSQL(
        "CREATE TABLE lectures(id TEXT PRIMARY KEY, course INTEGER REFERENCES courses(id) ON DELETE"
            + " SET NULL, created INTEGER NOT NULL, title TEXT NOT NULL DEFAULT '', status TEXT NOT"
            + " NULL, language TEXT NOT NULL, delete_audio INTEGER NOT NULL DEFAULT 0, duration"
            + " INTEGER NOT NULL DEFAULT 0, error TEXT NOT NULL DEFAULT '')");
    db.execSQL(
        "CREATE TABLE chunks(id INTEGER PRIMARY KEY, lecture TEXT NOT NULL REFERENCES lectures(id)"
            + " ON DELETE CASCADE, number INTEGER NOT NULL, path TEXT NOT NULL, start INTEGER NOT"
            + " NULL, duration INTEGER NOT NULL DEFAULT 0, state TEXT NOT NULL, processed INTEGER"
            + " NOT NULL DEFAULT 0, imported INTEGER NOT NULL DEFAULT 0, UNIQUE(lecture,number))");
    db.execSQL(
        "CREATE TABLE segments(id INTEGER PRIMARY KEY, chunk INTEGER NOT NULL REFERENCES chunks(id)"
            + " ON DELETE CASCADE, start INTEGER NOT NULL, end INTEGER NOT NULL, text TEXT NOT"
            + " NULL, original_text TEXT, UNIQUE(chunk,start,end))");
    db.execSQL(
        "CREATE TABLE marks(id INTEGER PRIMARY KEY, lecture TEXT NOT NULL REFERENCES lectures(id)"
            + " ON DELETE CASCADE, time INTEGER NOT NULL)");
    db.execSQL("CREATE INDEX chunks_queue ON chunks(state,lecture,number)");
    db.execSQL("CREATE INDEX segments_time ON segments(chunk,start)");
    db.execSQL("CREATE INDEX marks_time ON marks(lecture,time)");
    db.execSQL("CREATE INDEX lectures_course ON lectures(course,created)");
    db.execSQL("CREATE VIRTUAL TABLE transcript_search USING fts4(text,tokenize=unicode61)");
    db.execSQL(
        "CREATE TRIGGER segment_insert AFTER INSERT ON segments BEGIN INSERT INTO"
            + " transcript_search(docid,text) VALUES(new.id,new.text); END");
    db.execSQL(
        "CREATE TRIGGER segment_delete AFTER DELETE ON segments BEGIN DELETE FROM transcript_search"
            + " WHERE docid=old.id; END");
    db.execSQL(
        "CREATE TRIGGER segment_update AFTER UPDATE OF text ON segments BEGIN UPDATE"
            + " transcript_search SET text=new.text WHERE docid=old.id; END");
    createDiagnostics(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int old, int next) {
    if (old == 1 && next == 2) {
      // Existing text may already have been edited. Do not claim it is the original ASR output.
      db.execSQL("ALTER TABLE segments ADD COLUMN original_text TEXT");
      createDiagnostics(db);
    } else throw new IllegalStateException("A non-destructive database migration is required");
  }

  private static void createDiagnostics(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE diagnostics(id INTEGER PRIMARY KEY, lecture TEXT NOT NULL REFERENCES"
            + " lectures(id) ON DELETE CASCADE, time INTEGER NOT NULL, start INTEGER NOT NULL,"
            + " end INTEGER NOT NULL, kind TEXT NOT NULL, data TEXT NOT NULL)");
    db.execSQL("CREATE INDEX diagnostics_lecture ON diagnostics(lecture,id)");
  }

  public synchronized List<Course> courses() {
    List<Course> result = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT id,name FROM courses ORDER BY name COLLATE NOCASE", null)) {
      while (c.moveToNext()) result.add(new Course(c.getLong(0), c.getString(1)));
    }
    return result;
  }

  public synchronized void saveCourse(long id, String name) {
    String clean = name.trim();
    if (clean.isEmpty() || clean.length() > 120)
      throw new IllegalArgumentException("Invalid course name");
    ContentValues values = new ContentValues();
    values.put("name", clean);
    if (id == 0) getWritableDatabase().insertOrThrow("courses", null, values);
    else getWritableDatabase().update("courses", values, "id=?", new String[] {Long.toString(id)});
  }

  public synchronized String createLecture(
      long course, String language, boolean deleteAudio, String status) {
    String id = UUID.randomUUID().toString();
    ContentValues v = new ContentValues();
    v.put("id", id);
    if (course > 0) v.put("course", course);
    v.put("created", System.currentTimeMillis());
    v.put("status", status);
    v.put("language", language);
    v.put("delete_audio", deleteAudio);
    getWritableDatabase().insertOrThrow("lectures", null, v);
    return id;
  }

  private Lecture lecture(Cursor c) {
    return new Lecture(
        c.getString(0),
        c.getLong(1),
        c.getLong(2),
        c.getString(3),
        c.getString(4),
        c.getString(5),
        c.getInt(6) != 0,
        c.getLong(7),
        c.getString(8));
  }

  public synchronized Lecture lecture(String id) {
    try (Cursor c =
        getReadableDatabase().rawQuery("SELECT * FROM lectures WHERE id=?", new String[] {id})) {
      return c.moveToFirst() ? lecture(c) : null;
    }
  }

  public synchronized List<Lecture> lectures(long course, String search) {
    return lectures(course, search, 0, Long.MAX_VALUE);
  }

  public synchronized List<Lecture> lectures(long course, String search, long from, long until) {
    String fts = Core.searchQuery(search);
    String sql =
        "SELECT l.* FROM lectures l LEFT JOIN courses c ON c.id=l.course WHERE (CAST(? AS"
            + " INTEGER)=0 OR l.course=?) AND l.created>=? AND l.created<?";
    List<String> args =
        new ArrayList<>(
            List.of(
                Long.toString(course),
                Long.toString(course),
                Long.toString(from),
                Long.toString(until)));
    if (!search.trim().isEmpty()) {
      sql += " AND (l.title LIKE ? ESCAPE '\\' OR c.name LIKE ? ESCAPE '\\'";
      String like =
          "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      args.add(like);
      args.add(like);
      if (!fts.isEmpty()) {
        sql +=
            " OR EXISTS(SELECT 1 FROM segments s JOIN chunks k ON k.id=s.chunk JOIN"
                + " transcript_search f ON f.docid=s.id WHERE k.lecture=l.id AND f.text MATCH ?)";
        args.add(fts);
      }
      sql += ")";
    }
    sql += " ORDER BY l.created DESC";
    List<Lecture> out = new ArrayList<>();
    try (Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]))) {
      while (c.moveToNext()) out.add(lecture(c));
    }
    return out;
  }

  public synchronized void assign(String id, long course) {
    ContentValues v = new ContentValues();
    if (course == 0) v.putNull("course");
    else v.put("course", course);
    getWritableDatabase().update("lectures", v, "id=?", new String[] {id});
  }

  public synchronized void rename(String id, String title) {
    ContentValues v = new ContentValues();
    v.put("title", title.trim());
    getWritableDatabase().update("lectures", v, "id=?", new String[] {id});
  }

  public synchronized void status(String id, String status, String error) {
    ContentValues v = new ContentValues();
    v.put("status", status);
    v.put("error", error == null ? "" : error);
    getWritableDatabase().update("lectures", v, "id=?", new String[] {id});
  }

  public synchronized long addChunk(
      String lecture, int number, String path, long start, boolean imported) {
    ContentValues v = new ContentValues();
    v.put("lecture", lecture);
    v.put("number", number);
    v.put("path", path);
    v.put("start", start);
    v.put("state", imported ? "ready" : "writing");
    v.put("imported", imported);
    return getWritableDatabase().insertOrThrow("chunks", null, v);
  }

  public synchronized void ready(long id, long duration) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("duration", duration);
      v.put("state", "ready");
      db.update("chunks", v, "id=?", new String[] {"" + id});
      db.execSQL(
          "UPDATE lectures SET duration=MAX(duration,(SELECT start+duration FROM chunks WHERE"
              + " id=?)) WHERE id=(SELECT lecture FROM chunks WHERE id=?)",
          new Object[] {id, id});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  private Chunk chunk(Cursor c) {
    return new Chunk(
        c.getLong(0),
        c.getString(1),
        c.getInt(2),
        c.getString(3),
        c.getLong(4),
        c.getLong(5),
        c.getString(6),
        c.getLong(7),
        c.getInt(8) != 0);
  }

  public synchronized Chunk next() {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT k.* FROM chunks k JOIN lectures l ON l.id=k.lecture WHERE k.state='ready'"
                    + " AND l.status NOT IN ('recording','paused','cancelled') ORDER BY"
                    + " l.created,k.number LIMIT 1",
                null)) {
      return c.moveToFirst() ? chunk(c) : null;
    }
  }

  public synchronized List<Chunk> chunks(String lecture) {
    List<Chunk> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT * FROM chunks WHERE lecture=? ORDER BY number", new String[] {lecture})) {
      while (c.moveToNext()) out.add(chunk(c));
    }
    return out;
  }

  public synchronized void chunkState(long id, String state) {
    ContentValues v = new ContentValues();
    v.put("state", state);
    getWritableDatabase().update("chunks", v, "id=?", new String[] {"" + id});
  }

  public synchronized void saveWindow(Chunk chunk, long end, List<Piece> pieces) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      for (Piece p : pieces) {
        ContentValues v = new ContentValues();
        v.put("chunk", chunk.id());
        v.put("start", p.start());
        v.put("end", p.end());
        v.put("text", p.text());
        v.put("original_text", p.text());
        db.insertWithOnConflict("segments", null, v, SQLiteDatabase.CONFLICT_IGNORE);
      }
      ContentValues v = new ContentValues();
      v.put("processed", end);
      db.update("chunks", v, "id=?", new String[] {"" + chunk.id()});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized List<Segment> segments(String lecture) {
    List<Segment> out = new ArrayList<>();
    String sql =
        "SELECT s.id,s.chunk,s.start,s.end,s.text,k.path,k.start,EXISTS(SELECT 1 FROM marks m WHERE"
            + " m.lecture=k.lecture AND m.time>=s.start AND m.time<s.end) FROM segments s JOIN"
            + " chunks k ON k.id=s.chunk WHERE k.lecture=? ORDER BY s.start,s.id";
    try (Cursor c = getReadableDatabase().rawQuery(sql, new String[] {lecture})) {
      while (c.moveToNext())
        out.add(
            new Segment(
                c.getLong(0),
                c.getLong(1),
                c.getLong(2),
                c.getLong(3),
                c.getString(4),
                c.getString(5),
                c.getLong(6),
                c.getInt(7) != 0));
    }
    return out;
  }

  public synchronized int segmentCount(long chunk) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT COUNT(*) FROM segments WHERE chunk=?", new String[] {"" + chunk})) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  public synchronized void edit(long id, String text) {
    ContentValues v = new ContentValues();
    v.put("text", text);
    getWritableDatabase().update("segments", v, "id=?", new String[] {"" + id});
  }

  public synchronized void diagnostic(
      String lecture, String kind, long start, long end, JSONObject data) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      ContentValues v = new ContentValues();
      v.put("lecture", lecture);
      v.put("time", System.currentTimeMillis());
      v.put("start", start);
      v.put("end", end);
      v.put("kind", kind);
      v.put("data", data.toString());
      db.insertOrThrow("diagnostics", null, v);
      db.execSQL(
          "DELETE FROM diagnostics WHERE lecture=? AND id NOT IN (SELECT id FROM diagnostics"
              + " WHERE lecture=? ORDER BY id DESC LIMIT 2048)",
          new Object[] {lecture, lecture});
      db.execSQL(
          "DELETE FROM diagnostics WHERE id NOT IN (SELECT id FROM diagnostics ORDER BY id DESC"
              + " LIMIT 8192)");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized JSONObject reportText(String lecture, long start, long end)
      throws JSONException {
    JSONArray passages = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT s.start,s.end,s.text,s.original_text FROM segments s JOIN chunks k ON"
                    + " k.id=s.chunk WHERE k.lecture=? AND s.start<? AND s.end>? ORDER BY"
                    + " s.start,s.id",
                new String[] {lecture, Long.toString(end), Long.toString(start)})) {
      while (c.moveToNext()) {
        passages.put(
            new JSONObject()
                .put("start_ms", c.getLong(0))
                .put("end_ms", c.getLong(1))
                .put("text", c.getString(2))
                .put("original_text", c.isNull(3) ? JSONObject.NULL : c.getString(3)));
      }
    }
    return new JSONObject()
        .put("passages", passages)
        .put(
            "scope",
            "Overlapping passages are included in full; original_text is null for legacy text.");
  }

  public synchronized JSONArray reportDiagnostics(String lecture, long start, long end)
      throws JSONException {
    JSONArray events = new JSONArray();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT time,start,end,kind,data FROM diagnostics WHERE lecture=?"
                    + " AND (start<0 OR (start<? AND end>?)) ORDER BY id",
                new String[] {lecture, Long.toString(end), Long.toString(start)})) {
      while (c.moveToNext())
        events.put(
            new JSONObject()
                .put("time_unix_ms", c.getLong(0))
                .put("start_ms", c.getLong(1))
                .put("end_ms", c.getLong(2))
                .put("kind", c.getString(3))
                .put("data", new JSONObject(c.getString(4))));
    }
    return events;
  }

  public synchronized long mark(String lecture, long time) {
    ContentValues v = new ContentValues();
    v.put("lecture", lecture);
    v.put("time", time);
    return getWritableDatabase().insertOrThrow("marks", null, v);
  }

  public synchronized List<Mark> marks(String lecture) {
    List<Mark> out = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,time FROM marks WHERE lecture=? ORDER BY time",
                new String[] {lecture})) {
      while (c.moveToNext()) out.add(new Mark(c.getLong(0), c.getLong(1)));
    }
    return out;
  }

  public synchronized void retry(String lecture) {
    getWritableDatabase()
        .execSQL(
            "UPDATE chunks SET state='ready' WHERE lecture=? AND state IN ('error','processing')",
            new Object[] {lecture});
    status(lecture, "queued", "");
  }

  public synchronized void recover() {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.execSQL("UPDATE chunks SET state='ready' WHERE state='processing'");
      db.execSQL("UPDATE chunks SET state='error' WHERE state='writing'");
      db.execSQL(
          "UPDATE lectures SET status='interrupted',error='interrupted' WHERE status IN"
              + " ('recording','paused')");
      db.execSQL("UPDATE lectures SET status='queued' WHERE status='processing'");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void settle(String lecture) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery(
                "SELECT SUM(CASE WHEN state='error' THEN 1 ELSE 0 END),SUM(CASE WHEN state IN"
                    + " ('ready','processing','writing') THEN 1 ELSE 0 END) FROM chunks WHERE"
                    + " lecture=?",
                new String[] {lecture})) {
      c.moveToFirst();
      if (c.getInt(1) == 0) {
        Lecture l = lecture(lecture);
        if (l != null && !l.status().equals("cancelled"))
          status(
              lecture,
              c.getInt(0) > 0 ? "error" : "done",
              c.getInt(0) > 0 ? (l.error().isEmpty() ? "processing_error" : l.error()) : "");
      }
    }
  }

  public synchronized void deleteLecture(String id) {
    getWritableDatabase().delete("lectures", "id=?", new String[] {id});
  }

  public synchronized boolean sourceUsed(String path) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT 1 FROM chunks WHERE path=? LIMIT 1", new String[] {path})) {
      return c.moveToFirst();
    }
  }

  public File directory(Context context, String id) {
    if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException();
    File dir = new File(context.getFilesDir(), "lectures/" + id);
    if (!dir.isDirectory() && !dir.mkdirs())
      throw new IllegalStateException("Cannot create audio directory");
    return dir;
  }
}
