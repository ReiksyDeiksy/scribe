package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProgressInstrumentedTest {
  private static boolean contains(View view, String text) {
    if (view instanceof TextView label && label.getText().toString().contains(text)) return true;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++)
        if (contains(group.getChildAt(i), text)) return true;
    return false;
  }

  @Test
  public void progressAndWorkingStateRenderInBothLanguagesAndThemes() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Store store = Store.get(context);
    var prefs = context.getSharedPreferences("settings", 0);
    String oldLanguage = prefs.getString("ui_language", null);
    boolean hadTheme = prefs.contains("dark_theme"),
        oldTheme = prefs.getBoolean("dark_theme", false);
    boolean hadSeen = prefs.contains(PatchNotes.SEEN_VERSION);
    int oldSeen = prefs.getInt(PatchNotes.SEEN_VERSION, 0);
    String lecture = store.createLecture(0, "auto", false, "queued");
    Activity activity = null;
    long id = store.addChunk(lecture, 0, "content://qa/progress", 0, true);
    store.ready(id, 60000);
    store.saveWindow(
        store.chunks(lecture).get(0), 30000, List.of(new Store.Piece(0, 30000, "Saved speech")));
    store.recognitionState(lecture, "ro", true);
    try {
      for (String language : new String[] {"en", "ro"})
        for (boolean dark : new boolean[] {false, true}) {
          store.saveWindow(store.chunks(lecture).get(0), 30000, List.of());
          prefs
              .edit()
              .putString("ui_language", language)
              .putBoolean("dark_theme", dark)
              .putInt(PatchNotes.SEEN_VERSION, PatchNotes.latest().code())
              .commit();
          instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
          instrumentation.waitForIdleSync();
          EngineService.activeLecture = lecture;
          EngineService.processing = true;
          EngineService.windowStartMs = 28000;
          EngineService.windowEndMs = 58000;
          EngineService.windowBeganMs = SystemClock.elapsedRealtime() - 4000;
          store.status(lecture, "processing", "");
          activity =
              instrumentation.startActivitySync(
                  new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          instrumentation.waitForIdleSync();
          Activity current = activity;
          instrumentation.runOnMainSync(
              () -> {
                ProgressBar overall = current.findViewById(R.id.transcription_progress),
                    working = current.findViewById(R.id.transcription_working);
                assertEquals(500, overall.getProgress());
                assertEquals(View.VISIBLE, working.getVisibility());
                assertTrue(working.isIndeterminate());
                assertTrue(contains(current.getWindow().getDecorView(), "50%"));
                assertTrue(
                    contains(
                        current.getWindow().getDecorView(),
                        current.getString(R.string.transcript_review)));
              });
          Thread.sleep(750);
          Bitmap picture = instrumentation.getUiAutomation().takeScreenshot();
          assertNotNull(picture);
          try (FileOutputStream out =
              new FileOutputStream(
                  new File(
                      context.getExternalFilesDir(null),
                      "progress-" + language + (dark ? "-dark" : "-light") + ".png"))) {
            picture.compress(Bitmap.CompressFormat.PNG, 100, out);
          }
          picture.recycle();
          EngineService.processing = false;
          EngineService.activeLecture = null;
          EngineService.windowBeganMs = 0;
          store.saveWindow(store.chunks(lecture).get(0), 60000, List.of());
          store.status(lecture, "done", "");
          Thread.sleep(1500);
          instrumentation.waitForIdleSync();
          instrumentation.runOnMainSync(
              () -> {
                ProgressBar overall = current.findViewById(R.id.transcription_progress),
                    working = current.findViewById(R.id.transcription_working);
                assertEquals(1000, overall.getProgress());
                assertEquals(View.GONE, working.getVisibility());
              });
          instrumentation.runOnMainSync(current::finish);
          activity = null;
          instrumentation.waitForIdleSync();
        }
    } finally {
      EngineService.processing = false;
      EngineService.activeLecture = null;
      EngineService.windowBeganMs = 0;
      EngineService.checkingSpeech = false;
      if (activity != null) {
        Activity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
      store.deleteLecture(lecture);
      var edit = prefs.edit().putString("ui_language", oldLanguage);
      if (hadTheme) edit.putBoolean("dark_theme", oldTheme);
      else edit.remove("dark_theme");
      if (hadSeen) edit.putInt(PatchNotes.SEEN_VERSION, oldSeen);
      else edit.remove(PatchNotes.SEEN_VERSION);
      edit.commit();
      instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
    }
  }
}
