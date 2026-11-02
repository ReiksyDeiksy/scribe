package ro.scribe.lectures;

import static org.junit.Assert.*;

import org.junit.Test;

public class SpeechQualityTest {
  @Test
  public void flagsRunawayTextWithoutRejectingOrdinaryRepetition() {
    assertTrue(SpeechQuality.repetitive("Intro. " + "se parte de lași, ".repeat(20)));
    assertTrue(SpeechQuality.repetitive("f".repeat(120)));
    assertFalse(SpeechQuality.repetitive("yes yes, that is very important. yes yes."));
    assertTrue(SpeechQuality.suspicious("", -10, 0, 30000, .02));
    assertTrue(SpeechQuality.suspicious("A short result", -.2, 5000, 30000, .02));
    assertFalse(SpeechQuality.suspicious("A complete clear sentence", -.2, 30000, 30000, .02));
    assertTrue(
        SpeechQuality.score("Clear complete speech", -.3, 29000, 30000)
            > SpeechQuality.score("word ".repeat(40), -.1, 30000, 30000));
  }
}
