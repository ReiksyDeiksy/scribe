package ro.scribe.lectures;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PcmWindowsTest {
  @Test
  public void fileBoundariesDoNotFlushAndAllNewSamplesSurvive() throws Exception {
    List<Long> starts = new ArrayList<>();
    long[] count = {0}, previousEnd = {0};
    PcmWindows windows =
        new PcmWindows(
            0,
            (start, samples) -> {
              starts.add(start);
              long offset = start * 16;
              assertTrue(samples.length <= Core.WINDOW_SAMPLES);
              for (int i = 0; i < samples.length; i++)
                assertEquals((float) (offset + i), samples[i], 0);
              count[0] += offset + samples.length - previousEnd[0];
              previousEnd[0] = offset + samples.length;
            });
    int total = 331136 * 16;
    for (int from = 0; from < total; from += 60032 * 16) {
      int length = Math.min(60032 * 16, total - from);
      float[] file = new float[length];
      for (int i = 0; i < length; i++) file[i] = from + i;
      windows.append(file, 0, file.length);
    }
    windows.finish();
    assertEquals(12, starts.size());
    assertEquals(total, count[0]);
    assertEquals(308000L, (long) starts.get(starts.size() - 1));
  }

  @Test
  public void threeHoursRemainBoundedAndDoNotEmitOverlapAlone() throws Exception {
    int[] calls = {0};
    long[] end = {0};
    PcmWindows windows =
        new PcmWindows(
            0,
            (start, samples) -> {
              assertTrue(samples.length <= Core.WINDOW_SAMPLES);
              calls[0]++;
              end[0] = start + samples.length / 16;
            });
    float[] second = new float[16000];
    for (int i = 0; i < 10800; i++) windows.append(second, 0, second.length);
    windows.finish();
    assertEquals(10800000, end[0]);
    assertEquals(386, calls[0]);
    int[] exactly = {0};
    PcmWindows exact = new PcmWindows(1000, (start, samples) -> exactly[0]++);
    for (int i = 0; i < 30; i++) exact.append(second, 0, second.length);
    exact.finish();
    assertEquals(1, exactly[0]);
  }
}
