package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UiInstrumentedTest {
  @Test
  public void lectureModeSelectorIsBilingualAndKeepsCompatibilityLanguageSetting()
      throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    var prefs = context.getSharedPreferences("settings", 0);
    var saved = new java.util.HashMap<>(prefs.getAll());
    Activity activity = null;
    try {
      for (String language : new String[] {"en", "ro"}) {
        prefs
            .edit()
            .putString("ui_language", language)
            .putString("recognition_engine", LectureModel.ENGINE)
            .putInt(PatchNotes.SEEN_VERSION, PatchNotes.latest().code())
            .commit();
        activity =
            instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        Activity current = activity;
        assertNull(
            languageButton(
                current.getWindow().getDecorView(),
                current.getString(R.string.lecture_language_value, "")));
        screenshot(context, "lecture-library-" + language);
        for (String engine : new String[] {"whisper", LectureModel.ENGINE}) {
          instrumentation.runOnMainSync(
              () ->
                  languageButton(
                          current.getWindow().getDecorView(),
                          current.getString(R.string.recognition_mode_value, ""))
                      .performClick());
          instrumentation.waitForIdleSync();
          String label =
              current.getString(
                  engine.equals("whisper") ? R.string.mode_compatibility : R.string.mode_lecture);
          AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
          boolean clicked = false;
          for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label))
            if (label.contentEquals(node.getText())) {
              assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));
              clicked = true;
              break;
            }
          assertTrue(clicked);
          instrumentation.waitForIdleSync();
          assertEquals(engine, prefs.getString("recognition_engine", null));
          assertEquals(
              engine.equals("whisper"),
              languageButton(
                      current.getWindow().getDecorView(),
                      current.getString(R.string.lecture_language_value, ""))
                  != null);
        }
        instrumentation.runOnMainSync(current::finish);
        activity = null;
      }
    } finally {
      if (activity != null) {
        Activity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
      var edit = prefs.edit();
      for (String key :
          new String[] {"ui_language", "recognition_engine", PatchNotes.SEEN_VERSION}) {
        if (!saved.containsKey(key)) edit.remove(key);
        else if (saved.get(key) instanceof String value) edit.putString(key, value);
        else edit.putInt(key, (Integer) saved.get(key));
      }
      edit.commit();
    }
  }

  private static Button languageButton(View view, String prefix) {
    if (view instanceof Button button && button.getText().toString().startsWith(prefix))
      return button;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = languageButton(group.getChildAt(i), prefix);
        if (found != null) return found;
      }
    return null;
  }

  @Test
  public void libraryLanguageShortcutPersistsRomanianEnglishAndAuto() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    var prefs = context.getSharedPreferences("settings", 0);
    String oldUi = prefs.getString("ui_language", null),
        oldAudio = prefs.getString("audio_language", null);
    String oldEngine = prefs.getString("recognition_engine", null);
    prefs.edit().putString("recognition_engine", "whisper").commit();
    boolean hadSeen = prefs.contains(PatchNotes.SEEN_VERSION);
    int oldSeen = prefs.getInt(PatchNotes.SEEN_VERSION, 0);
    Activity activity = null;
    try {
      for (String ui : new String[] {"en", "ro"}) {
        prefs
            .edit()
            .putString("ui_language", ui)
            .putInt(PatchNotes.SEEN_VERSION, PatchNotes.latest().code())
            .commit();
        activity =
            instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        Activity current = activity;
        for (String code : new String[] {"ro", "en", "auto"}) {
          instrumentation.runOnMainSync(
              () -> {
                Button button =
                    languageButton(
                        current.getWindow().getDecorView(),
                        current.getString(R.string.lecture_language_value, ""));
                assertNotNull(button);
                button.performClick();
              });
          instrumentation.waitForIdleSync();
          String label =
              code.equals("auto")
                  ? current.getString(R.string.automatic)
                  : java.util.Locale.forLanguageTag(code)
                      .getDisplayLanguage(
                          current.getResources().getConfiguration().getLocales().get(0));
          AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow(),
              choice = null;
          for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label))
            if (label.contentEquals(node.getText())) {
              choice = node;
              break;
            }
          assertNotNull(label, choice);
          assertTrue(choice.performAction(AccessibilityNodeInfo.ACTION_CLICK));
          instrumentation.waitForIdleSync();
          assertEquals(code, prefs.getString("audio_language", null));
          assertTrue(
              contains(
                  current.getWindow().getDecorView(),
                  current.getString(R.string.lecture_language_value, label)));
        }
        instrumentation.runOnMainSync(current::finish);
        activity = null;
        instrumentation.waitForIdleSync();
      }
    } finally {
      if (activity != null) {
        Activity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
      var edit = prefs.edit().putString("ui_language", oldUi).putString("audio_language", oldAudio);
      edit.putString("recognition_engine", oldEngine);
      if (hadSeen) edit.putInt(PatchNotes.SEEN_VERSION, oldSeen);
      else edit.remove(PatchNotes.SEEN_VERSION);
      edit.commit();
    }
  }

  private static boolean contains(View view, String text) {
    if (view instanceof TextView t && t.getText().toString().contains(text)) return true;
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) if (contains(g.getChildAt(i), text)) return true;
    return false;
  }

  private static ListView list(View view) {
    if (view instanceof ListView l) return l;
    if (view instanceof ViewGroup g)
      for (int i = 0; i < g.getChildCount(); i++) {
        ListView l = list(g.getChildAt(i));
        if (l != null) return l;
      }
    return null;
  }

  @Test
  public void bothLanguagesRenderSavedTranscriptsAndStarredPassages() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Store store = Store.get(context);
    String old = context.getSharedPreferences("settings", 0).getString("ui_language", null);
    var prefs = context.getSharedPreferences("settings", 0);
    int oldSeen = prefs.getInt(PatchNotes.SEEN_VERSION, 0);
    boolean hadSeen = prefs.contains(PatchNotes.SEEN_VERSION);
    prefs.edit().putInt(PatchNotes.SEEN_VERSION, PatchNotes.latest().code()).commit();
    store.saveCourse(0, "Anatomie QA");
    long course =
        store.courses().stream()
            .filter(c -> c.name().equals("Anatomie QA"))
            .findFirst()
            .orElseThrow()
            .id();
    String id = store.createLecture(0, "ro", false, "done");
    store.assign(id, course);
    long chunk = store.addChunk(id, 0, "content://qa/example", 0, true);
    store.ready(chunk, 30000);
    store.saveWindow(
        store.chunks(id).get(0),
        30000,
        List.of(
            new Store.Piece(
                0,
                20000,
                "Acesta este un exemplu de transcriere. Poți reveni la pasajele importante.")));
    store.chunkState(chunk, "done");
    store.mark(id, 12000);
    try {
      for (String language : new String[] {"en", "ro"}) {
        context
            .getSharedPreferences("settings", 0)
            .edit()
            .putString("ui_language", language)
            .commit();
        Activity activity =
            instrumentation.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        assertTrue(
            contains(
                activity.getWindow().getDecorView(),
                language.equals("ro") ? "Bibliotecă" : "Library"));
        screenshot(context, "library-" + language);
        instrumentation.runOnMainSync(
            () -> {
              ListView l = list(activity.getWindow().getDecorView());
              l.performItemClick(l.getChildAt(0), 0, 0);
            });
        instrumentation.waitForIdleSync();
        assertTrue(contains(activity.getWindow().getDecorView(), "Acesta este"));
        assertTrue(
            contains(
                activity.getWindow().getDecorView(),
                language.equals("ro") ? "Raportează o problemă" : "Report a problem"));
        assertTrue(contains(activity.getWindow().getDecorView(), "★  00:00"));
        assertTrue(
            contains(
                activity.getWindow().getDecorView(), language.equals("ro") ? " din " : " on "));
        screenshot(context, "transcript-" + language);
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
      }
      assertTrue(store.segments(id).get(0).text().startsWith("Acesta este"));
    } finally {
      store.deleteLecture(id);
      store.getWritableDatabase().delete("courses", "id=?", new String[] {"" + course});
      context.getSharedPreferences("settings", 0).edit().putString("ui_language", old).commit();
      if (hadSeen) prefs.edit().putInt(PatchNotes.SEEN_VERSION, oldSeen).commit();
      else prefs.edit().remove(PatchNotes.SEEN_VERSION).commit();
    }
  }

  private static void screenshot(Context context, String name) throws Exception {
    // waitForIdleSync covers the UI tree, not the final SurfaceFlinger animation frame.
    Thread.sleep(750);
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream out =
        new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}
