package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PatchNotesInstrumentedTest {
  private final android.app.Instrumentation instrumentation =
      InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private Activity activity;
  private String oldLanguage;
  private boolean oldTheme, hadTheme, hadSeen;
  private int oldSeen;

  @Before
  public void saveSettings() {
    var prefs = context.getSharedPreferences("settings", 0);
    oldLanguage = prefs.getString("ui_language", null);
    oldTheme = prefs.getBoolean("dark_theme", false);
    hadTheme = prefs.contains("dark_theme");
    hadSeen = prefs.contains(PatchNotes.SEEN_VERSION);
    oldSeen = prefs.getInt(PatchNotes.SEEN_VERSION, 0);
    assertNull(EngineService.activeLecture);
    assertNull(EngineService.capture);
    assertFalse(EngineService.processing);
  }

  @After
  public void restoreSettings() {
    EngineService.activeLecture = null;
    close();
    var edit =
        context.getSharedPreferences("settings", 0).edit().putString("ui_language", oldLanguage);
    if (hadTheme) edit.putBoolean("dark_theme", oldTheme);
    else edit.remove("dark_theme");
    if (hadSeen) edit.putInt(PatchNotes.SEEN_VERSION, oldSeen);
    else edit.remove(PatchNotes.SEEN_VERSION);
    edit.commit();
    instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
    instrumentation.waitForIdleSync();
  }

  private void launch() {
    activity =
        instrumentation.startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    instrumentation.waitForIdleSync();
  }

  private void close() {
    if (activity != null) {
      instrumentation.runOnMainSync(activity::finish);
      instrumentation.waitForIdleSync();
      activity = null;
    }
  }

  private AccessibilityNodeInfo node(String text) {
    AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
    if (root == null) return null;
    for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
      if (text.contentEquals(node.getText())) return node;
    return null;
  }

  private void dialogButton(int label) {
    AccessibilityNodeInfo button = node(activity.getString(label));
    assertNotNull("Dialog action must be visible: " + activity.getString(label), button);
    assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK));
    instrumentation.waitForIdleSync();
  }

  private static Button findButton(View view, String label) {
    if (view instanceof Button button && button.getText().toString().equals(label)) return button;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = findButton(group.getChildAt(i), label);
        if (found != null) return found;
      }
    return null;
  }

  private void click(int label) {
    instrumentation.runOnMainSync(
        () -> {
          Button button =
              findButton(activity.getWindow().getDecorView(), activity.getString(label));
          assertNotNull(button);
          button.performClick();
        });
    instrumentation.waitForIdleSync();
  }

  private void recreate() {
    var monitor = instrumentation.addMonitor(MainActivity.class.getName(), null, false);
    try {
      instrumentation.runOnMainSync(activity::recreate);
      activity = instrumentation.waitForMonitorWithTimeout(monitor, 10000);
      assertNotNull(activity);
      instrumentation.waitForIdleSync();
    } finally {
      instrumentation.removeMonitor(monitor);
    }
  }

  @Test
  public void installedVersionMatchesHistoryAndUpdateShowsOnceWithoutInterruptingRecording()
      throws Exception {
    var installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    assertEquals(installed.getLongVersionCode(), PatchNotes.latest().code());
    assertEquals(installed.versionName, PatchNotes.latest().version());
    int previous = Integer.MAX_VALUE;
    for (var release : PatchNotes.HISTORY) {
      assertTrue("History must keep unique versions newest first", release.code() < previous);
      assertFalse(context.getString(release.summary()).isBlank());
      assertFalse(context.getString(release.body()).isBlank());
      previous = release.code();
    }
    var prefs = context.getSharedPreferences("settings", 0);
    int previousVersion = PatchNotes.HISTORY.get(1).code();
    prefs
        .edit()
        .putString("ui_language", "en")
        .putInt(PatchNotes.SEEN_VERSION, previousVersion)
        .commit();
    // A live/paused capture has an active lecture. Do not cover its controls.
    EngineService.activeLecture = "qa-active-recording";
    launch();
    assertNull(node(activity.getString(R.string.patch_new)));
    assertEquals(previousVersion, prefs.getInt(PatchNotes.SEEN_VERSION, 0));
    close();
    EngineService.activeLecture = null;
    launch();
    assertNotNull(node(activity.getString(R.string.patch_new)));
    assertEquals(PatchNotes.latest().code(), prefs.getInt(PatchNotes.SEEN_VERSION, 0));
    recreate();
    assertNotNull(
        "The open window survives recreation", node(activity.getString(R.string.patch_new)));
    dialogButton(R.string.patch_done);
    close();
    launch();
    assertNull(
        "The same update must not appear again", node(activity.getString(R.string.patch_new)));
    // Existing installations without this preference also get the latest note.
    close();
    prefs.edit().remove(PatchNotes.SEEN_VERSION).commit();
    launch();
    assertNotNull(node(activity.getString(R.string.patch_new)));
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    instrumentation.waitForIdleSync();
    close();
    launch();
    assertNull(node(activity.getString(R.string.patch_new)));
  }

  @Test
  public void localizedHistoryAndSmallWindowWorkInBothThemes() throws Exception {
    var prefs = context.getSharedPreferences("settings", 0);
    for (String language : new String[] {"en", "ro"})
      for (boolean dark : new boolean[] {false, true}) {
        prefs
            .edit()
            .putString("ui_language", language)
            .putBoolean("dark_theme", dark)
            .remove(PatchNotes.SEEN_VERSION)
            .commit();
        instrumentation.runOnMainSync(() -> ScribeApp.applyNightMode(context));
        instrumentation.waitForIdleSync();
        launch();
        assertNotNull(node(activity.getString(R.string.patch_new)));
        assertNotNull(node(activity.getString(PatchNotes.latest().summary())));
        assertNotNull(
            node(
                activity.getString(PatchNotes.latest().body())
                    + "\n\n"
                    + activity.getString(PatchNotes.latest().signoff())));
        assertEquals("I love youuuu\nDanny~", activity.getString(PatchNotes.find(6).signoff()));
        screenshot("patch-window-" + language + (dark ? "-dark" : "-light"));
        dialogButton(R.string.patch_history);
        instrumentation.runOnMainSync(
            () -> {
              for (var release : PatchNotes.HISTORY) {
                String summary = activity.getString(release.summary());
                assertTrue(contains(activity.getWindow().getDecorView(), summary));
              }
            });
        screenshot("patch-history-" + language + (dark ? "-dark" : "-light"));
        // Open an older release and ensure the installed-version marker stays current.
        instrumentation.runOnMainSync(
            () -> {
              ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
              Button older = findContaining(root, "0.1.2-test");
              assertNotNull(older);
              older.performClick();
            });
        instrumentation.waitForIdleSync();
        assertNotNull(node(activity.getString(R.string.patch_3_summary)));
        assertNotNull(node(activity.getString(R.string.patch_3_body) + "\n\nLove ya, Mara <3"));
        dialogButton(R.string.patch_done);
        assertEquals(PatchNotes.latest().code(), prefs.getInt(PatchNotes.SEEN_VERSION, 0));
        recreate();
        assertTrue(
            contains(
                activity.getWindow().getDecorView(), activity.getString(R.string.patch_intro)));
        click(R.string.help_back);
        assertNotNull(
            findButton(
                activity.getWindow().getDecorView(), activity.getString(R.string.patch_notes)));
        click(R.string.patch_notes);
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        instrumentation.waitForIdleSync();
        assertNotNull(
            findButton(activity.getWindow().getDecorView(), activity.getString(R.string.help)));
        close();
      }
  }

  private static Button findContaining(View view, String text) {
    if (view instanceof Button button && button.getText().toString().contains(text)) return button;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++) {
        Button found = findContaining(group.getChildAt(i), text);
        if (found != null) return found;
      }
    return null;
  }

  private static boolean contains(View view, String text) {
    if (view instanceof TextView label && label.getText().toString().contains(text)) return true;
    if (view instanceof ViewGroup group)
      for (int i = 0; i < group.getChildCount(); i++)
        if (contains(group.getChildAt(i), text)) return true;
    return false;
  }

  private void screenshot(String name) throws Exception {
    Thread.sleep(750);
    Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);
    try (FileOutputStream out =
        new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }
}
