package ro.scribe.lectures;

import java.time.LocalDate;
import java.util.List;

/** Bundled release history, newest first. Add a localized note with every app update. */
final class PatchNotes {
  static final String SEEN_VERSION = "patch_notes_seen_version";

  record Release(int code, String version, LocalDate date, int summary, int body, int signoff) {
    Release(int code, String version, LocalDate date, int summary, int body) {
      this(code, version, date, summary, body, R.string.personal_signoff);
    }
  }

  static final List<Release> HISTORY =
      List.of(
          new Release(
              6,
              "0.1.5-test",
              LocalDate.of(2026, 11, 2),
              R.string.patch_6_summary,
              R.string.patch_6_body,
              R.string.patch_6_signoff),
          new Release(
              5,
              "0.1.4-test",
              LocalDate.of(2026, 11, 1),
              R.string.patch_5_summary,
              R.string.patch_5_body),
          new Release(
              4,
              "0.1.3-test",
              LocalDate.of(2026, 11, 1),
              R.string.patch_4_summary,
              R.string.patch_4_body),
          new Release(
              3,
              "0.1.2-test",
              LocalDate.of(2026, 11, 1),
              R.string.patch_3_summary,
              R.string.patch_3_body),
          new Release(
              2,
              "0.1.1-test",
              LocalDate.of(2026, 11, 1),
              R.string.patch_2_summary,
              R.string.patch_2_body),
          new Release(
              1,
              "0.1.0-test",
              LocalDate.of(2026, 10, 22),
              R.string.patch_1_summary,
              R.string.patch_1_body));

  static Release latest() {
    return HISTORY.get(0);
  }

  static Release find(int code) {
    for (Release release : HISTORY) if (release.code() == code) return release;
    return null;
  }

  private PatchNotes() {}
}
