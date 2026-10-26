package ro.scribe.lectures;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

public final class AudioDecoder {
  public interface Window {
    void accept(long startMs, float[] samples) throws Exception;
  }

  private AudioDecoder() {}

  public static long duration(Context context, String path) throws IOException {
    MediaExtractor extractor = new MediaExtractor();
    try {
      if (path.startsWith("content://")) extractor.setDataSource(context, Uri.parse(path), null);
      else extractor.setDataSource(path);
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat format = extractor.getTrackFormat(i);
        if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")
            && format.containsKey(MediaFormat.KEY_DURATION))
          return format.getLong(MediaFormat.KEY_DURATION) / 1000;
      }
      return 0;
    } finally {
      extractor.release();
    }
  }

  public static long decode(
      Context context, String path, long checkpoint, BooleanSupplier cancelled, Window callback)
      throws Exception {
    MediaExtractor extractor = new MediaExtractor();
    MediaCodec codec = null;
    try {
      if (path.startsWith("content://")) extractor.setDataSource(context, Uri.parse(path), null);
      else extractor.setDataSource(path);
      int track = -1;
      MediaFormat input = null;
      for (int i = 0; i < extractor.getTrackCount(); i++) {
        MediaFormat f = extractor.getTrackFormat(i);
        String mime = f.getString(MediaFormat.KEY_MIME);
        if (mime != null && mime.startsWith("audio/")) {
          track = i;
          input = f;
          break;
        }
      }
      if (track < 0) throw new IOException("unsupported_audio");
      extractor.selectTrack(track);
      if (checkpoint > 2000)
        extractor.seekTo((checkpoint - 2000) * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
      codec = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
      codec.configure(input, null, null, 0);
      codec.start();
      MediaFormat output = input;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      boolean inputDone = false, outputDone = false;
      LinearResampler resampler = null;
      WindowBuffer windows = new WindowBuffer(callback);
      long firstPts = -1;
      int misses = 0;
      while (!outputDone) {
        if (cancelled.getAsBoolean()) throw new IOException("cancelled");
        if (!inputDone) {
          int index = codec.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer buffer = codec.getInputBuffer(index);
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) {
              codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              inputDone = true;
            } else {
              codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
              extractor.advance();
            }
          }
        }
        int index = codec.dequeueOutputBuffer(info, 10000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          output = codec.getOutputFormat();
          resampler = null;
        }
        if (index >= 0) {
          misses = 0;
          if (info.size > 0) {
            ByteBuffer buffer = codec.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            int rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if (channels < 1 || channels > 8) throw new IOException("unsupported_audio");
            int encoding =
                output.containsKey(MediaFormat.KEY_PCM_ENCODING)
                    ? output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    : AudioFormat.ENCODING_PCM_16BIT;
            if (encoding != AudioFormat.ENCODING_PCM_16BIT
                && encoding != AudioFormat.ENCODING_PCM_FLOAT)
              throw new IOException("unsupported_audio");
            if (resampler == null) resampler = new LinearResampler(rate);
            if (firstPts < 0) {
              firstPts = Math.max(0, info.presentationTimeUs / 1000);
              windows.start = firstPts;
            }
            int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
            while (buffer.remaining() >= channels * bytes) {
              float sum = 0;
              for (int ch = 0; ch < channels; ch++)
                sum += bytes == 4 ? buffer.getFloat() : buffer.getShort() / 32768f;
              resampler.accept(sum / channels, windows::sample);
            }
          }
          outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          codec.releaseOutputBuffer(index, false);
        } else if (++misses > 3000) throw new IOException("Audio decoder timed out");
      }
      windows.finish();
      return windows.lastEnd;
    } finally {
      if (codec != null) {
        try {
          codec.stop();
        } finally {
          codec.release();
        }
      }
      extractor.release();
    }
  }

  private static final class WindowBuffer {
    private final float[] samples = new float[Core.WINDOW_SAMPLES];
    private int size;
    private long start, lastEnd;
    private boolean emitted;
    private final Window consumer;

    WindowBuffer(Window consumer) {
      this.consumer = consumer;
    }

    void sample(float value) throws Exception {
      samples[size++] = value;
      if (size == samples.length) {
        emit();
        System.arraycopy(samples, size - Core.OVERLAP_SAMPLES, samples, 0, Core.OVERLAP_SAMPLES);
        size = Core.OVERLAP_SAMPLES;
        start += 28000;
        emitted = true;
      }
    }

    void emit() throws Exception {
      lastEnd = start + size * 1000L / Core.SAMPLE_RATE;
      consumer.accept(start, Arrays.copyOf(samples, size));
    }

    void finish() throws Exception {
      if (size > 0 && (!emitted || size > Core.OVERLAP_SAMPLES)) emit();
    }
  }
}
