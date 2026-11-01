package ro.scribe.lectures;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import java.util.zip.ZipInputStream;

/** Separate test-APK process: proves that a recipient can read the granted attachment. */
public final class ReportReceiverActivity extends Activity {
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    String result = "Report attachment failed";
    Uri uri = getIntent().getParcelableExtra(android.content.Intent.EXTRA_STREAM);
    try (var zip = new ZipInputStream(getContentResolver().openInputStream(uri))) {
      boolean report = false, diagnostics = false;
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        report |= entry.getName().equals("report.json");
        diagnostics |= entry.getName().equals("diagnostics.json");
        zip.closeEntry();
      }
      if (report && diagnostics) result = "Report attachment received";
    } catch (Exception ignored) {
    }
    TextView view = new TextView(this);
    view.setText(result);
    view.setTextSize(24);
    setContentView(view);
  }
}
