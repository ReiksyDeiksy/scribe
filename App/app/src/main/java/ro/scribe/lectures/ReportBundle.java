package ro.scribe.lectures;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Creates self-contained reports without exporting the database or private source paths. */
final class ReportBundle {
  static final long MAX_BYTES = 256L * 1024 * 1024;
  static final String[] PROBLEMS = {
    "wrong_text", "missing_speech", "slow", "playback", "crash", "other"
  };

  record Request(
      String lecture,
      String problem,
      String note,
      String expected,
      boolean audio,
      boolean transcript,
      boolean whole,
      long start,
      long end) {}

  record Result(File file, JSONObject manifest) {}

  private ReportBundle() {}

  static File readyDirectory(Context context) throws IOException {
    File dir = new File(context.getCacheDir(), "reports/ready");
    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("report_failed");
    return dir;
  }

  static File resolve(Context context, String name) throws IOException {
    if (name == null || !name.matches("scribe-report-[a-f0-9-]{36}\\.zip"))
      throw new FileNotFoundException();
    File file = new File(readyDirectory(context), name).getCanonicalFile();
    if (!file.getParentFile().equals(readyDirectory(context).getCanonicalFile()) || !file.isFile())
      throw new FileNotFoundException();
    return file;
  }

  static Result read(Context context, String name) throws Exception {
    File file = resolve(context, name);
    try (ZipFile zip = new ZipFile(file)) {
      ZipEntry entry = zip.getEntry("report.json");
      if (entry == null || entry.getSize() > 4 * 1024 * 1024)
        throw new IOException("report_failed");
      try (InputStream in = zip.getInputStream(entry)) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int count;
        while ((count = in.read(block)) != -1) {
          if (buffer.size() + count > 4 * 1024 * 1024) throw new IOException("report_failed");
          buffer.write(block, 0, count);
        }
        byte[] bytes = buffer.toByteArray();
        return new Result(file, new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
      }
    }
  }

