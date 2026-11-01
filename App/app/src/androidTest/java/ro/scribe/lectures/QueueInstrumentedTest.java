package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QueueInstrumentedTest {
  @Test(timeout = 240000)
  public void successfulOwnedAudioIsDeletedOnlyAfterTextCommitWhileFailedAudioIsKept()
      throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Store store = Store.get(context);
    Activity activity =
        instrumentation.startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      for (boolean valid : new boolean[] {true, false}) {
        String lecture = store.createLecture(0, "en", true, "queued");
        File audio = new File(store.directory(context, lecture), "reference.wav");
        try {
          try (FileOutputStream out = new FileOutputStream(audio)) {
            if (valid) {
              try (InputStream in = instrumentation.getContext().getAssets().open("jfk.wav")) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
              }
            } else out.write(new byte[100]);
            out.getFD().sync();
          }
          long chunk = store.addChunk(lecture, 0, audio.getAbsolutePath(), 0, false);
          store.ready(chunk, 11000);
          instrumentation.runOnMainSync(
              () -> EngineService.command(context, EngineService.PROCESS, null));
          long deadline = System.currentTimeMillis() + 120000;
          while (!store.lecture(lecture).status().equals(valid ? "done" : "error")
              || EngineService.processing) {
            if (System.currentTimeMillis() > deadline) fail("Queue did not settle");
            Thread.sleep(100);
          }
          if (valid) {
            assertFalse(audio.exists());
            assertTrue(store.segmentCount(chunk) > 0);
            assertTrue(
                store.segments(lecture).toString().toLowerCase(Locale.ROOT).contains("country"));
            String diagnostics = store.reportDiagnostics(lecture, 0, Long.MAX_VALUE).toString();
            assertTrue(diagnostics, diagnostics.contains("recognition_elapsed_ms"));
            assertTrue(diagnostics, diagnostics.contains("decode_elapsed_ms"));
            assertFalse(diagnostics, diagnostics.toLowerCase(Locale.ROOT).contains("country"));
          } else {
            assertTrue(audio.isFile());
            assertEquals(0, store.segmentCount(chunk));
          }
        } finally {
          audio.delete();
          store.deleteLecture(lecture);
        }
      }
    } finally {
      instrumentation.runOnMainSync(activity::finish);
    }
  }
}
