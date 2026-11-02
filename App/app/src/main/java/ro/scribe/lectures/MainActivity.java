package ro.scribe.lectures;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
  private int backgroundColor,
      surfaceColor,
      primaryColor,
      inkColor,
      mutedColor,
      actionColor,
      outlineColor,
      surfaceTintColor,
      highlightColor,
      primaryInkColor;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService files = Executors.newSingleThreadExecutor();
  private Store store;
  private SharedPreferences prefs;
  private LinearLayout root, content;
  private ListView list;
  private TextView recordingState, recordingClock, detailState, detailProgress;
  private Button pause;
  private ProgressBar meter;
  private ProgressBar transcriptionProgress, workingIndicator;
  private TextView workState, reviewNotice, detailLanguage;
  private static final String[] AUDIO_CODES = {
    "auto", "ro", "en", "de", "fr", "es", "it", "uk", "tr"
  };
  private String screen = "library", lectureId, query = "", signature = "", exportLecture;
  private String detailMode = "";
  private long courseFilter;
  private long dayFrom, dayUntil = Long.MAX_VALUE;
  private boolean hadBackgroundWork;
  private List<Store.Lecture> lectures = new ArrayList<>();
  private List<Store.Segment> segments = new ArrayList<>();
  private final Set<Long> starred = new HashSet<>();
  private MediaPlayer player;
  private String playingPath;
  private long highlightId;
  private AlertDialog patchDialog;
  private int patchVersion;
  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          refresh();
          handler.postDelayed(this, 1200);
        }
      };

  @Override
  protected void attachBaseContext(Context base) {
    super.attachBaseContext(ScribeApp.localized(base));
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    backgroundColor = getColor(R.color.scribe_background);
    surfaceColor = getColor(R.color.scribe_surface);
    primaryColor = getColor(R.color.scribe_primary);
    inkColor = getColor(R.color.scribe_ink);
    mutedColor = getColor(R.color.scribe_muted);
    actionColor = getColor(R.color.scribe_action);
    outlineColor = getColor(R.color.scribe_outline);
    surfaceTintColor = getColor(R.color.scribe_surface_tint);
    highlightColor = getColor(R.color.scribe_highlight);
    primaryInkColor = getColor(R.color.scribe_on_primary);
    store = Store.get(this);
    prefs = getSharedPreferences("settings", 0);
    if (saved != null) {
      lectureId = saved.getString("lecture");
      screen = saved.getString("screen", "library");
      courseFilter = saved.getLong("course");
      patchVersion = saved.getInt("patch_version");
    } else if (EngineService.activeLecture != null) {
      lectureId = EngineService.activeLecture;
      screen = "detail";
    }
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(tick);
    PatchNotes.Release restored = PatchNotes.find(patchVersion);
    if (restored != null) showPatchNote(restored);
    else if (prefs.getInt(PatchNotes.SEEN_VERSION, 0) < PatchNotes.latest().code()
        && EngineService.activeLecture == null
        && EngineService.capture == null
        && !EngineService.processing) showPatchNote(PatchNotes.latest());
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("lecture", lectureId);
    state.putString("screen", screen);
    state.putLong("course", courseFilter);
    if (patchDialog != null && patchDialog.isShowing()) state.putInt("patch_version", patchVersion);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    if (patchDialog != null) patchDialog.dismiss();
    stopPlayback();
    files.shutdown();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (screen.equals("help") || screen.equals("patch_notes")) {
      screen = "settings";
      render();
    } else if (!screen.equals("library")) {
      screen = "library";
      render();
    } else super.onBackPressed();
  }

  private int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.HORIZONTAL);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setPadding(0, dp(5), 0, dp(5));
    return t;
  }

  private TextView text(int resource, int size, int color) {
    return text(getString(resource), size, color);
  }

  private Button button(int label, boolean primary, Runnable action) {
    return button(getString(label), primary, action);
  }

  private Button button(String label, boolean primary, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTextColor(primary ? primaryInkColor : actionColor);
    b.setTypeface(Typeface.DEFAULT, primary ? Typeface.BOLD : Typeface.NORMAL);
    GradientDrawable background = shape(primary ? primaryColor : surfaceColor, 16);
    if (!primary) background.setStroke(dp(1), outlineColor);
    b.setBackgroundTintList(null);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(getColor(R.color.scribe_ripple)), background, null));
    b.setPadding(dp(16), dp(8), dp(16), dp(8));
    b.setMinHeight(dp(48));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(0, dp(5), 0, dp(5));
    b.setLayoutParams(p);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  private void two(LinearLayout parent, View a, View b) {
    LinearLayout r = row();
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(3), dp(4), dp(3), dp(4));
    r.addView(a, new LinearLayout.LayoutParams(p));
    r.addView(b, new LinearLayout.LayoutParams(p));
    parent.addView(r);
  }

  private LinearLayout card() {
    LinearLayout v = column();
    v.setPadding(dp(18), dp(12), dp(18), dp(12));
    GradientDrawable background = shape(surfaceColor, 20);
    background.setStroke(dp(1), outlineColor);
    v.setBackground(background);
    return v;
  }

  private void note(LinearLayout parent, int id) {
    parent.addView(text(id, 14, mutedColor));
  }

  private void render() {
    signature = "";
    recordingState = null;
    recordingClock = null;
    detailState = null;
    detailProgress = null;
    transcriptionProgress = null;
    workingIndicator = null;
    workState = null;
    reviewNotice = null;
    detailLanguage = null;
    meter = null;
    pause = null;
    root = column();
    root.setBackgroundColor(backgroundColor);
    root.setPadding(dp(20), dp(14), dp(20), dp(12));
    setContentView(root);
    if (Build.VERSION.SDK_INT >= 35)
      root.setOnApplyWindowInsetsListener(
          (view, insets) -> {
            android.graphics.Insets bars =
                insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(
                dp(20) + bars.left, dp(14) + bars.top, dp(20) + bars.right, dp(12) + bars.bottom);
            return insets;
          });
    LinearLayout heading = row();
    heading.setPadding(0, 0, 0, dp(10));
    ImageView brand = new ImageView(this);
    brand.setImageResource(R.drawable.ic_scribe);
    brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams brandSize = new LinearLayout.LayoutParams(dp(48), dp(48));
    brandSize.setMarginEnd(dp(12));
    heading.addView(brand, brandSize);
    TextView wordmark = text("scribe", 30, inkColor);
    wordmark.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    heading.addView(wordmark, new LinearLayout.LayoutParams(0, -2, 1));
    TextView caption = text(R.string.brand_caption, 11, actionColor);
    caption.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    caption.setLetterSpacing(0.08f);
    caption.setPadding(dp(10), dp(7), dp(10), dp(7));
    caption.setBackground(shape(surfaceTintColor, 10));
    heading.addView(caption);
    root.addView(heading);
    content = column();
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    if (screen.equals("detail") && store.lecture(lectureId) != null) detail();
    else if (screen.equals("courses")) courses();
    else if (screen.equals("settings")) settings();
    else if (screen.equals("help")) help();
    else if (screen.equals("patch_notes")) patchNotes();
    else {
      screen = "library";
      library();
    }
    LinearLayout nav = row();
    nav.setPadding(0, dp(8), 0, 0);
    for (String tab : new String[] {"library", "courses", "settings"}) {
      boolean selected =
          screen.equals(tab)
              || ((screen.equals("help") || screen.equals("patch_notes"))
                  && tab.equals("settings"));
      int label =
          tab.equals("library")
              ? R.string.library
              : tab.equals("courses") ? R.string.courses : R.string.settings;
      Button b =
          button(
              label,
              selected,
              () -> {
                screen = tab;
                render();
              });
      b.setTextSize(12);
      b.setPadding(dp(6), dp(8), dp(6), dp(8));
      int icon =
          tab.equals("library")
              ? R.drawable.ic_library
              : tab.equals("courses") ? R.drawable.ic_courses : R.drawable.ic_settings;
      b.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
      b.setCompoundDrawableTintList(
          ColorStateList.valueOf(selected ? primaryInkColor : getColor(R.color.scribe_steel_blue)));
      b.setCompoundDrawablePadding(dp(3));
      LinearLayout.LayoutParams tabSize = new LinearLayout.LayoutParams(0, dp(64), 1);
      tabSize.setMargins(dp(3), 0, dp(3), 0);
      nav.addView(b, tabSize);
    }
    root.addView(nav);
    refresh();
  }

  private void library() {
    note(content, R.string.tagline);
    content.addView(
        button(
            getString(
                R.string.lecture_language_value,
                audioLanguageName(prefs.getString("audio_language", "auto"))),
            false,
            this::chooseAudioLanguage));
    EditText search = new EditText(this);
    search.setSingleLine();
    search.setTextColor(inkColor);
    search.setHintTextColor(mutedColor);
    search.setTextSize(15);
    search.setHint(R.string.search);
    search.setText(query);
    GradientDrawable searchBackground = shape(surfaceColor, 16);
    searchBackground.setStroke(dp(1), outlineColor);
    search.setBackground(searchBackground);
    search.setPadding(dp(14), dp(12), dp(14), dp(12));
    search.setMinHeight(dp(52));
    search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
    search.setCompoundDrawableTintList(ColorStateList.valueOf(getColor(R.color.scribe_cadet_blue)));
    search.setCompoundDrawablePadding(dp(10));
    content.addView(search);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
            refreshLibrary();
          }

          public void afterTextChanged(Editable e) {}
        });
    List<Store.Course> available = store.courses();
    List<String> names = new ArrayList<>();
    names.add(getString(R.string.all_courses));
    int selected = 0;
    for (int i = 0; i < available.size(); i++) {
      names.add(available.get(i).name());
      if (available.get(i).id() == courseFilter) selected = i + 1;
    }
    Spinner filter = spinner(names);
    filter.setSelection(selected);
    content.addView(filter);
    filter.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
            courseFilter = position == 0 ? 0 : available.get(position - 1).id();
            refreshLibrary();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    content.addView(
        button(
            dayFrom == 0 ? getString(R.string.all_dates) : date(dayFrom, "dd MMM yyyy"),
            false,
            this::chooseDate));
    list = new ListView(this);
    list.setDivider(null);
    list.setClipToPadding(false);
    content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView empty =
        text(
            getString(R.string.no_lectures) + "\n\n" + getString(R.string.no_lectures_hint),
            18,
            mutedColor);
    empty.setGravity(Gravity.CENTER);
    content.addView(empty);
    list.setEmptyView(empty);
    list.setOnItemClickListener((p, v, index, id) -> openLecture(lectures.get(index).id()));
    Button recordButton = button(R.string.new_recording, true, this::record);
    recordButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0);
    recordButton.setCompoundDrawableTintList(ColorStateList.valueOf(primaryInkColor));
    content.addView(recordButton);
    content.addView(
        button(
            R.string.import_audio,
            false,
            () -> {
              Intent i =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("audio/*")
                      .addCategory(Intent.CATEGORY_OPENABLE);
              i.addFlags(
                  Intent.FLAG_GRANT_READ_URI_PERMISSION
                      | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
              startActivityForResult(i, 20);
            }));
    refreshLibrary();
  }

  private void refreshLibrary() {
    if (!screen.equals("library") || list == null) return;
    List<Store.Lecture> updated = store.lectures(courseFilter, query, dayFrom, dayUntil);
    if (list.getAdapter() != null && updated.equals(lectures)) return;
    int first = list.getFirstVisiblePosition();
    View firstView = list.getChildAt(0);
    int offset = firstView == null ? 0 : firstView.getTop();
    lectures = updated;
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return lectures.size();
          }

          public Object getItem(int p) {
            return lectures.get(p);
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int position, View convert, ViewGroup parent) {
            Store.Lecture l = lectures.get(position);
            LinearLayout wrapper = column();
            wrapper.setPadding(0, dp(5), 0, dp(5));
            LinearLayout c = card();
            TextView name = text(title(l), 19, inkColor);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            c.addView(name);
            c.addView(
                text(
                    courseName(l.course()) + "  ·  " + date(l.created(), "dd MMM · HH:mm"),
                    13,
                    mutedColor));
            c.addView(
                text(status(l.status()) + "  ·  " + Core.clock(l.duration()), 13, actionColor));
            wrapper.addView(c);
            return wrapper;
          }
        });
    list.setSelectionFromTop(first, offset);
  }

  private void courses() {
    note(content, R.string.course_hint);
    content.addView(button(R.string.add_course, true, () -> editCourse(0, "")));
    ListView courseList = new ListView(this);
    courseList.setDivider(null);
    List<Store.Course> all = store.courses();
    courseList.setAdapter(
        new ArrayAdapter<>(
            this,
            android.R.layout.simple_list_item_1,
            all.stream().map(Store.Course::name).toArray(String[]::new)));
    courseList.setOnItemClickListener(
        (p, v, n, id) -> {
          courseFilter = all.get(n).id();
          screen = "library";
          render();
        });
    courseList.setOnItemLongClickListener(
        (p, v, n, id) -> {
          editCourse(all.get(n).id(), all.get(n).name());
          return true;
        });
    content.addView(courseList, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  private void editCourse(long id, String name) {
    EditText input = edit(name);
    new AlertDialog.Builder(this)
        .setTitle(id == 0 ? R.string.add_course : R.string.rename_course)
        .setView(input)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(
            R.string.save,
            (d, w) -> {
              try {
                store.saveCourse(id, input.getText().toString());
                render();
              } catch (RuntimeException e) {
                toast(R.string.invalid_name);
              }
            })
        .show();
  }

  private void chooseCourse(String id) {
    List<Store.Course> all = store.courses();
    String[] names = new String[all.size() + 2];
    names[0] = getString(R.string.unassigned);
    for (int i = 0; i < all.size(); i++) names[i + 1] = all.get(i).name();
    names[names.length - 1] = getString(R.string.add_course);
    new AlertDialog.Builder(this)
        .setTitle(R.string.assign_course)
        .setItems(
            names,
            (d, which) -> {
              if (which == names.length - 1) {
                EditText input = edit("");
                new AlertDialog.Builder(this)
                    .setTitle(R.string.add_course)
                    .setView(input)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(
                        R.string.save,
                        (a, b) -> {
                          try {
                            store.saveCourse(0, input.getText().toString());
                            for (Store.Course c : store.courses())
                              if (c.name().equals(input.getText().toString().trim()))
                                store.assign(id, c.id());
                            render();
                          } catch (RuntimeException e) {
                            toast(R.string.invalid_name);
                          }
                        })
                    .show();
              } else {
                store.assign(id, which == 0 ? 0 : all.get(which - 1).id());
                render();
              }
            })
        .show();
  }

  private void record() {
    if (EngineService.capture != null) {
      openLecture(EngineService.activeLecture);
      return;
    }
    if (EngineService.processing) {
      toast(R.string.busy);
      return;
    }
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 10);
      return;
    }
    if (StorageGuard.available(this) < Core.RESERVE_BYTES) {
      toast(R.string.storage_low);
      return;
    }
    String id =
        store.createLecture(
            courseFilter,
            prefs.getString("audio_language", "auto"),
            prefs.getBoolean("delete_audio", false),
            "recording");
    try {
      EngineService.command(this, EngineService.RECORD, id);
      openLecture(id);
    } catch (RuntimeException e) {
      store.status(id, "error", "microphone_permission");
      toast(R.string.microphone_permission);
    }
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 11);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == 10) {
      if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) record();
      else toast(R.string.permission_hint);
    }
  }

  private void openLecture(String id) {
    stopPlayback();
    lectureId = id;
    screen = "detail";
    render();
  }

  private void detail() {
    Store.Lecture lecture = store.lecture(lectureId);
    detailMode = detailMode(lecture);
    LinearLayout top = row();
    Button back =
        button(
            R.string.back,
            false,
            () -> {
              screen = "library";
              render();
            });
    Button more = button(R.string.more, false, this::more);
    top.addView(back, new LinearLayout.LayoutParams(0, -1, 1));
    top.addView(more, new LinearLayout.LayoutParams(-2, -1));
    content.addView(top);
    TextView name = text(title(lecture), 25, inkColor);
    name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    content.addView(name);
    content.addView(
        button(
            getString(R.string.assign_course) + " · " + courseName(lecture.course()),
            false,
            () -> chooseCourse(lectureId)));
    detailState = text(status(lecture.status()), 14, actionColor);
    content.addView(detailState);
    detailProgress = text("", 13, mutedColor);
    content.addView(detailProgress);
    detailLanguage = text("", 13, mutedColor);
    content.addView(detailLanguage);
    transcriptionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    transcriptionProgress.setId(R.id.transcription_progress);
    transcriptionProgress.setMax(1000);
    transcriptionProgress.setProgressTintList(ColorStateList.valueOf(primaryColor));
    transcriptionProgress.setProgressBackgroundTintList(ColorStateList.valueOf(surfaceTintColor));
    content.addView(transcriptionProgress, new LinearLayout.LayoutParams(-1, dp(8)));
    workingIndicator = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    workingIndicator.setId(R.id.transcription_working);
    workingIndicator.setIndeterminate(true);
    workingIndicator.setIndeterminateTintList(ColorStateList.valueOf(actionColor));
    content.addView(workingIndicator, new LinearLayout.LayoutParams(-1, dp(4)));
    workState = text("", 13, mutedColor);
    content.addView(workState);
    reviewNotice = text(R.string.transcript_review, 14, actionColor);
    content.addView(reviewNotice);
    if (detailMode.equals("recording")) {
      LinearLayout controls = card();
      recordingClock = text("00:00", 36, inkColor);
      recordingClock.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
      recordingClock.setGravity(Gravity.CENTER);
      controls.addView(recordingClock);
      meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
      meter.setMax(100);
      meter.setProgressTintList(ColorStateList.valueOf(getColor(R.color.scribe_sea_green)));
      meter.setProgressBackgroundTintList(ColorStateList.valueOf(surfaceTintColor));
      controls.addView(meter, new LinearLayout.LayoutParams(-1, dp(10)));
      pause =
          button(
              lecture.status().equals("paused") ? R.string.resume : R.string.pause,
              false,
              () -> EngineService.command(this, EngineService.PAUSE, null));
      two(
          controls,
          pause,
          button(
              R.string.stop, false, () -> EngineService.command(this, EngineService.STOP, null)));
      controls.addView(
          button(
              R.string.mark,
              true,
              () -> {
                EngineService.command(this, EngineService.MARK, null);
                toast(R.string.marked);
              }));
      note(controls, R.string.recording_hint);
      content.addView(controls);
    } else if (!lecture.status().equals("done")) {
      if (EngineService.processing && lectureId.equals(EngineService.activeLecture))
        content.addView(
            button(
                R.string.cancel_processing,
                false,
                () -> EngineService.command(this, EngineService.CANCEL, null)));
      else
        content.addView(
            button(
                R.string.retry,
                true,
                () -> {
                  store.retry(lectureId);
                  EngineService.command(this, EngineService.PROCESS, null);
                  signature = "";
                  handler.postDelayed(this::render, 250);
                }));
    }
    LinearLayout actions = row();
    Button marks = button(R.string.markers, false, this::showMarks);
    actions.addView(marks, new LinearLayout.LayoutParams(0, -1, 1));
    actions.addView(
        button(R.string.copy, false, this::copy), new LinearLayout.LayoutParams(0, -1, 1));
    actions.addView(
        button(R.string.export, false, this::export), new LinearLayout.LayoutParams(0, -1, 1));
    content.addView(actions);
    content.addView(
        button(
            R.string.report_problem,
            false,
            () ->
                startActivity(
                    new Intent(this, ReportActivity.class).putExtra("lecture", lectureId))));
    note(content, R.string.transcript_hint);
    list = new ListView(this);
    list.setDivider(null);
    list.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
    content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView empty = text(R.string.no_transcript, 18, mutedColor);
    content.addView(empty);
    list.setEmptyView(empty);
    list.setOnItemClickListener(
        (p, v, index, id) ->
            play(
                segments.get(index).path(),
                segments.get(index).start() - segments.get(index).chunkStart()));
    list.setOnItemLongClickListener(
        (p, v, index, id) -> {
          Store.Segment s = segments.get(index);
          EditText input = edit(s.text());
          input.setMinLines(4);
          new AlertDialog.Builder(this)
              .setTitle(R.string.edit_passage)
              .setView(input)
              .setNegativeButton(R.string.cancel, null)
              .setPositiveButton(
                  R.string.save,
                  (dialog, which) -> {
                    store.edit(s.id(), input.getText().toString());
                    signature = "";
                    refresh();
                  })
              .show();
          return true;
        });
    refreshDetail();
  }

  private void refresh() {
    if (screen.equals("detail")) refreshDetail();
    boolean busy = EngineService.capture != null || EngineService.processing;
    if (screen.equals("library") && (busy || hadBackgroundWork)) refreshLibrary();
    hadBackgroundWork = busy;
  }

  private void chooseDate() {
    Calendar day = Calendar.getInstance();
    if (dayFrom != 0) day.setTimeInMillis(dayFrom);
    DatePickerDialog dialog =
        new DatePickerDialog(
            this,
            (picker, year, month, date) -> {
              Calendar selected = Calendar.getInstance();
              selected.set(year, month, date, 0, 0, 0);
              selected.set(Calendar.MILLISECOND, 0);
              dayFrom = selected.getTimeInMillis();
              selected.add(Calendar.DATE, 1);
              dayUntil = selected.getTimeInMillis();
              render();
            },
            day.get(Calendar.YEAR),
            day.get(Calendar.MONTH),
            day.get(Calendar.DAY_OF_MONTH));
    dialog.setButton(
        DatePickerDialog.BUTTON_NEUTRAL,
        getString(R.string.all_dates),
        (d, w) -> {
          dayFrom = 0;
          dayUntil = Long.MAX_VALUE;
          render();
        });
    dialog.show();
  }

  private void refreshDetail() {
    Store.Lecture l = store.lecture(lectureId);
    if (l == null) return;
    if (!detailMode.equals(detailMode(l))) {
      render();
      return;
    }
    RecorderEngine recording = EngineService.capture;
    if (recordingClock != null
        && recording != null
        && lectureId.equals(EngineService.activeLecture)) {
      recordingClock.setText(Core.clock(recording.position));
      meter.setProgress(recording.level);
      pause.setText(recording.paused ? R.string.resume : R.string.pause);
    }
    List<Store.Chunk> chunks = store.chunks(lectureId);
    long processed = 0, total = 0, processedPosition = 0;
    StringBuilder key = new StringBuilder(l.status()).append(l.error());
    for (Store.Chunk c : chunks) {
      processed += c.processed();
      total += c.duration();
      processedPosition = Math.max(processedPosition, c.start() + c.processed());
      key.append(c.id()).append(c.state()).append(c.processed());
    }
    List<Store.Mark> marks = store.marks(lectureId);
    key.append(marks.size());
    detailState.setText(
        l.error().isEmpty()
            ? status(l.status())
            : getString(R.string.status_error, status(l.status()), error(l.error())));
    int progress = total > 0 ? (int) Math.min(1000, processed * 1000 / total) : 0;
    if (l.status().equals("done")) progress = 1000;
    detailProgress.setText(
        total > 0
            ? getString(
                R.string.progress_percent, Core.clock(processed), Core.clock(total), progress / 10)
            : getString(R.string.progress, Core.clock(processed), Core.clock(total)));
    transcriptionProgress.setProgress(progress);
    transcriptionProgress.setVisibility(detailMode.equals("recording") ? View.GONE : View.VISIBLE);
    transcriptionProgress.setContentDescription(detailProgress.getText());
    boolean working = EngineService.processing && lectureId.equals(EngineService.activeLecture);
    workingIndicator.setVisibility(working ? View.VISIBLE : View.GONE);
    workState.setVisibility(working ? View.VISIBLE : View.GONE);
    long began = EngineService.windowBeganMs;
    workState.setText(
        began > 0
            ? getString(
                EngineService.checkingSpeech ? R.string.checking_section : R.string.working_section,
                Core.clock(EngineService.windowStartMs),
                Core.clock(EngineService.windowEndMs),
                Core.clock(Math.max(0, android.os.SystemClock.elapsedRealtime() - began)))
            : getString(R.string.preparing_audio));
    boolean review = store.needsReview(lectureId);
    reviewNotice.setVisibility(review ? View.VISIBLE : View.GONE);
    key.append(review);
    String detected = store.detectedLanguage(lectureId);
    String shown =
        l.language().equals("auto") && !detected.isEmpty()
            ? getString(
                R.string.language_detected, audioLanguageName("auto"), audioLanguageName(detected))
            : audioLanguageName(l.language());
    detailLanguage.setText(getString(R.string.lecture_language_value, shown));
    if (signature.equals(key.toString())) return;
    signature = key.toString();
    int first = list.getFirstVisiblePosition();
    View firstView = list.getChildAt(0);
    int offset = firstView == null ? 0 : firstView.getTop();
    segments = store.segments(lectureId);
    starred.clear();
    for (Store.Mark mark : marks) {
      int target = markerTarget(mark.time(), chunks);
      if (target >= 0) starred.add(segments.get(target).id());
    }
    list.setAdapter(
        new BaseAdapter() {
          public int getCount() {
            return segments.size();
          }

          public Object getItem(int p) {
            return segments.get(p);
          }

          public long getItemId(int p) {
            return segments.get(p).id();
          }

          public View getView(int position, View convert, ViewGroup parent) {
            Store.Segment s = segments.get(position);
            LinearLayout v = column();
            v.setPadding(dp(14), dp(10), dp(14), dp(10));
            boolean marked = starred.contains(s.id());
            v.setBackground(
                shape(
                    s.id() == highlightId
                        ? highlightColor
                        : marked ? surfaceTintColor : surfaceColor,
                    16));
            if (prefs.getBoolean("timestamps", true) || marked)
              v.addView(text((marked ? "★  " : "") + Core.clock(s.start()), 12, actionColor));
            TextView passage = text(s.text(), 18, inkColor);
            passage.setLineSpacing(dp(3), 1.08f);
            v.addView(passage);
            return v;
          }
        });
    list.setSelectionFromTop(first, offset);
  }

  private String detailMode(Store.Lecture lecture) {
    if (lecture.status().equals("recording") || lecture.status().equals("paused"))
      return "recording";
    return EngineService.processing && lecture.id().equals(EngineService.activeLecture)
        ? "processing"
        : "idle";
  }

  private void showMarks() {
    List<Store.Mark> marks = store.marks(lectureId);
    if (marks.isEmpty()) {
      toast(R.string.marker_waiting);
      return;
    }
    String[] labels = new String[marks.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = "★  " + Core.clock(marks.get(i).time());
    new AlertDialog.Builder(this)
        .setTitle(R.string.markers)
        .setItems(
            labels,
            (d, n) -> {
              int best = markerTarget(marks.get(n).time(), store.chunks(lectureId));
              if (best < 0) {
                toast(R.string.marker_waiting);
                return;
              }
              highlightId = segments.get(best).id();
              signature = "";
              refreshDetail();
              list.smoothScrollToPosition(best);
            })
        .show();
  }

  private int markerTarget(long time, List<Store.Chunk> chunks) {
    long owner = -1;
    for (Store.Chunk c : chunks) {
      if (c.processed() > 0 && time >= c.start() && time <= c.start() + c.processed()) {
        owner = c.id();
        break;
      }
    }
    if (owner == -1) return -1;
    int best = -1;
    long distance = Long.MAX_VALUE;
    for (int i = 0; i < segments.size(); i++) {
      Store.Segment s = segments.get(i);
      if (s.chunk() != owner) continue;
      long delta = time < s.start() ? s.start() - time : time > s.end() ? time - s.end() : 0;
      if (delta < distance) {
        distance = delta;
        best = i;
      }
    }
    return best;
  }

  private void more() {
    String[] labels = {
      getString(R.string.rename),
      getString(R.string.share),
      getString(R.string.stop_playback),
      getString(R.string.delete)
    };
    new AlertDialog.Builder(this)
        .setTitle(R.string.more)
        .setItems(
            labels,
            (d, index) -> {
              if (index == 0) {
                EditText input = edit(store.lecture(lectureId).title());
                input.setHint(R.string.title);
                new AlertDialog.Builder(this)
                    .setTitle(R.string.rename)
                    .setView(input)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(
                        R.string.save,
                        (a, b) -> {
                          store.rename(lectureId, input.getText().toString());
                          render();
                        })
                    .show();
              } else if (index == 1) share();
              else if (index == 2) stopPlayback();
              else remove();
            })
        .show();
  }

  private void remove() {
    if (lectureId.equals(EngineService.activeLecture)) {
      toast(R.string.busy);
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.delete)
        .setMessage(R.string.delete_confirm)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(
            R.string.delete,
            (d, w) -> {
              stopPlayback();
              List<Store.Chunk> chunks = store.chunks(lectureId);
              File owned = store.directory(this, lectureId);
              for (File file : owned.listFiles() == null ? new File[0] : owned.listFiles())
                if (file.isFile() && !file.delete()) {
                  toast(R.string.processing_error);
                  return;
                }
              store.deleteLecture(lectureId);
              for (Store.Chunk chunk : chunks) {
                if (chunk.imported()
                    && chunk.path().startsWith("content://")
                    && !store.sourceUsed(chunk.path())) {
                  try {
                    getContentResolver()
                        .releasePersistableUriPermission(
                            Uri.parse(chunk.path()), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  } catch (SecurityException ignored) {
                    /* The provider may already have revoked access. */
                  }
                }
              }
              owned.delete();
              screen = "library";
              render();
            })
        .show();
  }

  private void settings() {
    ScrollView scroll = new ScrollView(this);
    LinearLayout page = column();
    scroll.addView(page);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    page.addView(text(R.string.settings, 26, inkColor));
    page.addView(
        button(
            R.string.help,
            false,
            () -> {
              screen = "help";
              render();
            }));
    page.addView(
        button(
            R.string.patch_notes,
            false,
            () -> {
              screen = "patch_notes";
              render();
            }));
    Switch darkTheme = new Switch(this);
    darkTheme.setText(R.string.dark_theme);
    darkTheme.setTextColor(inkColor);
    darkTheme.setPadding(0, dp(16), 0, dp(16));
    darkTheme.setChecked(prefs.getBoolean("dark_theme", false));
    darkTheme.setOnCheckedChangeListener(
        (v, checked) -> {
          prefs.edit().putBoolean("dark_theme", checked).apply();
          ScribeApp.applyNightMode(this);
        });
    page.addView(darkTheme);
    note(page, R.string.ui_language);
    Spinner ui = spinner(List.of("English", "Română"));
    String language = getResources().getConfiguration().getLocales().get(0).getLanguage();
    ui.setSelection(language.equals("ro") ? 1 : 0);
    page.addView(ui);
    ui.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            String chosen = n == 1 ? "ro" : "en";
            if (!chosen.equals(language)) {
              prefs.edit().putString("ui_language", chosen).apply();
              recreate();
            }
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    note(page, R.string.audio_language);
    String[] codes = AUDIO_CODES;
    Spinner audio =
        spinner(
            List.of(
                getString(R.string.automatic),
                "Română",
                "English",
                "Deutsch",
                "Français",
                "Español",
                "Italiano",
                "Українська",
                "Türkçe"));
    String current = prefs.getString("audio_language", "auto");
    for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) audio.setSelection(i);
    page.addView(audio);
    note(page, R.string.audio_language_hint);
    audio.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int n, long id) {
            prefs.edit().putString("audio_language", codes[n]).apply();
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    Switch times = new Switch(this);
    times.setText(R.string.timestamps);
    times.setTextColor(inkColor);
    times.setPadding(0, dp(16), 0, dp(16));
    times.setChecked(prefs.getBoolean("timestamps", true));
    times.setOnCheckedChangeListener(
        (v, checked) -> prefs.edit().putBoolean("timestamps", checked).apply());
    page.addView(times);
    Switch deletion = new Switch(this);
    deletion.setText(R.string.delete_audio);
    deletion.setTextColor(inkColor);
    deletion.setPadding(0, dp(12), 0, dp(12));
    deletion.setChecked(prefs.getBoolean("delete_audio", false));
    deletion.setOnCheckedChangeListener(
        (v, checked) -> prefs.edit().putBoolean("delete_audio", checked).apply());
    page.addView(deletion);
    note(page, R.string.delete_audio_hint);
    note(page, R.string.offline_hint);
    note(page, R.string.model_label);
    page.addView(
        text(
            getString(
                R.string.local_storage,
                String.format(Locale.ROOT, "%.1f", StorageGuard.available(this) / 1e9)),
            14,
            mutedColor));
    note(page, R.string.privacy);
  }

  private String patchLabel(PatchNotes.Release release) {
    Locale locale = getResources().getConfiguration().getLocales().get(0);
    return getString(
        R.string.patch_version_date,
        release.version(),
        release
            .date()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)));
  }

  private void patchNotes() {
    content.addView(
        button(
            R.string.help_back,
            false,
            () -> {
              screen = "settings";
              render();
            }));
    ScrollView scroll = new ScrollView(this);
    LinearLayout page = column();
    scroll.addView(page);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView title = text(R.string.patch_notes, 26, inkColor);
    title.setAccessibilityHeading(true);
    page.addView(title);
    note(page, R.string.patch_intro);
    for (PatchNotes.Release release : PatchNotes.HISTORY) {
      page.addView(
          button(
              patchLabel(release) + "\n" + getString(release.summary()),
              false,
              () -> showPatchNote(release)));
    }
  }

  private void showPatchNote(PatchNotes.Release release) {
    if (patchDialog != null && patchDialog.isShowing()) return;
    patchVersion = release.code();
    ScrollView scroll =
        new ScrollView(this) {
          @Override
          protected void onMeasure(int width, int height) {
            // Keep tablet dialogs compact; allow the content to scroll with larger fonts.
            int maximum = Math.round(getResources().getDisplayMetrics().heightPixels * 0.55f);
            if (MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED)
              maximum = Math.min(maximum, MeasureSpec.getSize(height));
            super.onMeasure(width, MeasureSpec.makeMeasureSpec(maximum, MeasureSpec.AT_MOST));
          }
        };
    LinearLayout page = column();
    page.setPadding(dp(24), dp(8), dp(24), dp(8));
    TextView version = text(patchLabel(release), 14, mutedColor);
    page.addView(version);
    TextView heading = text(release.summary(), 18, inkColor);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    heading.setAccessibilityHeading(true);
    page.addView(heading);
    TextView body =
        text(getString(release.body()) + "\n\n" + getString(release.signoff()), 16, inkColor);
    body.setLineSpacing(dp(3), 1);
    page.addView(body);
    scroll.addView(page);
    scroll.setFillViewport(false);
    patchDialog =
        new AlertDialog.Builder(this)
            .setTitle(
                release.code() == PatchNotes.latest().code()
                    ? R.string.patch_new
                    : R.string.patch_notes)
            .setView(scroll)
            .setPositiveButton(R.string.patch_done, (dialog, which) -> {})
            .setNeutralButton(
                R.string.patch_history,
                (dialog, which) -> {
                  screen = "patch_notes";
                  render();
                })
            .create();
    patchDialog.setOnDismissListener(
        dialog -> {
          patchDialog = null;
          patchVersion = 0;
        });
    patchDialog.show();
    patchDialog.getWindow().setBackgroundDrawable(shape(surfaceColor, 20));
    patchDialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
    patchDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setAllCaps(false);
    patchDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(actionColor);
    patchDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(actionColor);
    if (release.code() == PatchNotes.latest().code())
      prefs.edit().putInt(PatchNotes.SEEN_VERSION, release.code()).apply();
  }

  private void help() {
    content.addView(
        button(
            R.string.help_back,
            false,
            () -> {
              screen = "settings";
              render();
            }));
    ScrollView scroll = new ScrollView(this);
    LinearLayout page = column();
    scroll.addView(page);
    content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView greeting = text(R.string.help_greeting, 26, inkColor);
    greeting.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    greeting.setAccessibilityHeading(true);
    page.addView(greeting);
    page.addView(text(R.string.help_intro, 16, inkColor));
    helpSection(page, R.string.help_record_title, R.string.help_record_body);
    helpSection(page, R.string.help_transcribe_title, R.string.help_transcribe_body);
    helpSection(page, R.string.help_library_title, R.string.help_library_body);
    helpSection(page, R.string.help_transcript_title, R.string.help_transcript_body);
    helpSection(page, R.string.help_import_title, R.string.help_import_body);
    helpSection(page, R.string.help_save_title, R.string.help_save_body);
    helpSection(page, R.string.help_settings_title, R.string.help_settings_body);
    helpSection(page, R.string.help_audio_title, R.string.help_audio_body);
    helpSection(page, R.string.help_trouble_title, R.string.help_trouble_body);
    helpSection(page, R.string.help_updates_title, R.string.help_updates_body);
    helpSection(page, R.string.help_report_title, R.string.help_report_body);
    page.addView(text(R.string.help_footer, 16, inkColor));
  }

  private String audioLanguageName(String code) {
    return code.equals("auto")
        ? getString(R.string.automatic)
        : Locale.forLanguageTag(code)
            .getDisplayLanguage(getResources().getConfiguration().getLocales().get(0));
  }

  private void chooseAudioLanguage() {
    String[] names = new String[AUDIO_CODES.length];
    int selected = 0;
    for (int i = 0; i < AUDIO_CODES.length; i++) {
      names[i] = audioLanguageName(AUDIO_CODES[i]);
      if (AUDIO_CODES[i].equals(prefs.getString("audio_language", "auto"))) selected = i;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.audio_language)
        .setSingleChoiceItems(
            names,
            selected,
            (dialog, which) -> {
              prefs.edit().putString("audio_language", AUDIO_CODES[which]).apply();
              dialog.dismiss();
              render();
            })
        .setNegativeButton(R.string.cancel, null)
        .show();
  }

  private void helpSection(LinearLayout page, int title, int body) {
    LinearLayout section = card();
    LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
    spacing.setMargins(0, dp(12), 0, 0);
    page.addView(section, spacing);
    TextView heading = text(title, 18, inkColor);
    heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    heading.setAccessibilityHeading(true);
    section.addView(heading);
    TextView explanation = text(body, 16, inkColor);
    explanation.setLineSpacing(dp(3), 1);
    section.addView(explanation);
  }

  private Spinner spinner(List<String> items) {
    Spinner s = new Spinner(this);
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
    s.setAdapter(adapter);
    s.setMinimumHeight(dp(48));
    s.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.scribe_cadet_blue)));
    return s;
  }

  private EditText edit(String value) {
    EditText e = new EditText(this);
    e.setTextColor(inkColor);
    e.setTextSize(18);
    e.setPadding(dp(20), dp(12), dp(20), dp(12));
    e.setText(value);
    return e;
  }

  private String title(Store.Lecture l) {
    if (!l.title().isEmpty()) return l.title();
    return getString(
        R.string.auto_title,
        l.course() == 0 ? getString(R.string.recording_name) : courseName(l.course()),
        date(
            l.created(),
            getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ro")
                ? "dd.MM.yyyy · HH:mm"
                : "dd MMM yyyy · HH:mm"));
  }

  private String courseName(long id) {
    for (Store.Course c : store.courses()) if (c.id() == id) return c.name();
    return getString(R.string.unassigned);
  }

  private String date(long millis, String pattern) {
    return new SimpleDateFormat(pattern, getResources().getConfiguration().getLocales().get(0))
        .format(new Date(millis));
  }

  private String status(String value) {
    return getString(
        switch (value) {
          case "recording" -> R.string.state_recording;
          case "paused" -> R.string.state_paused;
          case "queued" -> R.string.state_queued;
          case "processing" -> R.string.state_processing;
          case "done" -> R.string.state_done;
          case "cancelled" -> R.string.state_cancelled;
          case "interrupted" -> R.string.state_interrupted;
          default -> R.string.state_error;
        });
  }

  private String error(String value) {
    return getString(
        switch (value) {
          case "storage_low" -> R.string.storage_low;
          case "thermal" -> R.string.thermal;
          case "microphone_permission" -> R.string.microphone_permission;
          case "microphone_unavailable" -> R.string.microphone_unavailable;
          case "microphone_interrupted" -> R.string.microphone_interrupted;
          case "unsupported_audio" -> R.string.unsupported_audio;
          case "no_words" -> R.string.no_words;
          case "interrupted" -> R.string.interrupted;
          default -> R.string.processing_error;
        });
  }

  private void toast(int message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private String transcript(String id) {
    StringBuilder out = new StringBuilder(title(store.lecture(id))).append("\n\n");
    for (Store.Segment s : store.segments(id)) {
      if (prefs.getBoolean("timestamps", true))
        out.append('[').append(Core.clock(s.start())).append("] ");
      out.append(s.text()).append("\n\n");
    }
    return out.toString();
  }

  private boolean hasText() {
    if (store.segments(lectureId).isEmpty()) {
      toast(R.string.no_export);
      return false;
    }
    return true;
  }

  private void copy() {
    if (!hasText()) return;
    try {
      getSystemService(ClipboardManager.class)
          .setPrimaryClip(ClipData.newPlainText("scribe", transcript(lectureId)));
      toast(R.string.copied);
    } catch (RuntimeException error) {
      toast(R.string.clipboard_large);
    }
  }

  private void share() {
    if (!hasText()) return;
    try {
      startActivity(
          Intent.createChooser(
              new Intent(Intent.ACTION_SEND)
                  .setType("text/plain")
                  .putExtra(Intent.EXTRA_TEXT, transcript(lectureId)),
              getString(R.string.share_title)));
    } catch (RuntimeException e) {
      export();
    }
  }

  private void export() {
    if (!hasText()) return;
    exportLecture = lectureId;
    String name = title(store.lecture(lectureId)).replaceAll("[^\\p{L}\\p{N} ._-]", "_");
    if (name.length() > 90) name = name.substring(0, 90);
    startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TITLE, name + ".txt"),
        21);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if (request == 20) {
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException e) {
        toast(R.string.processing_error);
        return;
      }
      String id =
          store.createLecture(
              courseFilter, prefs.getString("audio_language", "auto"), false, "queued");
      store.addChunk(id, 0, uri.toString(), 0, true);
      openLecture(id);
      EngineService.command(this, EngineService.PROCESS, null);
    } else if (request == 21 && exportLecture != null) {
      String body = transcript(exportLecture);
      files.execute(
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
              if (out == null) throw new java.io.IOException();
              out.write(body.getBytes(StandardCharsets.UTF_8));
              out.flush();
              handler.post(() -> toast(R.string.exported));
            } catch (Exception e) {
              handler.post(() -> toast(R.string.processing_error));
            }
          });
    }
  }

  private void play(String path, long offset) {
    if (EngineService.capture != null) {
      toast(R.string.busy);
      return;
    }
    stopPlayback();
    try {
      MediaPlayer next = new MediaPlayer();
      player = next;
      playingPath = path;
      if (path.startsWith("content://")) next.setDataSource(this, Uri.parse(path));
      else {
        if (!new File(path).isFile()) throw new java.io.IOException();
        next.setDataSource(path);
      }
      next.setOnPreparedListener(
          p -> {
            p.seekTo(Math.max(0, offset), MediaPlayer.SEEK_CLOSEST);
            p.start();
          });
      next.setOnErrorListener(
          (p, what, extra) -> {
            stopPlayback();
            toast(R.string.audio_missing);
            return true;
          });
      next.setOnCompletionListener(
          p -> {
            List<Store.Chunk> chunks = store.chunks(lectureId);
            for (int i = 0; i < chunks.size() - 1; i++)
              if (chunks.get(i).path().equals(path)) {
                play(chunks.get(i + 1).path(), 0);
                return;
              }
            stopPlayback();
          });
      next.prepareAsync();
    } catch (Exception e) {
      stopPlayback();
      toast(R.string.audio_missing);
    }
  }

  private void stopPlayback() {
    if (player != null) {
      player.release();
      player = null;
    }
    playingPath = null;
  }
}
