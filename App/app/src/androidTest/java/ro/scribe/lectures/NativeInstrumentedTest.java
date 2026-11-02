package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NativeInstrumentedTest {
  @Test(timeout = 240000)
  public void actualSpeechDecodesAndTranscribesOnAndroid() throws Exception {
    Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Context tests = InstrumentationRegistry.getInstrumentation().getContext();
    File audio = new File(target.getCacheDir(), "public-jfk-test.wav");
    try (InputStream in = tests.getAssets().open("jfk.wav");
        FileOutputStream out = new FileOutputStream(audio)) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
    }
    StringBuilder transcript = new StringBuilder();
    long[] frames = {0};
    Whisper.resetCancel();
    try (Whisper model = new Whisper(target)) {
      long end =
          AudioDecoder.decode(
              target,
              audio.getAbsolutePath(),
              0,
              () -> false,
              (start, samples) -> {
                frames[0] += samples.length;
                JSONArray result = model.transcribe(samples, "en", "");
                Whisper.Recognition automatic = model.recognize(samples, "auto", false);
                assertEquals("en", automatic.language());
                assertTrue(automatic.languageProbability() >= 0.5);
                assertTrue(Double.isFinite(automatic.averageLogProbability()));
                assertTrue(
                    automatic.segments().toString().toLowerCase(Locale.ROOT).contains("country"));
                for (int i = 0; i < result.length(); i++)
                  transcript.append(result.getJSONObject(i).getString("text"));
              });
      assertTrue(end > 10000);
      assertTrue(frames[0] > 160000);
      String text = transcript.toString().toLowerCase(Locale.ROOT);
      assertTrue(text, text.contains("country"));
      assertTrue(text, text.contains("ask"));
    } finally {
      audio.delete();
    }
  }
}
