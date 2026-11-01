package ro.scribe.lectures;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** A local report draft and an explicit preview before handing a ZIP to another app. */
public final class ReportActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private String lectureId;
  private List<Store.Segment> segments;
  private Spinner problem, scope, passage;
  private EditText note, expected, from, until;
  private CheckBox audio, transcript;
  private LinearLayout page, range;
  private Button prepare;
  private boolean busy;
  private ReportBundle.Result prepared;

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(ScribeApp.localized(base));
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    lectureId = getIntent().getStringExtra("lecture");
    Store.Lecture lecture = lectureId == null ? null : Store.get(this).lecture(lectureId);
    if (lecture == null) {
      finish();
      return;
    }
    segments = Store.get(this).segments(lectureId);
    form(lecture, state);
    if (state != null && state.getString("prepared") != null) {
      String file = state.getString("prepared");
      busy = true;
      worker.execute(
          () -> {
            try {
              ReportBundle.Result result = ReportBundle.read(getApplicationContext(), file);
              runOnUiThread(
                  () -> {
                    if (alive()) {
                      busy = false;
                      preview(result);
                    }
                  });
            } catch (Exception e) {
              runOnUiThread(
                  () -> {
                    if (alive()) {
                      busy = false;
                      toast(R.string.report_failed);
                    }
                  });
            }
          });
    }
  }

  private boolean alive() {
    return !isFinishing() && !isDestroyed();
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private void base() {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(20), dp(16), dp(20), dp(24));
    page.setBackgroundColor(getColor(R.color.scribe_background));
    scroll.addView(page);
    setContentView(scroll);
    if (Build.VERSION.SDK_INT >= 35)
      page.setOnApplyWindowInsetsListener(
          (v, insets) -> {
            var bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(
                dp(20) + bars.left, dp(16) + bars.top, dp(20) + bars.right, dp(24) + bars.bottom);
            return insets;
          });
    page.addView(button(R.string.report_back, false, this::finish));
  }

  private TextView text(String value, int size) {
    TextView v = new TextView(this);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(getColor(R.color.scribe_ink));
    v.setPadding(0, dp(8), 0, dp(8));
    return v;
  }

  private void label(int resource) {
    page.addView(text(getString(resource), 15));
  }

  private EditText input(int id, int hint, int lines, int limit) {
    EditText v = new EditText(this);
    v.setId(id);
    v.setHint(hint);
    v.setMinLines(lines);
    v.setTextSize(16);
    v.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)});
    v.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | (lines > 1 ? android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
    return v;
  }

  private Spinner spinner(int id, String[] values) {
    Spinner v = new Spinner(this);
    v.setId(id);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    v.setAdapter(adapter);
    v.setMinimumHeight(dp(48));
    return v;
  }

  private Button button(int resource, boolean primary, Runnable action) {
    Button v = new Button(this);
    v.setText(resource);
    v.setAllCaps(false);
    v.setTextSize(16);
    v.setTextColor(getColor(primary ? R.color.scribe_on_primary : R.color.scribe_action));
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(getColor(primary ? R.color.scribe_primary : R.color.scribe_surface));
    shape.setCornerRadius(dp(16));
    if (!primary) shape.setStroke(dp(1), getColor(R.color.scribe_outline));
    v.setBackgroundTintList(null);
    v.setBackground(shape);
    v.setPadding(dp(14), dp(12), dp(14), dp(12));
    v.setMinHeight(dp(48));
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(0, dp(6), 0, dp(6));
    v.setLayoutParams(params);
    v.setOnClickListener(ignored -> action.run());
    return v;
  }

  private CheckBox check(int id, int label) {
    CheckBox v = new CheckBox(this);
    v.setId(id);
    v.setText(label);
    v.setTextSize(16);
    v.setTextColor(getColor(R.color.scribe_ink));
    v.setMinHeight(dp(48));
    page.addView(v);
    return v;
  }

  private void form(Store.Lecture lecture, Bundle state) {
    base();
    TextView heading = text(getString(R.string.report_problem), 25);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    page.addView(heading);
    label(R.string.report_intro);
    label(R.string.report_kind);
    problem = spinner(R.id.report_kind, getResources().getStringArray(R.array.report_problems));
    page.addView(problem);
    label(R.string.report_scope);
    scope = spinner(R.id.report_scope, getResources().getStringArray(R.array.report_scopes));
    page.addView(scope);
    String[] passages =
        segments.isEmpty()
            ? new String[] {getString(R.string.report_no_passages)}
            : segments.stream()
                .map(
                    s ->
                        Core.clock(s.start())
                            + " · "
                            + s.text()
                                .substring(0, Math.min(s.text().length(), 90))
                                .replace('\n', ' '))
                .toArray(String[]::new);
    passage = spinner(R.id.report_passage, passages);
    page.addView(passage);
    range = new LinearLayout(this);
    range.setOrientation(LinearLayout.VERTICAL);
    range.addView(text(getString(R.string.report_range_hint), 14));
    from = input(R.id.report_from, R.string.report_from, 1, 12);
    until = input(R.id.report_until, R.string.report_until, 1, 12);
    from.setText(Core.clock(0));
    until.setText(Core.clock((lecture.duration() + 999) / 1000 * 1000));
    range.addView(from);
    range.addView(until);
    page.addView(range);
    audio = check(R.id.report_audio, R.string.report_include_audio);
    transcript = check(R.id.report_text, R.string.report_include_text);
    label(R.string.report_attachments_hint);
    label(R.string.report_notes);
    note = input(R.id.report_note, R.string.report_note_hint, 3, 8000);
    page.addView(note);
    label(R.string.report_expected);
    expected = input(R.id.report_expected, R.string.report_expected_hint, 3, 16000);
    page.addView(expected);
    label(R.string.report_privacy);
    prepare = button(R.string.report_prepare, true, this::prepare);
    prepare.setId(R.id.report_prepare);
    page.addView(prepare);
    scope.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            passage.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
            range.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    if (state != null) {
      problem.setSelection(state.getInt("problem"));
      scope.setSelection(state.getInt("scope"));
      passage.setSelection(Math.min(state.getInt("passage"), passages.length - 1));
      from.setText(state.getString("from", "00:00"));
      until.setText(state.getString("until", until.getText().toString()));
      note.setText(state.getString("note", ""));
      expected.setText(state.getString("expected", ""));
      audio.setChecked(state.getBoolean("audio"));
      transcript.setChecked(state.getBoolean("transcript"));
    }
  }

  private void prepare() {
    if (busy) return;
    long start = 0, end = Long.MAX_VALUE;
    int mode = scope.getSelectedItemPosition();
    try {
      if (mode == 1) {
        if (segments.isEmpty()) {
          toast(R.string.report_no_passages);
          return;
        }
        Store.Segment s = segments.get(passage.getSelectedItemPosition());
        start = s.start();
        end = s.end();
      } else if (mode == 2) {
        start = ReportRange.parse(from.getText().toString().trim());
        end = ReportRange.parse(until.getText().toString().trim());
        if (end <= start) throw new IllegalArgumentException();
      }
    } catch (IllegalArgumentException e) {
      toast(R.string.report_range_invalid);
      return;
    }
    ReportBundle.Request request =
        new ReportBundle.Request(
            lectureId,
            ReportBundle.PROBLEMS[problem.getSelectedItemPosition()],
            note.getText().toString(),
            expected.getText().toString(),
            audio.isChecked(),
            transcript.isChecked(),
            mode == 0,
            start,
            end);
    busy = true;
    prepare.setEnabled(false);
    prepare.setText(R.string.report_preparing);
    worker.execute(
        () -> {
          try {
            ReportBundle.Result result = ReportBundle.create(getApplicationContext(), request);
            runOnUiThread(
                () -> {
                  if (alive()) {
                    busy = false;
                    preview(result);
                  }
                });
          } catch (Exception e) {
            int message =
                switch (e.getMessage() == null ? "" : e.getMessage()) {
                  case "report_busy" -> R.string.report_busy;
                  case "report_too_large" -> R.string.report_too_large;
                  case "storage_low" -> R.string.storage_low;
                  default -> R.string.report_failed;
                };
            runOnUiThread(
                () -> {
                  if (alive()) {
                    busy = false;
                    prepare.setEnabled(true);
                    prepare.setText(R.string.report_prepare);
                    toast(message);
                  }
                });
          }
        });
  }

  private void preview(ReportBundle.Result result) {
    prepared = result;
    base();
    page.addView(text(getString(R.string.report_ready), 25));
    JSONObject m = result.manifest();
    JSONObject selection = m.optJSONObject("selection");
    String selected =
        selection.optBoolean("whole_lecture")
            ? getString(R.string.report_whole)
            : Core.clock(selection.optLong("start_ms"))
                + " – "
                + Core.clock(selection.optLong("end_ms"));
    page.addView(
        text(
            getString(
                R.string.report_preview,
                selected,
                android.text.format.Formatter.formatShortFileSize(this, result.file().length()),
                m.optJSONArray("audio").length(),
                getString(
                    m.optBoolean("transcript_included") ? R.string.report_yes : R.string.report_no),
                m.optInt("diagnostic_events")),
            16));
    label(R.string.report_preview_details);
    if (m.optBoolean("transcript_included")) label(R.string.report_text_scope);
    if (m.optBoolean("audio_requested")
        && (m.optJSONArray("audio").length() == 0 || m.optJSONArray("missing_audio").length() > 0))
      label(R.string.report_missing_audio);
    page.addView(text(getString(R.string.report_notes) + "\n" + m.optString("note"), 16));
    page.addView(
        text(getString(R.string.report_expected) + "\n" + m.optString("expected_text"), 16));
    label(R.string.report_share_hint);
    Button share =
        button(
            R.string.report_share,
            true,
            () -> {
              try {
                Intent send = ReportBundle.shareIntent(this, prepared.file());
                Intent chooser = Intent.createChooser(send, getString(R.string.report_share));
                chooser.setClipData(send.getClipData());
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(chooser);
              } catch (Exception e) {
                toast(R.string.report_share_failed);
              }
            });
    share.setId(R.id.report_share);
    page.addView(share);
    page.addView(
        button(
            R.string.report_save,
            false,
            () -> {
              if (busy) return;
              try {
                startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, prepared.file().getName()),
                    41);
              } catch (RuntimeException e) {
                toast(R.string.report_share_failed);
              }
            }));
    page.addView(
        button(
            R.string.report_edit,
            false,
            () -> {
              if (busy) return;
              Bundle draft = new Bundle();
              saveDraft(draft);
              prepared = null;
              form(Store.get(this).lecture(lectureId), draft);
            }));
  }

  private void saveDraft(Bundle state) {
    if (problem == null) return;
    state.putInt("problem", problem.getSelectedItemPosition());
    state.putInt("scope", scope.getSelectedItemPosition());
    state.putInt("passage", passage.getSelectedItemPosition());
    state.putString("from", from.getText().toString());
    state.putString("until", until.getText().toString());
    state.putString("note", note.getText().toString());
    state.putString("expected", expected.getText().toString());
    state.putBoolean("audio", audio.isChecked());
    state.putBoolean("transcript", transcript.isChecked());
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    saveDraft(state);
    if (prepared != null) state.putString("prepared", prepared.file().getName());
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 41
        || result != RESULT_OK
        || data == null
        || data.getData() == null
        || prepared == null) return;
    var uri = data.getData();
    var file = prepared.file();
    busy = true;
    worker.execute(
        () -> {
          int message = R.string.report_saved;
          try (var in = new FileInputStream(file);
              OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new java.io.IOException();
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) {
              if (Thread.currentThread().isInterrupted()) throw new java.io.IOException();
              out.write(buffer, 0, count);
            }
          } catch (Exception e) {
            message = R.string.report_save_failed;
          }
          int resultMessage = message;
          runOnUiThread(
              () -> {
                if (alive()) {
                  busy = false;
                  toast(resultMessage);
                }
              });
        });
  }

  private void toast(int resource) {
    Toast.makeText(this, resource, Toast.LENGTH_LONG).show();
  }

  @Override
  protected void onDestroy() {
    worker.shutdownNow();
    super.onDestroy();
  }
}