  static Intent shareIntent(Context context, File file) throws IOException {
    File checked = resolve(context, file.getName());
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".reports", checked);
    Intent send =
        new Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    send.setClipData(ClipData.newRawUri("scribe report", uri));
    return send;
  }

  static Result create(Context context, Request request) throws Exception {
    if (!List.of(PROBLEMS).contains(request.problem())
        || request.note() == null
        || request.expected() == null
        || request.note().length() > 8000
        || request.expected().length() > 16000) throw new IOException("report_failed");
    Store store = Store.get(context);
    checkIdle(request.lecture());
    Store.Lecture lecture = store.lecture(request.lecture());
    if (lecture == null) throw new IOException("report_failed");
    long start = request.whole() ? 0 : request.start();
    long end = request.whole() ? Long.MAX_VALUE : request.end();
    if (start < 0 || end <= start || (!request.whole() && end > 86400000))
      throw new IOException("report_range_invalid");
    List<Store.Chunk> chunks = store.chunks(lecture.id());
    JSONArray events = store.reportDiagnostics(lecture.id(), start, end);
    JSONObject text = request.transcript() ? store.reportText(lecture.id(), start, end) : null;
    JSONArray audio = new JSONArray(), missing = new JSONArray();
    String id = UUID.randomUUID().toString();
    File ready = readyDirectory(context);
    long expiry = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
    File[] old = ready.listFiles();
    if (old != null)
      for (File file : old)
        if (file.getName().matches("scribe-report-[a-f0-9-]{36}\\.zip")
            && file.lastModified() < expiry) file.delete();
    File root = ready.getParentFile();
    File[] abandoned = root.listFiles();
    long unfinishedExpiry = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
    if (abandoned != null)
      for (File directory : abandoned) {
        if (directory.getName().matches("[a-f0-9-]{36}")
            && directory.isDirectory()
            && directory.lastModified() < unfinishedExpiry
            && directory.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())) {
          new File(directory, "report.partial").delete();
          new File(directory, "excerpt.wav").delete();
          directory.delete();
        }
      }
    File work = new File(context.getCacheDir(), "reports/" + id);
    if (!work.mkdir()) throw new IOException("report_failed");
    File partial = new File(work, "report.partial");
    File output = new File(ready, "scribe-report-" + id + ".zip");
    boolean complete = false;
    try {
      JSONObject manifest =
          Diagnostics.object(
              "schema_version",
              1,
              "report_id",
              id,
              "created_unix_ms",
              System.currentTimeMillis(),
              "problem",
              request.problem(),
              "note",
              request.note(),
              "expected_text",
              request.expected(),
              "app_at_export",
              Diagnostics.build(context),
              "recognition_defaults_at_export",
              Diagnostics.recognition(
                  context
                      .getSharedPreferences("settings", 0)
                      .getString("recognition_engine", LectureModel.ENGINE)),
              "device_at_export",
              Diagnostics.sample(context),
              "lecture",
              Diagnostics.object(
                  "id",
                  lecture.id(),
                  "title",
                  lecture.title(),
                  "language",
                  lecture.language(),
                  "detected_language",
                  store.detectedLanguage(lecture.id()),
                  "recognition_engine",
                  store.recognitionEngine(lecture.id()),
                  "review_required",
                  store.needsReview(lecture.id()),
                  "status",
                  lecture.status(),
                  "error",
                  lecture.error(),
                  "duration_ms",
                  lecture.duration(),
                  "automatic_audio_deletion",
                  lecture.deleteAudio()),
              "selection",
              Diagnostics.object(
                  "whole_lecture",
                  request.whole(),
                  "start_ms",
                  start,
                  "end_ms",
                  request.whole() ? JSONObject.NULL : end),
              "audio_requested",
              request.audio(),
              "transcript_included",
              request.transcript(),
              "audio",
              audio,
              "missing_audio",
              missing,
              "diagnostic_events",
              events.length(),
              "diagnostic_limits",
              "Up to 2048 events per lecture, 8192 total. Older builds did not collect timings."
                  + " Export-time settings are not historical measurements.");
      try (ZipOutputStream zip = new ZipOutputStream(new LimitedOutput(context, partial))) {
        zip.setLevel(Deflater.NO_COMPRESSION);
        if (request.audio()) {
          for (Store.Chunk chunk : chunks) {
            checkIdle(lecture.id());
            if (!request.whole()
                && (chunk.start() >= end
                    || (chunk.duration() > 0 && chunk.start() + chunk.duration() <= start)))
              continue;
            if ("writing".equals(chunk.state())) throw new IOException("report_busy");
            File excerpt = null;
            try {
              String name;
              long from = Math.max(start, chunk.start());
              long to =
                  request.whole()
                      ? chunk.start() + chunk.duration()
                      : chunk.duration() > 0
                          ? Math.min(end, chunk.start() + chunk.duration())
                          : end;
              InputStream source;
              if (request.whole()) {
                name =
                    String.format(
                        Locale.ROOT,
                        "audio/section-%05d.%s",
                        chunk.number(),
                        extension(context, chunk));
                source = source(context, store, chunk);
              } else {
                // Check ownership before the decoder opens any filesystem path.
                try (InputStream checked = source(context, store, chunk)) {
                  checked.available();
                }
                excerpt = new File(work, "excerpt.wav");
                Excerpt cut = excerpt(context, chunk, from, to, excerpt);
                if (cut.frames() == 0) throw new FileNotFoundException();
                from = cut.start();
                to = cut.end();
                source = new FileInputStream(excerpt);
                name = String.format(Locale.ROOT, "audio/section-%05d.wav", chunk.number());
              }
              try (InputStream in = source) {
                JSONObject attachment = entry(context, zip, name, in);
                attachment
                    .put("start_ms", from)
                    .put("end_ms", request.whole() && chunk.duration() == 0 ? JSONObject.NULL : to)
                    .put("original_file", request.whole());
                audio.put(attachment);
              }
            } catch (FileNotFoundException | SecurityException unavailable) {
              missing.put(
                  Diagnostics.object("start_ms", chunk.start(), "duration_ms", chunk.duration()));
            } finally {
              if (excerpt != null) excerpt.delete();
            }
          }
        }
        entry(context, zip, "diagnostics.json", events.toString(2));
        if (text != null) {
          entry(context, zip, "transcript.json", text.toString(2));
          StringBuilder readable = new StringBuilder();
          JSONArray passages = text.getJSONArray("passages");
          for (int i = 0; i < passages.length(); i++) {
            JSONObject passage = passages.getJSONObject(i);
            readable
                .append('[')
                .append(Core.clock(passage.getLong("start_ms")))
                .append("] ")
                .append(passage.getString("text"))
                .append("\n\n");
          }
          entry(context, zip, "transcript.txt", readable.toString());
        }
        entry(context, zip, "report.json", manifest.toString(2));
        entry(
            context,
            zip,
            "README.txt",
            "scribe problem report\n\nProblem: "
                + request.problem()
                + "\nNotes: "
                + request.note()
                + "\nExpected words: "
                + request.expected()
                + "\n\n"
                + "report.json contains the selected scope, attachment hashes, app/device"
                + " information and settings.\n"
                + "diagnostics.json contains bounded event and timing records, without audio or"
                + " transcript text.\n"
                + "Optional transcript.json includes original recognition text where available and"
                + " current edited text.\n"
                + "Passages overlapping the selected range are included in full.\n"
                + "Optional audio/ contains only selected audio. Excerpts are decoded to mono 16"
                + " kHz PCM WAV; full-lecture attachments retain original encoded files.\n"
                + "Audio timestamps in report.json refer to lecture time. Missing audio is listed"
                + " explicitly.\n"
                + "Older recordings have no historical timing data or preserved original"
                + " recognition text.\n"
                + "This archive was prepared locally. Creating it does not send it.\n");
      }
      checkIdle(lecture.id());
      if (!partial.renameTo(output)) throw new IOException("report_failed");
      complete = true;
      return new Result(output, manifest);
    } finally {
      partial.delete();
      File[] temporary = work.listFiles();
      if (temporary != null) for (File file : temporary) file.delete();
      work.delete();
      if (!complete) output.delete();
    }
  }

  private static void checkIdle(String lecture) throws IOException {
    if (Thread.currentThread().isInterrupted()) throw new IOException("report_cancelled");
    if (EngineService.capture != null || lecture.equals(EngineService.activeLecture))
      throw new IOException("report_busy");
  }

  private static InputStream source(Context context, Store store, Store.Chunk chunk)
      throws IOException {
    if (chunk.imported() && chunk.path().startsWith("content://")) {
      InputStream in = context.getContentResolver().openInputStream(Uri.parse(chunk.path()));
      if (in == null) throw new FileNotFoundException();
      return in;
    }
    File file = new File(chunk.path()).getCanonicalFile();
    if (!chunk.imported()
        && !file.getParentFile()
            .equals(store.directory(context, chunk.lecture()).getCanonicalFile()))
      throw new IOException("report_failed");
    return new FileInputStream(file);
  }

  private static String extension(Context context, Store.Chunk chunk) {
    if (!chunk.imported()) return "m4a";
    String mime = context.getContentResolver().getType(Uri.parse(chunk.path()));
    if (mime != null) {
      String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
      if (extension != null && extension.matches("[a-z0-9]{1,8}")) return extension;
    }
    String path = chunk.path().toLowerCase(Locale.ROOT);
    for (String extension :
        List.of("wav", "mp3", "m4a", "aac", "flac", "ogg", "opus", "webm", "mp4"))
      if (path.endsWith("." + extension)) return extension;
    return "audio";
  }

  private static class Finished extends Exception {}

  private record Excerpt(long frames, long start, long end) {}

  private static Excerpt excerpt(Context context, Store.Chunk chunk, long from, long to, File file)
      throws Exception {
    long localStart = Math.max(0, from - chunk.start()), localEnd = to - chunk.start();
    long[] frames = {0}, until = {localStart}, first = {-1};
    try (RandomAccessFile wav = new RandomAccessFile(file, "rw")) {
      wav.setLength(0);
      wav.write(new byte[44]);
      try {
        AudioDecoder.decode(
            context,
            chunk.path(),
            localStart,
            () -> Thread.currentThread().isInterrupted(),
            (start, samples) -> {
              checkIdle(chunk.lecture());
              if (StorageGuard.available(context) < Core.RESERVE_BYTES)
                throw new IOException("storage_low");
              int a =
                  (int)
                      Math.min(
                          samples.length,
                          Math.max(0, (Math.max(localStart, until[0]) - start) * 16));
              int b = (int) Math.min(samples.length, Math.max(0, (localEnd - start) * 16));
              byte[] pcm = new byte[Math.max(0, b - a) * 2];
              for (int i = a; i < b; i++) {
                short sample =
                    (short) Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32768)));
                pcm[(i - a) * 2] = (byte) sample;
                pcm[(i - a) * 2 + 1] = (byte) (sample >> 8);
              }
              if (wav.length() + pcm.length > MAX_BYTES) throw new IOException("report_too_large");
              wav.write(pcm);
              frames[0] += pcm.length / 2;
              if (pcm.length > 0) {
                if (first[0] < 0) first[0] = start + a / 16;
                until[0] = Math.max(until[0], start + b / 16);
              }
              if (start + samples.length / 16 >= localEnd) throw new Finished();
            });
      } catch (Finished done) {
        // Stop decoding as soon as the selected interval has been written.
      }
      int bytes = (int) (frames[0] * 2);
      wav.seek(0);
      wav.write("RIFF".getBytes(StandardCharsets.US_ASCII));
      wav.writeInt(Integer.reverseBytes(bytes + 36));
      wav.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
      wav.writeInt(Integer.reverseBytes(16));
      wav.writeShort(Short.reverseBytes((short) 1));
      wav.writeShort(Short.reverseBytes((short) 1));
      wav.writeInt(Integer.reverseBytes(Core.SAMPLE_RATE));
      wav.writeInt(Integer.reverseBytes(Core.SAMPLE_RATE * 2));
      wav.writeShort(Short.reverseBytes((short) 2));
      wav.writeShort(Short.reverseBytes((short) 16));
      wav.write("data".getBytes(StandardCharsets.US_ASCII));
      wav.writeInt(Integer.reverseBytes(bytes));
    }
    return new Excerpt(frames[0], chunk.start() + first[0], chunk.start() + until[0]);
  }

  private static JSONObject entry(Context context, ZipOutputStream zip, String name, InputStream in)
      throws Exception {
    zip.putNextEntry(new ZipEntry(name));
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] buffer = new byte[65536];
    long size = 0;
    int count;
    while ((count = in.read(buffer)) != -1) {
      if (Thread.currentThread().isInterrupted()) throw new IOException("report_cancelled");
      zip.write(buffer, 0, count);
      digest.update(buffer, 0, count);
      size += count;
    }
    zip.closeEntry();
    StringBuilder hash = new StringBuilder();
    for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
    return Diagnostics.object("file", name, "bytes", size, "sha256", hash.toString());
  }

  private static void entry(Context context, ZipOutputStream zip, String name, String text)
      throws Exception {
    entry(
        context,
        zip,
        name,
        new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  private static final class LimitedOutput extends FilterOutputStream {
    private final Context context;
    private long count, lastCheck;

    LimitedOutput(Context context, File file) throws IOException {
      super(new FileOutputStream(file));
      this.context = context;
    }

    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int start, int length) throws IOException {
      if (count + length > MAX_BYTES) throw new IOException("report_too_large");
      if (count == 0 || count - lastCheck >= 1024 * 1024) {
        if (StorageGuard.available(context) < Core.RESERVE_BYTES)
          throw new IOException("storage_low");
        lastCheck = count;
      }
      out.write(bytes, start, length);
      count += length;
    }
  }
}
