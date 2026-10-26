package ro.scribe.lectures;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONArray;

public final class Whisper implements AutoCloseable {
  static {
    System.loadLibrary("scribe");
  }

  public static final String MODEL = "ggml-base-q5_1.bin";
  public static final String SHA256 =
      "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898";
  private long handle;

  public Whisper(Context context) throws Exception {
    File file = new File(context.getFilesDir(), MODEL);
    if (!file.isFile() || file.length() != 59707625L || !checksum(file).equals(SHA256)) {
      if (StorageGuard.available(context) < 59707625L + Core.RESERVE_BYTES)
        throw new IOException("storage_low");
      File temp = new File(context.getFilesDir(), MODEL + ".partial");
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = context.getAssets().open("models/" + MODEL);
          FileOutputStream out = new FileOutputStream(temp)) {
        byte[] buffer = new byte[65536];
        int n;
        while ((n = in.read(buffer)) != -1) {
          out.write(buffer, 0, n);
          digest.update(buffer, 0, n);
        }
        out.getFD().sync();
      }
      if (!hex(digest.digest()).equals(SHA256)) throw new IOException("Model checksum mismatch");
      if (!temp.renameTo(file)) throw new IOException("Could not commit model");
    }
    handle = open(file.getAbsolutePath());
  }

  private static String checksum(File file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] buffer = new byte[65536];
      int n;
      while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
    }
    return hex(digest.digest());
  }

  private static String hex(byte[] bytes) {
    StringBuilder result = new StringBuilder();
    for (byte b : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return result.toString();
  }

  public JSONArray transcribe(float[] audio, String language, String previous) throws Exception {
    int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1));
    return new JSONArray(
        new String(decode(handle, audio, language, previous, threads), StandardCharsets.UTF_8));
  }

  @Override
  public void close() {
    if (handle != 0) {
      close(handle);
      handle = 0;
    }
  }

  private static native long open(String path) throws IOException;

  private static native void close(long handle);

  private static native byte[] decode(
      long handle, float[] audio, String language, String prompt, int threads) throws IOException;

  public static native void cancel();

  public static native void resetCancel();
}
