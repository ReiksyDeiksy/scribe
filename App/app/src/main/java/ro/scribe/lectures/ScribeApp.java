package ro.scribe.lectures;

import android.app.Application;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;

public final class ScribeApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    applyNightMode(this);
    Store.get(this).recover();
  }

  public static void applyNightMode(Context context) {
    boolean dark = context.getSharedPreferences("settings", 0).getBoolean("dark_theme", false);
    context
        .getSystemService(UiModeManager.class)
        .setApplicationNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
  }

  // Both languages are packaged locally; bundle.language.enableSplit is disabled.
  @android.annotation.SuppressLint("AppBundleLocaleChanges")
  public static Context localized(Context base) {
    String fallback = Locale.getDefault().getLanguage().equals("ro") ? "ro" : "en";
    String language = base.getSharedPreferences("settings", 0).getString("ui_language", fallback);
    Configuration config = new Configuration(base.getResources().getConfiguration());
    config.setLocale(Locale.forLanguageTag(language));
    return base.createConfigurationContext(config);
  }
}
