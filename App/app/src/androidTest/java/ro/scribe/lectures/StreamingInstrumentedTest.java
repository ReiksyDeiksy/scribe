package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StreamingInstrumentedTest {
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
  private final Store store = Store.get(context);

  private static void wav(File file, int milliseconds, int offset) throws Exception {
    int frames = milliseconds * 16, bytes = frames * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header
        .put("RIFF".getBytes())
        .putInt(36 + bytes)
        .put("WAVEfmt ".getBytes())
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(16000)
        .putInt(32000)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes())
        .putInt(bytes);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(header.array());
      ByteBuffer block = ByteBuffer.allocate(32000).order(ByteOrder.LITTLE_ENDIAN);
      for (int start = 0; start < frames; start += 16000) {
        block.clear();
        for (int i = start; i < Math.min(frames, start + 16000); i++)
          block.putShort((short) ((offset + i) % 1000));
        out.write(block.array(), 0, block.position());
      }
    }
  }

  private List<Store.Chunk> sources(String lecture) throws Exception {
    int start = 0, number = 0;
    for (int length : new int[] {60032, 60032, 31000}) {
      File file = new File(store.directory(context, lecture), "part-" + number + ".wav");
      wav(file, length, start * 16);
      long id = store.addChunk(lecture, number++, file.getAbsolutePath(), start, false);
      store.ready(id, length);
      start += length;
    }
    return store.chunks(lecture);
  }

  private void remove(String lecture) {
    for (Store.Chunk chunk : store.chunks(lecture)) new File(chunk.path()).delete();
    store.directory(context, lecture).delete();
    store.deleteLecture(lecture);
  }

  @Test
  public void streamsAllSamplesAcrossSourceFilesAndSplitsCheckpointsAtomically() throws Exception {
    String lecture = store.createLecture(0, "auto", true, "queued");
    try {
      List<Store.Chunk> sources = sources(lecture);
      List<Long> starts = new ArrayList<>();
      long[] last = {0};
      long end =
          LectureDecoder.decode(
              context,
              sources,
              0,
              () -> false,
              (start, samples) -> {
                starts.add(start);
                for (int i = 0; i < samples.length; i++)
                  assertEquals(((start * 16 + i) % 1000) / 32768f, samples[i], .000001f);
                long until = start + samples.length / 16;
                store.saveSequenceWindow(
                    sources,
                    until,
                    List.of(new Store.Piece(Math.max(last[0], start), until, "Speech section")));
                last[0] = until;
              });
      assertEquals(151064, end);
      assertEquals(List.of(0L, 28000L, 56000L, 84000L, 112000L, 140000L), starts);
      for (Store.Chunk source : store.chunks(lecture)) {
        assertEquals(source.duration(), source.processed());
        assertTrue(new File(source.path()).isFile());
      }
      assertTrue(store.overlappingSegments(store.chunks(lecture).get(1)) > 0);
    } finally {
      remove(lecture);
    }
  }

  @Test
  public void cancelledStreamResumesWithOverlapAndKeepsHumanCorrections() throws Exception {
    String lecture = store.createLecture(0, "auto", false, "queued");
    try {
      List<Store.Chunk> sources = sources(lecture);
      boolean[] stop = {false};
      try {
        LectureDecoder.decode(
            context,
            sources,
            0,
            () -> stop[0],
            (start, samples) -> {
              store.saveSequenceWindow(
                  sources, 30000, List.of(new Store.Piece(0, 30000, "Original words")));
              stop[0] = true;
            });
        fail("Cancellation must stop decoding");
      } catch (IOException expected) {
        assertEquals("cancelled", expected.getMessage());
      }
      long edited = store.segments(lecture).get(0).id();
      store.edit(edited, "My manual correction");
      store.recognitionState(lecture, "ro", true);
      long[] checkpoint = {store.chunks(lecture).get(0).processed()};
      assertEquals(30000, checkpoint[0]);
      LectureDecoder.decode(
          context,
          store.chunks(lecture),
          checkpoint[0],
          () -> false,
          (start, samples) -> {
            assertTrue(start >= 28000);
            long end = start + samples.length / 16;
            store.saveSequenceWindow(
                sources, end, List.of(new Store.Piece(checkpoint[0], end, "Later words")));
            checkpoint[0] = end;
          });
      assertEquals(
          "My manual correction",
          store.segments(lecture).stream()
              .filter(s -> s.id() == edited)
              .findFirst()
              .orElseThrow()
              .text());
      assertEquals("ro", store.detectedLanguage(lecture));
      assertTrue(store.needsReview(lecture));
      assertEquals(151064, checkpoint[0]);
    } finally {
      remove(lecture);
    }
  }
}
