package ro.scribe.lectures;

import android.annotation.SuppressLint;
import android.content.Context;

final class StorageGuard {
  private StorageGuard() {}

  // Reserve actual free space; do not assume that Android will evict other apps' caches.
  @SuppressLint("UsableSpace")
  static long available(Context context) {
    return context.getFilesDir().getUsableSpace();
  }
}
