package ro.scribe.lectures;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.PowerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** One capture thread. AAC is encoded once; muxers roll over without restarting the microphone. */
public final class RecorderEngine {
  public volatile boolean stopped, paused;
  public volatile long position;
  public volatile int level;
  public volatile String interruption = "";
  private final Context context;
  private final Store store;
  private final String lecture;
  private MediaMuxer muxer;
  private int track, number;
  private long chunkId, chunkStart, lastPts;
  private File chunkFile;

  public RecorderEngine(Context context, String lecture) {
    this.context = context.getApplicationContext();
    this.lecture = lecture;
    store = Store.get(context);
  }

  public void run() throws Exception {
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) throw new SecurityException("microphone_permission");
    int bufferSize =
        Math.max(
            16384,
            AudioRecord.getMinBufferSize(
                    Core.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                * 4);
    AudioRecord microphone =
        new AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            Core.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize);
    MediaCodec encoder = null;
    PowerManager.WakeLock wake =
        ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scribe:recording");
    try {
      if (microphone.getState() != AudioRecord.STATE_INITIALIZED)
        throw new IOException("microphone_unavailable");
      microphone.registerAudioRecordingCallback(
          Runnable::run,
          new AudioManager.AudioRecordingCallback() {
            @Override
            public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
              for (AudioRecordingConfiguration c : configs)
                if (c.getClientAudioSessionId() == microphone.getAudioSessionId()
                    && c.isClientSilenced()) {
                  interruption = "microphone_interrupted";
                  stopped = true;
                }
            }
          });
      MediaFormat format =
          MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Core.SAMPLE_RATE, 1);
      format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
      format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
      format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
      encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
      encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
      encoder.start();
      microphone.startRecording();
      wake.acquire(12 * 60 * 60 * 1000L);
      byte[] pcm = new byte[4096];
      long frames = 0;
      boolean wasPaused = false, eos = false, finished = false;
      MediaFormat encodedFormat = null;
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long lastStorageCheck = 0;
      while (!finished) {
        if (paused && !stopped) {
          if (!wasPaused) {
            microphone.stop();
            level = 0;
            if (wake.isHeld()) wake.release();
            wasPaused = true;
          }
          Thread.sleep(80);
          continue;
        }
        if (wasPaused && !stopped) {
          wake.acquire(12 * 60 * 60 * 1000L);
          microphone.startRecording();
          wasPaused = false;
        }
        if (System.currentTimeMillis() - lastStorageCheck > 1500) {
          lastStorageCheck = System.currentTimeMillis();
          if (StorageGuard.available(context) < Core.RESERVE_BYTES) {
            interruption = "storage_low";
            stopped = true;
          }
        }
        if (!eos) {
          int index = encoder.dequeueInputBuffer(10000);
          if (index >= 0) {
            ByteBuffer in = encoder.getInputBuffer(index);
            if (stopped) {
              encoder.queueInputBuffer(
                  index,
                  0,
                  0,
                  frames * 1000000 / Core.SAMPLE_RATE,
                  MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              eos = true;
            } else {
              int count =
                  microphone.read(
                      pcm, 0, Math.min(pcm.length, in.capacity()), AudioRecord.READ_BLOCKING);
              if (count <= 0) throw new IOException("microphone_interrupted");
              in.clear();
              in.put(pcm, 0, count);
              encoder.queueInputBuffer(index, 0, count, frames * 1000000 / Core.SAMPLE_RATE, 0);
              frames += count / 2;
              position = frames * 1000 / Core.SAMPLE_RATE;
              int peak = 0;
              ByteBuffer values = ByteBuffer.wrap(pcm, 0, count).order(ByteOrder.LITTLE_ENDIAN);
              while (values.remaining() >= 2)
                peak = Math.max(peak, Math.abs((int) values.getShort()));
              level = peak * 100 / 32768;
            }
          }
        }
        int index;
        while ((index = encoder.dequeueOutputBuffer(info, 1000))
            != MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            encodedFormat = encoder.getOutputFormat();
            continue;
          }
          if (index < 0) continue;
          if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            long global = Math.max(0, info.presentationTimeUs / 1000);
            if (muxer == null || global - chunkStart >= Core.RECORD_CHUNK_MS) {
              finishChunk();
              beginChunk(encodedFormat, global);
            }
            ByteBuffer data = encoder.getOutputBuffer(index);
            data.position(info.offset);
            data.limit(info.offset + info.size);
            long original = info.presentationTimeUs;
            info.presentationTimeUs = Math.max(0, original - chunkStart * 1000);
            muxer.writeSampleData(track, data, info);
            info.presentationTimeUs = original;
            lastPts = global;
          }
          finished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
          encoder.releaseOutputBuffer(index, false);
          if (finished) break;
        }
      }
    } finally {
      try {
        microphone.stop();
      } catch (IllegalStateException ignored) {
      }
      microphone.release();
      if (encoder != null) {
        try {
          encoder.stop();
        } catch (IllegalStateException ignored) {
          // Configure/start can fail before the codec reaches the started state.
        } finally {
          encoder.release();
        }
      }
      try {
        finishChunk();
      } catch (Exception error) {
        if (chunkId != 0) store.chunkState(chunkId, "error");
        throw error;
      } finally {
        if (wake.isHeld()) wake.release();
        level = 0;
      }
    }
  }

  private void beginChunk(MediaFormat format, long time) throws IOException {
    if (format == null) throw new IOException("Audio encoder has no output format");
    chunkStart = time;
    lastPts = time;
    chunkFile =
        new File(
            store.directory(context, lecture),
            String.format(java.util.Locale.ROOT, "audio-%05d.m4a", number));
    chunkId = store.addChunk(lecture, number++, chunkFile.getAbsolutePath(), time, false);
    muxer =
        new MediaMuxer(chunkFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    track = muxer.addTrack(format);
    muxer.start();
  }

  private void finishChunk() throws IOException {
    if (muxer == null) return;
    MediaMuxer current = muxer;
    muxer = null;
    try {
      current.stop();
    } finally {
      current.release();
    }
    try (FileOutputStream sync = new FileOutputStream(chunkFile, true)) {
      sync.getFD().sync();
    }
    store.ready(chunkId, Math.max(1, lastPts - chunkStart + 64));
  }
}
