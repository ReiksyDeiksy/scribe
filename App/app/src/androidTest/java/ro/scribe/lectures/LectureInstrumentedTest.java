package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LectureInstrumentedTest {
  @Test(timeout = 180000)
  public void realSpeechHasWordTimesAndCancellationDoesNotReturnText() throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File audio = new File(context.getCacheDir(), "lecture-public-jfk.wav");
    try (var input =
        InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("jfk.wav")) {
      Files.copy(input, audio.toPath());
    }
    Whisper.resetCancel();
    try (LectureModel model = new LectureModel(context, () -> false)) {
      AudioDecoder.decode(
          context,
          audio.getAbsolutePath(),
          0,
          () -> false,
          (start, samples) -> {
            var result = model.recognize(samples, "auto", false);
            assertEquals("auto", result.language());
            assertEquals(-1, result.languageProbability(), 0);
            assertTrue(result.segments().toString().toLowerCase(Locale.ROOT).contains("country"));
            long end = 0;
            for (int i = 0; i < result.segments().length(); i++) {
              JSONObject word = result.segments().getJSONObject(i);
              assertTrue(word.getLong("start") >= end - 1);
              assertTrue(word.getLong("end") > word.getLong("start"));
              end = word.getLong("end");
              assertTrue(end <= samples.length / 16);
            }
            Whisper.cancel();
            try {
              model.recognize(samples, "auto", false);
              fail("Cancelled call returned text");
            } catch (java.io.IOException expected) {
              assertEquals("cancelled", expected.getMessage());
            } finally {
              Whisper.resetCancel();
            }
          });
    } finally {
      audio.delete();
      Whisper.resetCancel();
    }
  }

  @Test
  public void copyingAudioPreservesEditsMarksAndOriginalFilesAndRejectsMissingAudio()
      throws Exception {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Store store = Store.get(context);
    String original = store.createLecture(0, "ro", false, "done");
    String copy = null;
    File audio = new File(store.directory(context, original), "retained.m4a");
    byte[] bytes = {1, 2, 3, 4, 5};
    Files.write(audio.toPath(), bytes);
    long id = store.addChunk(original, 0, audio.getAbsolutePath(), 0, false);
    store.ready(id, 1000);
    store.saveWindow(
        store.chunks(original).get(0), 1000, List.of(new Store.Piece(0, 1000, "Original words")));
    store.edit(store.segments(original).get(0).id(), "My correction");
    store.mark(original, 500);
    try {
      copy = LectureCopy.create(context, original, LectureModel.ENGINE, "New copy", () -> false);
      assertEquals("My correction", store.segments(original).get(0).text());
      assertTrue(store.segments(copy).isEmpty());
      assertEquals(LectureModel.ENGINE, store.recognitionEngine(copy));
      assertTrue(store.needsReview(copy));
      assertFalse(store.lecture(copy).deleteAudio());
      assertEquals(500, store.marks(copy).get(0).time());
      assertArrayEquals(
          bytes, Files.readAllBytes(new File(store.chunks(copy).get(0).path()).toPath()));
      assertTrue(audio.isFile());
      assertEquals(0, store.chunks(copy).get(0).processed());
      audio.delete();
      int before = store.lectures(0, "").size();
      try {
        LectureCopy.create(context, original, LectureModel.ENGINE, "Missing", () -> false);
        fail();
      } catch (java.io.IOException expected) {
        assertEquals(before, store.lectures(0, "").size());
      }
    } finally {
      for (String lecture : new String[] {original, copy})
        if (lecture != null) {
          File dir = store.directory(context, lecture);
          for (File f : dir.listFiles()) f.delete();
          dir.delete();
          store.deleteLecture(lecture);
        }
    }
  }

  @Test
  public void tokenWordsKeepNumbersAndNegationsWithoutGuessingCorrections() throws Exception {
    JSONObject raw =
        new JSONObject(
            "{\"tokens\":[\" n\",\"esu\",\"purate\",\",\",\" 26\",\""
                + " zile\"],\"timestamps\":[0,0.1,0.2,0.3,1,2],\"durations\":[0.1,0.1,0.1,0.01,0.5,0.5]}");
    var words = LectureWords.segments(raw, 3000);
    assertEquals(3, words.length());
    assertEquals("nesupurate,", words.getJSONObject(0).getString("text"));
    assertEquals("26", words.getJSONObject(1).getString("text"));
    raw.getJSONArray("timestamps").put(0, -1);
    try {
      LectureWords.segments(raw, 3000);
      fail();
    } catch (java.io.IOException expected) {
    }
  }
}
