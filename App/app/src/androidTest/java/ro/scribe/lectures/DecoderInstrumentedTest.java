package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DecoderInstrumentedTest {
  @Test
  public void longStereoImportStreamsThroughBoundedOverlappingWindowsAndCanResume()
      throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File source = new File(context.getCacheDir(), "stereo-65s.wav");
    int frames = 48000 * 65, bytes = frames * 4;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header
        .put("RIFF".getBytes())
        .putInt(36 + bytes)
        .put("WAVEfmt ".getBytes())
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 2)
        .putInt(48000)
        .putInt(192000)
        .putShort((short) 4)
        .putShort((short) 16)
        .put("data".getBytes())
        .putInt(bytes);
    try (FileOutputStream out = new FileOutputStream(source)) {
      out.write(header.array());
      ByteBuffer block = ByteBuffer.allocate(4800 * 4).order(ByteOrder.LITTLE_ENDIAN);
      for (int start = 0; start < frames; start += 4800) {
        block.clear();
        for (int i = start; i < Math.min(frames, start + 4800); i++) {
          short sample = (short) (8000 * Math.sin(i * 2 * Math.PI * 440 / 48000));
          block.putShort(sample).putShort(sample);
        }
        out.write(block.array(), 0, block.position());
      }
    }
    long length = source.length();
    List<Long> starts = new ArrayList<>();
    try {
      long end =
          AudioDecoder.decode(
              context,
              source.getAbsolutePath(),
              0,
              () -> false,
              (start, samples) -> {
                starts.add(start);
                assertTrue(samples.length <= Core.WINDOW_SAMPLES);
              });
      assertEquals(List.of(0L, 28000L, 56000L), starts);
      assertEquals(65000, end, 30);
      starts.clear();
      AudioDecoder.decode(
          context,
          source.getAbsolutePath(),
          30000,
          () -> false,
          (start, samples) -> starts.add(start));
      assertTrue(starts.get(0) >= 27000);
      assertEquals(length, source.length());
      try {
        AudioDecoder.decode(
            context, source.getAbsolutePath(), 0, () -> true, (start, samples) -> {});
        fail("Cancellation must interrupt decoding");
      } catch (IOException expected) {
        assertEquals("cancelled", expected.getMessage());
      }
      assertTrue(source.isFile());
      assertEquals(length, source.length());
    } finally {
      source.delete();
    }
  }
}
