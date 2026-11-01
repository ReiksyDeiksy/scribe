package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeInstrumentedTest {
  private Activity activity;

  private static <T extends View> T find(View view, Class<T> type, String label) {
    if (type.isInstance(view)
        && view instanceof android.widget.TextView text
        && text.getText().toString().equals(label)) return type.cast(view);
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        T found = find(group.getChildAt(i), type, label);
        if (found != null) return found;
      }
    return null;
  }

  private void openSettings() {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              Button settings =
                  find(
                      activity.getWindow().getDecorView(),
                      Button.class,
                      activity.getString(R.string.settings));
              assertNotNull(settings);
              settings.performClick();
            });
  }

  private void assertTheme(boolean dark) {
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              assertEquals(
                  dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO,
                  activity.getResources().getConfiguration().uiMode
                      & Configuration.UI_MODE_NIGHT_MASK);
              Switch toggle =
                  find(
                      activity.getWindow().getDecorView(),
                      Switch.class,
                      activity.getString(R.string.dark_theme));
              assertNotNull("Settings must stay open across the theme change", toggle);
              assertEquals(dark, toggle.isChecked());
              assertEquals(
                  dark,
                  activity.getSharedPreferences("settings", 0).getBoolean("dark_theme", false));
              assertEquals(0xff00ced1, activity.getColor(R.color.scribe_primary));
              assertEquals(0xff7fffd4, activity.getColor(R.color.scribe_aquamarine));
              assertEquals(0xff152d3d, activity.getColor(R.color.scribe_on_primary));
              assertEquals(
                  dark ? 0xff0e1e24 : 0xfff2fbfa, activity.getColor(R.color.scribe_background));
            });
  }

  private void toggleTheme(boolean dark) {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    var monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
    Activity previous = activity;
    try {
      instrumentation.runOnMainSync(
          () -> {
            Switch toggle =
                find(
                    previous.getWindow().getDecorView(),
                    Switch.class,
                    previous.getString(R.string.dark_theme));
            assertNotNull(toggle);
            assertNotEquals(dark, toggle.isChecked());
            toggle.performClick();
          });
      Activity recreated = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
      assertNotNull("Android must recreate the themed activity", recreated);
      assertNotSame(previous, recreated);
      activity = recreated;
      instrumentation.waitForIdleSync();
      assertTheme(dark);
    } finally {
      instrumentation.removeMonitor(monitor);
    }
  }

  @Test
  public void settingsSwitchPreservesBrandColoursAndPersistsAcrossActivityReopen()
      throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    var prefs = context.getSharedPreferences("settings", 0);
    boolean hadTheme = prefs.contains("dark_theme");
    boolean oldTheme = prefs.getBoolean("dark_theme", false);
    String oldLanguage = prefs.getString("ui_language", null);
    try {
      prefs.edit().remove("dark_theme").putString("ui_language", "en").commit();
      instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
      instrumentation.waitForIdleSync();
      activity =
          instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      openSettings();
      assertTheme(false);
      screenshot(context, "settings-light");
      toggleTheme(true);
      screenshot(context, "settings-dark");
      instrumentation.runOnMainSync(activity::finish);
      instrumentation.waitForIdleSync();
      activity =
          instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      screenshot(context, "library-dark");
      openSettings();
      assertTheme(true);
      toggleTheme(false);
    } finally {
      if (activity != null) instrumentation.runOnMainSync(activity::finish);
      var restore = prefs.edit().putString("ui_language", oldLanguage);
      if (hadTheme) restore.putBoolean("dark_theme", oldTheme);
      else restore.remove("dark_theme");
      restore.commit();
      instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
      instrumentation.waitForIdleSync();
    }
  }

  private static void screenshot(Context context, String name) throws Exception {
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
