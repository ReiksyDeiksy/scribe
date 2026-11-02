package ro.scribe.lectures;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Copies retained sources on a worker; original audio, text and corrections are never changed. */
final class LectureCopy {
  static String create(
      Context context, String lecture, String engine, String title, BooleanSupplier cancelled)
      throws Exception {
    Store store = Store.get(context);
    Store.Lecture original = store.lecture(lecture);
    List<Store.Chunk> sources = store.chunks(lecture);
    if (original == null || sources.isEmpty() || lecture.equals(EngineService.activeLecture))
      throw new IOException("busy");
    File staging = new File(context.getCacheDir(), "lecture-copy-" + UUID.randomUUID());
    if (!staging.mkdir()) throw new IOException("storage_low");
    List<File> copies = new ArrayList<>();
    try {
      for (Store.Chunk source : sources) {
        if (cancelled.getAsBoolean()) throw new IOException("cancelled");
        File copy =
            new File(
                staging,
                String.format(java.util.Locale.ROOT, "section-%05d.audio", source.number()));
        try (InputStream in =
                source.path().startsWith("content://")
                    ? context.getContentResolver().openInputStream(Uri.parse(source.path()))
                    : new FileInputStream(source.path());
            FileOutputStream out = new FileOutputStream(copy)) {
          if (in == null) throw new IOException("audio_missing");
          byte[] buffer = new byte[65536];
          int count;
          long checked = 0;
          while ((count = in.read(buffer)) != -1) {
            if (cancelled.getAsBoolean()) throw new IOException("cancelled");
            if (checked <= 0) {
              if (StorageGuard.available(context) < Core.RESERVE_BYTES + 1024 * 1024)
                throw new IOException("storage_low");
              checked = 1024 * 1024;
            }
            out.write(buffer, 0, count);
            checked -= count;
          }
          out.getFD().sync();
        }
        if (copy.length() == 0) throw new IOException("audio_missing");
        copies.add(copy);
      }
      if (cancelled.getAsBoolean()) throw new IOException("cancelled");
      return store.commitAudioCopy(context, original, sources, copies, engine, title);
    } finally {
      for (File file : staging.listFiles() == null ? new File[0] : staging.listFiles())
        file.delete();
      staging.delete();
    }
  }

  private LectureCopy() {}
}
