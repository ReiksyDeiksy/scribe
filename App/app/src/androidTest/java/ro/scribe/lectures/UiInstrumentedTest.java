package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
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
