package ro.scribe.lectures;

import java.util.Locale;

/** Conservative signals for a bounded retry and keeping audio, never a claim of correctness. */
final class SpeechQuality {
  static boolean repetitive(String text) {
    String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    String[] words = clean.split("\\s+");
    for (int width = 1; width <= 5; width++) {
      int run = 1;
      for (int i = width; i + width <= words.length; i += width) {
        boolean same = true;
        for (int j = 0; j < width; j++)
          if (!words[i + j].equals(words[i - width + j])) {
            same = false;
            break;
          }
        run = same ? run + 1 : 1;
        if (run >= 5) return true;
      }
    }
    return clean.matches(".*([\\p{L}\\p{N}])\\1{19,}.*");
  }

  static boolean suspicious(
      String text, double logProbability, long covered, long duration, double rms) {
    return repetitive(text)
        || logProbability < -0.50
        || (duration >= 8000 && rms >= 0.004 && covered < duration / 2);
  }

  static double score(String text, double logProbability, long covered, long duration) {
    return Core.hasWords(text)
        ? logProbability
            + 0.4 * Math.min(1.0, covered / (double) duration)
            - (repetitive(text) ? 2 : 0)
        : -20;
  }

  private SpeechQuality() {}
}
