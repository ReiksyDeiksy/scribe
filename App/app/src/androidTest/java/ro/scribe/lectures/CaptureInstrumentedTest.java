package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CaptureInstrumentedTest {
  private static void waitFor(BooleanSupplier condition, long timeout) throws Exception {
    long end = System.currentTimeMillis() + timeout;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > end) fail("Timed out waiting for recorder state");
      Thread.sleep(100);
    }
  }

  @Test(timeout = 180000)
  public void recordingRollsOverPausesAndSurvivesScreenOffWithoutDeletingUnrecognisedAudio()
      throws Exception {
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Store store = Store.get(context);
    Activity activity =
        instrumentation.startActivitySync(
            new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    String id = store.createLecture(0, "en", true, "recording");
    try {
      instrumentation.runOnMainSync(() -> EngineService.command(context, EngineService.RECORD, id));
      waitFor(() -> EngineService.capture != null && EngineService.capture.position > 1500, 15000);
      EngineService.command(context, EngineService.MARK, null);
      waitFor(() -> !store.marks(id).isEmpty(), 5000);
      EngineService.command(context, EngineService.PAUSE, null);
      waitFor(() -> EngineService.capture.paused, 5000);
      Thread.sleep(500);
      long paused = EngineService.capture.position;
      Thread.sleep(1000);
      assertEquals(paused, EngineService.capture.position);
      EngineService.command(context, EngineService.PAUSE, null);
      instrumentation.getUiAutomation().executeShellCommand("input keyevent 223").close();
      waitFor(() -> EngineService.capture.position > paused + 2000, 10000);
      instrumentation.getUiAutomation().executeShellCommand("input keyevent 224").close();
      waitFor(() -> EngineService.capture.position > Core.RECORD_CHUNK_MS + 1200, 100000);
      EngineService.command(context, EngineService.STOP, null);
      waitFor(() -> EngineService.capture == null && !EngineService.processing, 45000);
      List<Store.Chunk> chunks = store.chunks(id);
      assertTrue(chunks.size() >= 2);
      assertFalse(store.marks(id).isEmpty());
      for (Store.Chunk chunk : chunks) {
        File file = new File(chunk.path());
        assertTrue("Audio must survive a no-words result", file.isFile());
        assertTrue(chunk.duration() > 0);
        try (MediaMetadataRetriever media = new MediaMetadataRetriever()) {
          media.setDataSource(file.getAbsolutePath());
          assertNotNull(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        }
      }
    } finally {
      if (EngineService.capture != null) EngineService.capture.stopped = true;
      if (EngineService.processing) EngineService.command(context, EngineService.CANCEL, null);
      waitFor(() -> EngineService.capture == null && !EngineService.processing, 30000);
      for (Store.Chunk chunk : store.chunks(id)) new File(chunk.path()).delete();
      store.deleteLecture(id);
      instrumentation.runOnMainSync(activity::finish);
    }
  }
}
