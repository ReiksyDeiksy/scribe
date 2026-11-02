package ro.scribe.lectures;

/** Shared envelope keeps the existing Whisper API available to compatibility callers. */
interface SpeechModel extends AutoCloseable {
  Whisper.Recognition recognize(float[] samples, String language, boolean beam) throws Exception;

  @Override
  void close();
}
