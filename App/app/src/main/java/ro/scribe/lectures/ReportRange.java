package ro.scribe.lectures;

final class ReportRange {
  private ReportRange() {}

  static long parse(String value) {
    if (!value.matches("[0-9]{1,5}(:[0-9]{1,2}){0,2}")) throw new IllegalArgumentException();
    String[] parts = value.split(":");
    long seconds = 0;
    for (int i = 0; i < parts.length; i++) {
      int n = Integer.parseInt(parts[i]);
      if (i > 0 && n >= 60) throw new IllegalArgumentException();
      seconds = seconds * 60 + n;
    }
    if (seconds > 86400) throw new IllegalArgumentException();
    return seconds * 1000;
  }
}
