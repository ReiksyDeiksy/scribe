package ro.scribe.lectures;

import static org.junit.Assert.*;

import org.junit.Test;

public class CoreTest {
  @Test
  public void punctuationAndMusicSymbolsAreNotSuccessfulTranscripts() {
    assertFalse(Core.hasWords("... — ♪"));
    assertFalse(Core.hasWords("\n  "));
    assertTrue(Core.hasWords("Țesutul conjunctiv"));
    assertTrue(Core.hasWords("你好"));
    assertTrue(Core.hasWords("2026"));
  }

  @Test
  public void deletionRequiresEverySafetyCondition() {
    for (String state :
        new String[] {"ready", "writing", "processing", "error", "cancelled", "done"})
      for (boolean enabled : new boolean[] {false, true})
        for (boolean committed : new boolean[] {false, true})
          for (boolean imported : new boolean[] {false, true})
            for (int count : new int[] {0, 1})
              assertEquals(
                  enabled && committed && !imported && count > 0 && state.equals("done"),
                  Core.mayDelete(enabled, state, count, committed, imported));
  }

  @Test
  public void overlappingWordsAreRemovedButShortRepetitionIsKept() {
    assertEquals(
        "and then continue.",
        Core.trimOverlap(
            "These are very important words.", "very important words and then continue."));
    assertEquals("yes yes", Core.trimOverlap("She said yes", "yes yes"));
    assertEquals(
        "urmează aici",
        Core.trimOverlap("Acesta este un exemplu important.", "un exemplu important urmează aici"));
  }

  @Test
  public void searchCannotInjectFtsSyntax() {
    assertEquals("\"anatomie\"* AND \"țesut\"*", Core.searchQuery("anatomie: \"țesut\"*"));
    assertEquals("", Core.searchQuery("\"*:-()"));
  }

  @Test
  public void lectureTimesDoNotWrapAtOneHour() {
    assertEquals("00:00", Core.clock(-1));
    assertEquals("37:12", Core.clock(2232000));
    assertEquals("2:01:03", Core.clock(7263000));
  }

  @Test
  public void resamplingPreservesTimingAcrossArbitraryInputBuffers() throws Exception {
    LinearResampler r = new LinearResampler(48000);
    int[] count = {0};
    float[] last = {0};
    for (int i = 0; i < 48000; i++)
      r.accept(
          i,
          v -> {
            count[0]++;
            last[0] = v;
          });
    assertEquals(16000, count[0]);
    assertEquals(47997, last[0], 0.01);
    LinearResampler fractional = new LinearResampler(44100);
    int[] n = {0};
    for (int i = 0; i < 44100; i++)
      fractional.accept(
          i,
          v -> {
            assertEquals(n[0] * 44100.0 / 16000, v, 0.02);
            n[0]++;
          });
    assertEquals(16000, n[0]);
  }
}
