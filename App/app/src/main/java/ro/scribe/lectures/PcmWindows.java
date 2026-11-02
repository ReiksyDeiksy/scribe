package ro.scribe.lectures;

import java.util.Arrays;

/** One bounded recognition buffer; recording-file boundaries do not flush it. */
final class PcmWindows {
  private final AudioDecoder.Window consumer;
  private final float[] buffer;
  private int size;
  private long start;
  private boolean emitted;

  PcmWindows(long start, AudioDecoder.Window consumer) {
    this(start, consumer, false);
  }

  PcmWindows(long start, AudioDecoder.Window consumer, boolean mergeShortTail) {
    this.start = start;
    this.consumer = consumer;
    buffer = new float[Core.WINDOW_SAMPLES + (mergeShortTail ? Core.SAMPLE_RATE * 8 : 0)];
  }

  void append(float[] samples, int offset, int count) throws Exception {
    while (count > 0) {
      int take = Math.min(count, buffer.length - size);
      System.arraycopy(samples, offset, buffer, size, take);
      size += take;
      offset += take;
      count -= take;
      if (size == buffer.length) {
        consumer.accept(start, Arrays.copyOf(buffer, Core.WINDOW_SAMPLES));
        int step = Core.WINDOW_SAMPLES - Core.OVERLAP_SAMPLES;
        System.arraycopy(buffer, step, buffer, 0, size - step);
        size -= step;
        start += 28000;
        emitted = true;
      }
    }
  }

  void finish() throws Exception {
    if (size > 0 && (!emitted || size > Core.OVERLAP_SAMPLES))
      consumer.accept(start, Arrays.copyOf(buffer, size));
  }
}
