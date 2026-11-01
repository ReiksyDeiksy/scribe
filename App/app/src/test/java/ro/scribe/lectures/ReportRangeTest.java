package ro.scribe.lectures;

import static org.junit.Assert.*;

import org.junit.Test;

public class ReportRangeTest {
  @Test
  public void parsesLectureTimesWithoutWrappingHours() {
    assertEquals(0, ReportRange.parse("00:00"));
    assertEquals(135000, ReportRange.parse("02:15"));
    assertEquals(10800000, ReportRange.parse("3:00:00"));
    assertEquals(86400000, ReportRange.parse("24:00:00"));
    for (String invalid :
        new String[] {"", "-1", "1:60", "1:00:60", "24:00:01", "1:2:3:4", "NaN"}) {
      try {
        ReportRange.parse(invalid);
        fail(invalid);
      } catch (IllegalArgumentException expected) {
      }
    }
  }
}
