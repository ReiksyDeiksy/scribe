package ro.scribe.lectures;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Preserve acoustic word times so overlap is removed before building readable passages. */
final class LectureWords {
  static JSONArray segments(JSONObject result, long duration) throws Exception {
    JSONArray tokens = result.getJSONArray("tokens"), times = result.getJSONArray("timestamps");
    JSONArray lengths = result.optJSONArray("durations");
    if (tokens.length() > 20000 || times.length() != tokens.length())
      throw new IOException("Invalid word times");
    JSONArray words = new JSONArray();
    StringBuilder text = new StringBuilder();
    long start = 0, end = 0;
    for (int i = 0; i < tokens.length(); i++) {
      String token = tokens.getString(i).replace('\u2581', ' ');
      double time = times.getDouble(i);
      double length = lengths != null && i < lengths.length() ? lengths.getDouble(i) : 0;
      if (!Double.isFinite(time) || !Double.isFinite(length) || time < 0 || length < 0)
        throw new IOException("Invalid word times");
      long a = Math.min(duration, Math.round(time * 1000));
      long b = Math.min(duration, Math.max(a + 1, Math.round((time + length) * 1000)));
      if (token.isEmpty()) continue;
      if (Character.isWhitespace(token.charAt(0)) && text.length() > 0) {
        add(words, start, end, text.toString());
        text.setLength(0);
      }
      if (text.length() == 0) start = a;
      text.append(token);
      end = Math.max(a, b);
    }
    add(words, start, end, text.toString());
    if (words.length() == 0 && Core.hasWords(result.optString("text")))
      throw new IOException("Missing word times");
    return words;
  }

  private static void add(JSONArray words, long start, long end, String text) throws Exception {
    if (Core.hasWords(text) && end > start)
      words.put(new JSONObject().put("start", start).put("end", end).put("text", text.trim()));
  }

  static List<Store.Piece> passages(List<Store.Piece> words) {
    List<Store.Piece> result = new ArrayList<>();
    StringBuilder text = new StringBuilder();
    long start = 0, end = 0;
    for (Store.Piece word : words) {
      if (text.length() > 0
          && (word.end() - start > 10000
              || word.start() - end > 1000
              || text.toString().matches(".*[.!?]$"))) {
        result.add(new Store.Piece(start, end, text.toString()));
        text.setLength(0);
      }
      if (text.length() == 0) start = word.start();
      else text.append(' ');
      text.append(word.text());
      end = word.end();
    }
    if (text.length() > 0) result.add(new Store.Piece(start, end, text.toString()));
    return result;
  }

  private LectureWords() {}
}
