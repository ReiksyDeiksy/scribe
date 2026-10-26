package ro.scribe.lectures;

import java.util.Locale;

/** Platform-independent rules shared by recording, imports and recovery. */
public final class Core {
  public static final long RESERVE_BYTES = 64L * 1024 * 1024;
  public static final int SAMPLE_RATE = 16000;
  public static final int WINDOW_SAMPLES = SAMPLE_RATE * 30;
  public static final int OVERLAP_SAMPLES = SAMPLE_RATE * 2;
  public static final long RECORD_CHUNK_MS = 60_000;

  private Core() {}

  public static boolean hasWords(String text) {
    return text.codePoints().anyMatch(Character::isLetterOrDigit);
  }

  public static boolean mayDelete(
      boolean enabled, String state, int segmentCount, boolean committed, boolean imported) {
    return enabled && "done".equals(state) && segmentCount > 0 && committed && !imported;
  }

  public static String clock(long millis) {
    long seconds = Math.max(0, millis) / 1000;
    return seconds >= 3600
        ? String.format(
            Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
  }

  public static String trimOverlap(String previous, String current) {
    String[] before = previous.trim().split("\\s+");
    String[] after = current.trim().split("\\s+");
    for (int count = Math.min(20, Math.min(before.length, after.length)); count >= 3; count--) {
      boolean same = true;
      for (int i = 0; i < count; i++) {
        if (!word(before[before.length - count + i]).equals(word(after[i]))) {
          same = false;
          break;
        }
      }
      if (same) return String.join(" ", java.util.Arrays.copyOfRange(after, count, after.length));
    }
    return current.trim();
  }

  private static String word(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  public static String searchQuery(String text) {
    String[] words = text.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().split("\\s+");
    StringBuilder out = new StringBuilder();
    for (String word : words)
      if (!word.isEmpty()) {
        if (out.length() > 0) out.append(" AND ");
        out.append('"').append(word).append("\"*");
      }
    return out.toString();
  }
}
