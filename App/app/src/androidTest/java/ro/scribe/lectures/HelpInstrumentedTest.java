package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HelpInstrumentedTest {
  private Activity activity;

  private static <T extends View> T find(View view, Class<T> type, String label) {
    if (type.isInstance(view)
        && (label == null
            || (view instanceof TextView text && text.getText().toString().equals(label))))
      return type.cast(view);
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        T result = find(group.getChildAt(i), type, label);
        if (result != null) return result;
      }
    return null;
  }

  private void click(int label) {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              Button button =
                  find(
                      activity.getWindow().getDecorView(), Button.class, activity.getString(label));
              assertNotNull("Expected button: " + activity.getString(label), button);
              assertTrue(button.performClick());
            });
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  }

  private void assertSettings() {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                assertNotNull(
                    "Leaving Help must return to Settings",
                    find(
                        activity.getWindow().getDecorView(),
                        Switch.class,
                        activity.getString(R.string.dark_theme))));
  }

  @Test
  public void helpScrollsAndSurvivesRecreationInBothLanguagesAndThemes() throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    var prefs = context.getSharedPreferences("settings", 0);
    String oldLanguage = prefs.getString("ui_language", null);
    boolean hadTheme = prefs.contains("dark_theme");
    boolean oldTheme = prefs.getBoolean("dark_theme", false);
    int oldSeen = prefs.getInt(PatchNotes.SEEN_VERSION, 0);
    boolean hadSeen = prefs.contains(PatchNotes.SEEN_VERSION);
    prefs.edit().putInt(PatchNotes.SEEN_VERSION, PatchNotes.latest().code()).commit();
    try {
      for (String language : new String[] {"en", "ro"}) {
        for (boolean dark : new boolean[] {false, true}) {
          prefs.edit().putString("ui_language", language).putBoolean("dark_theme", dark).commit();
          instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
          instrumentation.waitForIdleSync();
          activity =
              instrumentation.startActivitySync(
                  new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          instrumentation.waitForIdleSync();
          click(R.string.settings);
          click(R.string.help);
          instrumentation.runOnMainSync(
              () -> {
                View root = activity.getWindow().getDecorView();
                assertNotNull(
                    find(
                        root,
                        TextView.class,
                        language.equals("ro") ? "Hei, Mara! ♡" : "Hey Mara! ♡"));
                ScrollView scroll = find(root, ScrollView.class, null);
                assertNotNull(scroll);
                assertTrue("The complete guide must be scrollable", scroll.canScrollVertically(1));
              });
          String name = language + (dark ? "-dark" : "-light");
          screenshot(context, "help-" + name + "-top");
          instrumentation.runOnMainSync(
              () -> {
                View root = activity.getWindow().getDecorView();
                ScrollView scroll = find(root, ScrollView.class, null);
                scroll.setSmoothScrollingEnabled(false);
                scroll.fullScroll(View.FOCUS_DOWN);
              });
          instrumentation.waitForIdleSync();
          instrumentation.runOnMainSync(
              () -> {
                TextView footer =
                    find(
                        activity.getWindow().getDecorView(),
                        TextView.class,
                        activity.getString(R.string.help_footer));
                Rect visible = new Rect();
                assertNotNull(footer);
                assertTrue(
                    "The end of the guide must be reachable", footer.getGlobalVisibleRect(visible));
                assertEquals(
                    "The last paragraph must not be clipped", footer.getHeight(), visible.height());
              });
          screenshot(context, "help-" + name + "-bottom");
          var monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
          try {
            instrumentation.runOnMainSync(activity::recreate);
            Activity recreated = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("The activity must recreate", recreated);
            activity = recreated;
            instrumentation.waitForIdleSync();
          } finally {
            instrumentation.removeMonitor(monitor);
          }
          click(R.string.help_back);
          assertSettings();
          click(R.string.help);
          instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
          instrumentation.waitForIdleSync();
          assertSettings();
          click(R.string.library);
          instrumentation.runOnMainSync(
              () ->
                  assertNotNull(
                      find(
                          activity.getWindow().getDecorView(),
                          Button.class,
                          activity.getString(R.string.new_recording))));
          assertEquals(language, prefs.getString("ui_language", null));
          assertEquals(dark, prefs.getBoolean("dark_theme", false));
          instrumentation.runOnMainSync(activity::finish);
          instrumentation.waitForIdleSync();
          activity = null;
        }
      }
    } finally {
      if (activity != null) instrumentation.runOnMainSync(activity::finish);
      var restore = prefs.edit().putString("ui_language", oldLanguage);
      if (hadTheme) restore.putBoolean("dark_theme", oldTheme);
      else restore.remove("dark_theme");
      if (hadSeen) restore.putInt(PatchNotes.SEEN_VERSION, oldSeen);
      else restore.remove(PatchNotes.SEEN_VERSION);
      restore.commit();
      instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
      instrumentation.waitForIdleSync();
    }
  }

  private static void screenshot(Context context, String name) throws Exception {
    Thread.sleep(750);
    Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream output =
        new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
    }
    bitmap.recycle();
  }
}
