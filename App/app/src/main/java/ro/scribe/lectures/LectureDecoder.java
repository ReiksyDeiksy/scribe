package ro.scribe.lectures;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Streams contiguous sources into recognition windows without copying an entire lecture. */
final class LectureDecoder {
  static final class SourceFailure extends IOException {
    final long chunk;

    SourceFailure(long chunk, Exception cause) {
      super(cause.getMessage(), cause);
      this.chunk = chunk;
    }
  }

  static long decode(
      Context context,
      List<Store.Chunk> sources,
      long checkpoint,
      BooleanSupplier cancelled,
      AudioDecoder.Window consumer)
      throws Exception {
    return decode(context, sources, checkpoint, cancelled, consumer, false);
  }

  static long decode(
      Context context,
      List<Store.Chunk> sources,
      long checkpoint,
      BooleanSupplier cancelled,
      AudioDecoder.Window consumer,
      boolean mergeShortTail)
      throws Exception {
    long first = Math.max(sources.get(0).start(), checkpoint - 2000);
    PcmWindows windows = new PcmWindows(first, consumer, mergeShortTail);
    long[] position = {first * 16};
    for (Store.Chunk source : sources) {
      if (source.duration() > 0 && source.start() + source.duration() <= first) continue;
      long local = Math.max(0, first - source.start());
      try {
        long decoded =
            AudioDecoder.decode(
                context,
                source.path(),
                local,
                cancelled,
                (start, samples) -> {
                  if (cancelled.getAsBoolean()) throw new IOException("cancelled");
                  long at = (source.start() + start) * 16;
                  int skip = (int) Math.min(samples.length, Math.max(0, position[0] - at));
                  if (at + skip > position[0] + 16) throw new IOException("unsupported_audio");
                  int count = samples.length - skip;
                  if (count > 0) {
                    windows.append(samples, skip, count);
                    position[0] += count;
                  }
                });
        Store.get(context).sourceDuration(source.id(), decoded);
      } catch (Exception error) {
        throw new SourceFailure(source.id(), error);
      }
    }
    windows.finish();
    return position[0] / 16;
  }

  private LectureDecoder() {}
}
