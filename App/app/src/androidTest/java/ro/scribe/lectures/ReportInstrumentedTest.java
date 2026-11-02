package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReportInstrumentedTest {
  private Context context;
  private Store store;
  private final List<String> lectures = new ArrayList<>();
  private final List<File> files = new ArrayList<>();

  @Before
  public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    store = Store.get(context);
  }

  @After
  public void clean() {
    for (String id : lectures) store.deleteLecture(id);
    for (File file : files) file.delete();
  }

  private String lecture() {
    String id = store.createLecture(0, "ro", false, "done");
    lectures.add(id);
    return id;
  }

  private ReportBundle.Result create(ReportBundle.Request request) throws Exception {
    ReportBundle.Result result = ReportBundle.create(context, request);
    files.add(result.file());
    return result;
  }

  private static byte[] read(ZipFile zip, String name) throws Exception {
    try (var in = zip.getInputStream(zip.getEntry(name));
        var out = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      return out.toByteArray();
    }
  }

  private File wav(int seconds) throws Exception {
    File file = new File(context.getCacheDir(), "report-source-" + UUID.randomUUID() + ".wav");
    files.add(file);
    int frames = seconds * 16000, bytes = frames * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header
        .put("RIFF".getBytes())
        .putInt(bytes + 36)
        .put("WAVEfmt ".getBytes())
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes())
        .putInt(bytes);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(header.array());
      ByteBuffer block = ByteBuffer.allocate(32000).order(ByteOrder.LITTLE_ENDIAN);
      for (int second = 0; second < seconds; second++) {
        block.clear();
        for (int i = 0; i < 16000; i++) block.putShort((short) (1000 + second * 100));
        out.write(block.array());
      }
    }
    return file;
  }

  @Test
  public void selectedExcerptContainsOnlyRequestedAudioAndPreservesOriginalAndEdits()
      throws Exception {
    String id = lecture();
    File source = wav(65);
    long size = source.length();
    long chunk = store.addChunk(id, 0, source.getAbsolutePath(), 0, true);
    store.ready(chunk, 65000);
    store.saveWindow(
        store.chunks(id).get(0),
        65000,
        List.of(
            new Store.Piece(0, 10000, "PRIVATE BEFORE"),
            new Store.Piece(28000, 33000, "recunoaștere inițială"),
            new Store.Piece(40000, 50000, "PRIVATE AFTER")));
    store.edit(store.segments(id).get(1).id(), "corectură salvată");
    store.diagnostic(
        id, "window_processed", 28000, 58000, Diagnostics.object("recognition_elapsed_ms", 17));
    String other = lecture();
    store.diagnostic(other, "PRIVATE OTHER LECTURE", -1, -1, new JSONObject());
    ReportBundle.Result result =
        create(
            new ReportBundle.Request(
                id, "wrong_text", "test note", "cuvinte corecte", true, true, false, 28500, 32500));
    try (ZipFile zip = new ZipFile(result.file())) {
      byte[] excerpt = read(zip, "audio/section-00000.wav");
      assertEquals(
          "Exactly four seconds of mono PCM, without window overlap duplication",
          44 + 4000 * 32,
          excerpt.length);
      ByteBuffer pcm = ByteBuffer.wrap(excerpt).order(ByteOrder.LITTLE_ENDIAN);
      assertEquals(3800, pcm.getShort(44));
      assertEquals(4200, pcm.getShort(excerpt.length - 2));
      String text = new String(read(zip, "transcript.json"), StandardCharsets.UTF_8);
      assertTrue(text, text.contains("recunoaștere inițială"));
      assertTrue(text, text.contains("corectură salvată"));
      assertFalse(text.contains("PRIVATE"));
      String events = new String(read(zip, "diagnostics.json"), StandardCharsets.UTF_8);
      assertTrue(events.contains("recognition_elapsed_ms"));
      assertFalse(events.contains("PRIVATE OTHER"));
      assertFalse(
          new String(read(zip, "report.json"), StandardCharsets.UTF_8)
              .contains(source.getAbsolutePath()));
    }
    assertEquals(size, source.length());
    assertEquals("corectură salvată", store.segments(id).get(1).text());
    File fixture = new File(context.getFilesDir(), "qa-selected-report.zip");
    try (var in = new java.io.FileInputStream(result.file());
        var out = new FileOutputStream(fixture)) {
      byte[] block = new byte[8192];
      int count;
      while ((count = in.read(block)) != -1) out.write(block, 0, count);
    }
  }

  @Test
  public void excerptAcrossRecordingSectionsPreservesTheSelectedTimeline() throws Exception {
    String id = lecture();
    File first = wav(60), second = wav(10);
    long a = store.addChunk(id, 0, first.getAbsolutePath(), 0, true);
    long b = store.addChunk(id, 1, second.getAbsolutePath(), 60000, true);
    store.ready(a, 60000);
    store.ready(b, 10000);
    ReportBundle.Result result =
        create(
            new ReportBundle.Request(
                id, "missing_speech", "", "", true, false, false, 58500, 62500));
    assertEquals(2, result.manifest().getJSONArray("audio").length());
    try (ZipFile zip = new ZipFile(result.file())) {
      byte[] left = read(zip, "audio/section-00000.wav"),
          right = read(zip, "audio/section-00001.wav");
      assertEquals(44 + 1500 * 32, left.length);
      assertEquals(44 + 2500 * 32, right.length);
      assertEquals(6800, ByteBuffer.wrap(left).order(ByteOrder.LITTLE_ENDIAN).getShort(44));
      assertEquals(1000, ByteBuffer.wrap(right).order(ByteOrder.LITTLE_ENDIAN).getShort(44));
    }
  }

  @Test
  public void unreadableExcerptLeavesNoPartialReportAndBusyCaptureIsProtected() throws Exception {
    String id = lecture();
    File broken = new File(context.getCacheDir(), "report-broken-" + UUID.randomUUID() + ".wav");
    files.add(broken);
    try (FileOutputStream out = new FileOutputStream(broken)) {
      out.write(new byte[100]);
    }
    long chunk = store.addChunk(id, 0, broken.getAbsolutePath(), 0, true);
    store.ready(chunk, 5000);
    File root = new File(context.getCacheDir(), "reports");
    ReportBundle.readyDirectory(context);
    var before = java.util.Set.of(root.list());
    try {
      create(new ReportBundle.Request(id, "other", "", "", true, false, false, 0, 1000));
      fail("Unreadable excerpts must not produce a completed report");
    } catch (java.io.IOException expected) {
    }
    assertEquals(before, java.util.Set.of(root.list()));
    assertEquals(100, broken.length());
    RecorderEngine prior = EngineService.capture;
    try {
      EngineService.capture = new RecorderEngine(context, id);
      try {
        create(new ReportBundle.Request(id, "other", "", "", false, false, true, 0, 0));
        fail("Report generation must yield to recording");
      } catch (java.io.IOException expected) {
        assertEquals("report_busy", expected.getMessage());
      }
    } finally {
      EngineService.capture = prior;
    }
  }

  @Test
  public void unknownSourceDurationUsesActualExcerptEnd() throws Exception {
    String id = lecture();
    File source = wav(3);
    store.addChunk(id, 0, source.getAbsolutePath(), 0, true);
    ReportBundle.Result result =
        create(new ReportBundle.Request(id, "other", "", "", true, false, false, 2000, 5000));
    JSONObject attachment = result.manifest().getJSONArray("audio").getJSONObject(0);
    assertEquals(2000, attachment.getLong("start_ms"));
    assertEquals(3000, attachment.getLong("end_ms"));
  }

  @Test
  public void fullAudioIsUnchangedAndAttachmentsAreOptInEvenForFailedLectures() throws Exception {
    String id = lecture();
    File source = wav(2);
    long chunk = store.addChunk(id, 0, source.getAbsolutePath(), 0, true);
    store.ready(chunk, 2000);
    store.saveWindow(
        store.chunks(id).get(0), 2000, List.of(new Store.Piece(0, 2000, "PRIVATE WORDS")));
    store.status(id, "error", "processing_error");
    ReportBundle.Result minimal =
        create(new ReportBundle.Request(id, "crash", "", "", false, false, true, 0, 0));
    try (ZipFile zip = new ZipFile(minimal.file())) {
      assertNull(zip.getEntry("transcript.json"));
      assertEquals(3, zip.size());
      assertFalse(
          new String(read(zip, "report.json"), StandardCharsets.UTF_8).contains("PRIVATE WORDS"));
    }
    ReportBundle.Result full =
        create(new ReportBundle.Request(id, "other", "", "", true, false, true, 0, 0));
    try (ZipFile zip = new ZipFile(full.file())) {
      assertArrayEquals(
          java.nio.file.Files.readAllBytes(source.toPath()), read(zip, "audio/section-00000.wav"));
    }
    assertTrue(source.delete());
    ReportBundle.Result missing =
        create(new ReportBundle.Request(id, "playback", "", "", true, false, true, 0, 0));
    assertEquals(0, missing.manifest().getJSONArray("audio").length());
    assertEquals(1, missing.manifest().getJSONArray("missing_audio").length());
  }

  @Test
  public void sharingGrantsOnlyThePreparedZipToARealRecipient() throws Exception {
    String id = lecture();
    ReportBundle.Result result =
        create(new ReportBundle.Request(id, "other", "", "", false, false, true, 0, 0));
    Intent send = ReportBundle.shareIntent(context, result.file());
    assertEquals(Intent.ACTION_SEND, send.getAction());
    assertEquals("application/zip", send.getType());
    assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.getFlags());
    Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
    assertEquals(uri, send.getClipData().getItemAt(0).getUri());
    var provider =
        context
            .getPackageManager()
            .resolveContentProvider(context.getPackageName() + ".reports", 0);
    assertNotNull(provider);
    assertFalse(provider.exported);
    assertTrue(provider.grantUriPermissions);
    try {
      FileProvider.getUriForFile(
          context, provider.authority, new File(context.getFilesDir(), "lectures.db"));
      fail("Unrelated files must not be shareable");
    } catch (IllegalArgumentException expected) {
    }
    try {
      context
          .getContentResolver()
          .openInputStream(
              Uri.parse("content://" + provider.authority + "/reports/..%2F..%2Fsecret"));
      fail("Traversal must be rejected");
    } catch (SecurityException | java.io.FileNotFoundException expected) {
    }
    Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
    int recipientUid =
        context.getPackageManager().getApplicationInfo(testContext.getPackageName(), 0).uid;
    assertEquals(
        PackageManager.PERMISSION_DENIED,
        context.checkUriPermission(uri, -1, recipientUid, Intent.FLAG_GRANT_READ_URI_PERMISSION));
    send.setComponent(
        new ComponentName(testContext.getPackageName(), ReportReceiverActivity.class.getName()));
    send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(send);
    long deadline = System.currentTimeMillis() + 10000;
    boolean received = false;
    while (!received && System.currentTimeMillis() < deadline) {
      AccessibilityNodeInfo root =
          InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
      received =
          root != null
              && !root.findAccessibilityNodeInfosByText("Report attachment received").isEmpty();
      if (!received) Thread.sleep(100);
    }
    assertTrue("A different app must be able to open and read the granted ZIP", received);
    assertEquals(
        PackageManager.PERMISSION_DENIED,
        context.checkUriPermission(uri, -1, recipientUid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
    InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }

  @Test
  public void versionOneMigrationKeepsExistingTextAndMarksUnknownOriginals() throws Exception {
    String name = "report-migration-" + UUID.randomUUID() + ".db";
    try {
      try (SQLiteDatabase db = context.openOrCreateDatabase(name, 0, null)) {
        db.execSQL(
            "CREATE TABLE lectures(id TEXT PRIMARY KEY,delete_audio INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("INSERT INTO lectures VALUES('legacy-lecture',1)");
        db.execSQL(
            "CREATE TABLE segments(id INTEGER PRIMARY KEY, chunk INTEGER, start INTEGER, end"
                + " INTEGER, text TEXT NOT NULL)");
        db.execSQL("INSERT INTO segments VALUES(1,1,0,1000,'existing correction')");
        db.setVersion(1);
      }
      try (Store migrated = new Store(context, name);
          var cursor =
              migrated
                  .getReadableDatabase()
                  .rawQuery("SELECT text,original_text FROM segments", null)) {
        assertTrue(cursor.moveToFirst());
        assertEquals("existing correction", cursor.getString(0));
        assertTrue(cursor.isNull(1));
        assertEquals(4, migrated.getReadableDatabase().getVersion());
      }
    } finally {
      context.deleteDatabase(name);
    }
  }

  @Test
  public void reportFormAndPreviewWorkInBothLanguagesAndThemesAndKeepDrafts() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    var prefs = context.getSharedPreferences("settings", 0);
    String oldLanguage = prefs.getString("ui_language", null);
    boolean oldTheme = prefs.getBoolean("dark_theme", false),
        hadTheme = prefs.contains("dark_theme");
    String id = lecture();
    Activity activity = null;
    try {
      for (String language : new String[] {"en", "ro"})
        for (boolean dark : new boolean[] {false, true}) {
          prefs.edit().putString("ui_language", language).putBoolean("dark_theme", dark).commit();
          instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
          instrumentation.waitForIdleSync();
          activity =
              instrumentation.startActivitySync(
                  new Intent(context, ReportActivity.class)
                      .putExtra("lecture", id)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          Activity current = activity;
          instrumentation.runOnMainSync(
              () -> {
                assertFalse(((CheckBox) current.findViewById(R.id.report_audio)).isChecked());
                assertFalse(((CheckBox) current.findViewById(R.id.report_text)).isChecked());
                ((EditText) current.findViewById(R.id.report_note)).setText("Mara QA note");
              });
          screenshot("report-form-" + language + (dark ? "-dark" : "-light"));
          var monitor = instrumentation.addMonitor(ReportActivity.class.getName(), null, false);
          instrumentation.runOnMainSync(current::recreate);
          activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
          instrumentation.removeMonitor(monitor);
          assertNotNull(activity);
          Activity recreated = activity;
          instrumentation.runOnMainSync(
              () -> {
                assertEquals(
                    "Mara QA note",
                    ((EditText) recreated.findViewById(R.id.report_note)).getText().toString());
                recreated.findViewById(R.id.report_prepare).performClick();
              });
          long deadline = System.currentTimeMillis() + 10000;
          boolean[] ready = {false};
          while (!ready[0] && System.currentTimeMillis() < deadline) {
            instrumentation.runOnMainSync(
                () -> ready[0] = recreated.findViewById(R.id.report_share) != null);
            if (!ready[0]) Thread.sleep(100);
          }
          assertTrue("Report preview must become ready", ready[0]);
          screenshot("report-preview-" + language + (dark ? "-dark" : "-light"));
          instrumentation.runOnMainSync(recreated::finish);
          instrumentation.waitForIdleSync();
          activity = null;
        }
    } finally {
      if (activity != null) {
        Activity closing = activity;
        instrumentation.runOnMainSync(closing::finish);
      }
      var restore = prefs.edit().putString("ui_language", oldLanguage);
      if (hadTheme) restore.putBoolean("dark_theme", oldTheme);
      else restore.remove("dark_theme");
      restore.commit();
      instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
    }
  }

  private void screenshot(String name) throws Exception {
    Thread.sleep(500);
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    File file = new File(context.getExternalFilesDir(null), name + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}
