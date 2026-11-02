package ro.scribe.lectures;

import android.app.ActivityManager;
import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Offline lecture recognizer. It identifies languages itself; it does not force a language. */
final class LectureModel implements SpeechModel {
  static final String ENGINE = "parakeet";
  static final String MODEL = "parakeet-tdt-0.6b-v3-int8";
  private long handle;

  static {
    System.loadLibrary("scribe");
  }

  LectureModel(Context context, BooleanSupplier cancelled) throws Exception {
    File directory = new File(context.getFilesDir(), "models/parakeet-v3-int8");
    if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("storage_low");
    JSONObject manifest;
    try (InputStream in = context.getAssets().open("lecture-model.json")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int count;
      while ((count = in.read(buffer)) != -1) {
        if (bytes.size() + count > 16384) throw new IOException("Invalid model manifest");
        bytes.write(buffer, 0, count);
      }
      manifest = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
    }
    JSONArray files = manifest.getJSONArray("files");
    Set<String> allowed =
        Set.of("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt");
    if (files.length() != allowed.size()) throw new IOException("Invalid model manifest");
    for (int i = 0; i < files.length(); i++) {
      JSONObject item = files.getJSONObject(i);
      String name = item.getString("file"), expected = item.getString("sha256");
      if (!allowed.contains(name)) throw new IOException("Invalid model path");
      File file = new File(directory, name);
      long size = item.getLong("bytes");
      if (cancelled.getAsBoolean()) throw new IOException("cancelled");
      if (file.isFile() && file.length() == size && checksum(file, cancelled).equals(expected))
        continue;
      if (StorageGuard.available(context) < size + Core.RESERVE_BYTES)
        throw new IOException("storage_low");
      File partial = new File(directory, name + ".partial");
      boolean committed = false;
      try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = context.getAssets().open("models/parakeet-v3-int8/" + name);
            FileOutputStream out = new FileOutputStream(partial)) {
          byte[] buffer = new byte[65536];
          int count;
          long copied = 0;
          while ((count = in.read(buffer)) != -1) {
            if (cancelled.getAsBoolean()) throw new IOException("cancelled");
            copied += count;
            if (copied > size) throw new IOException("Invalid model size");
            out.write(buffer, 0, count);
            digest.update(buffer, 0, count);
          }
          out.getFD().sync();
        }
        if (partial.length() != size || !hex(digest.digest()).equals(expected))
          throw new IOException("Model checksum mismatch");
        if (!partial.renameTo(file)) throw new IOException("Could not commit model");
        committed = true;
      } finally {
        if (!committed) partial.delete();
      }
    }
    if (cancelled.getAsBoolean()) throw new IOException("cancelled");
    ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
    if (memory.lowMemory || memory.availMem < 1280L * 1024 * 1024)
      throw new IOException("memory_low");
    handle =
        open(
            directory.getAbsolutePath(),
            Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1)));
    if (cancelled.getAsBoolean()) {
      close();
      throw new IOException("cancelled");
    }
  }

  private static String checksum(File file, BooleanSupplier cancelled) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[65536];
      int count;
      while ((count = in.read(buffer)) != -1) {
        if (cancelled.getAsBoolean()) throw new IOException("cancelled");
        digest.update(buffer, 0, count);
      }
    }
    return hex(digest.digest());
  }

  private static String hex(byte[] value) {
    StringBuilder result = new StringBuilder();
    for (byte b : value) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return result.toString();
  }

  @Override
  public Whisper.Recognition recognize(float[] samples, String language, boolean beam)
      throws Exception {
    if (handle == 0) throw new IOException("Speech model is not loaded");
    JSONObject result = new JSONObject(new String(decode(handle, samples), StandardCharsets.UTF_8));
    return new Whisper.Recognition(
        LectureWords.segments(result, samples.length / 16), "auto", -1, 0);
  }

  @Override
  public void close() {
    if (handle != 0) {
      close(handle);
      handle = 0;
    }
  }

  private static native long open(String directory, int threads) throws IOException;

  private static native byte[] decode(long handle, float[] samples) throws IOException;

  private static native void close(long handle);
}
