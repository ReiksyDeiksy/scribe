package ro.scribe.lectures;

/** Continuous interpolation across decoder buffers, with no per-buffer timing drift. */
public final class LinearResampler {
  public interface Sink {
    void sample(float value) throws Exception;
  }

  private final double step;
  private double next;
  private long index;
  private float previous;

  public LinearResampler(int sourceRate) {
    if (sourceRate < 8000 || sourceRate > 384000)
      throw new IllegalArgumentException("Unsupported sample rate");
    step = sourceRate / (double) Core.SAMPLE_RATE;
  }

  public void accept(float sample, Sink sink) throws Exception {
    if (index == 0) {
      sink.sample(sample);
      next = step;
    } else
      while (next <= index) {
        double fraction = next - (index - 1);
        sink.sample((float) (previous + (sample - previous) * fraction));
        next += step;
      }
    previous = sample;
    index++;
  }
}
