package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StoreInstrumentedTest {
  private Context context;
  private Store store;
  private String name;

  @Before
  public void setup() {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    name = "test-" + UUID.randomUUID() + ".db";
    store = new Store(context, name);
  }

  @After
  public void clean() {
    store.close();
    context.deleteDatabase(name);
  }

  @Test
  public void unassignedLectureCanBeAssignedAfterRecordingAndSearchSurvivesEditing() {
    String lecture = store.createLecture(0, "ro", true, "queued");
    store.saveCourse(0, "Anatomie");
    long course = store.courses().get(0).id();
    store.assign(lecture, course);
    assertEquals(course, store.lecture(lecture).course());
    long id = store.addChunk(lecture, 0, "content://test/original", 0, true);
    Store.Chunk chunk = store.chunks(lecture).get(0);
    store.saveWindow(
        chunk, 30000, List.of(new Store.Piece(0, 18000, "Țesutul conjunctiv este important.")));
    assertEquals(1, store.lectures(course, "țesutul").size());
    long segment = store.segments(lecture).get(0).id();
    store.edit(segment, "Sistemul nervos central.");
    assertEquals(0, store.lectures(course, "țesutul").size());
    assertEquals(1, store.lectures(course, "nervos").size());
    assertEquals(1, store.lectures(0, "Anatomie").size());
    assertTrue(store.lectures(0, "\" OR 1=1 --").isEmpty());
  }

  @Test
  public void committedWindowsAreIdempotentAndRecoveryKeepsAudioAndCheckpoint() {
    try (var c = store.getReadableDatabase().rawQuery("PRAGMA synchronous", null)) {
      c.moveToFirst();
      assertEquals(2, c.getInt(0));
    }
    String lecture = store.createLecture(0, "en", false, "recording");
    long id = store.addChunk(lecture, 0, "/retained/audio.m4a", 0, false);
    store.ready(id, 60000);
    Store.Chunk chunk = store.chunks(lecture).get(0);
    List<Store.Piece> text = List.of(new Store.Piece(0, 20000, "The first section."));
    store.saveWindow(chunk, 30000, text);
    store.saveWindow(chunk, 30000, text);
    assertEquals(1, store.segmentCount(id));
    store.mark(lecture, 12000);
    assertTrue(store.segments(lecture).get(0).starred());
    store.chunkState(id, "processing");
    store.recover();
    Store.Chunk recovered = store.chunks(lecture).get(0);
    assertEquals("ready", recovered.state());
    assertEquals(30000, recovered.processed());
    assertEquals("/retained/audio.m4a", recovered.path());
    assertEquals("interrupted", store.lecture(lecture).status());
    store.deleteLecture(lecture);
    assertTrue(store.chunks(lecture).isEmpty());
    assertTrue(store.lectures(0, "first").isEmpty());
  }
}
