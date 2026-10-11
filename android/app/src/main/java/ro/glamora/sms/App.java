package ro.glamora.sms;

import android.app.Application;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Keeps the last crash: in the app (shown on next start) and as a text file in Downloads. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            String info = "";
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                info = "Glamora SMS · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · "
                        + Build.MANUFACTURER + " " + Build.MODEL + "\nThread: " + t.getName() + "\n" + sw;
                getSharedPreferences("glamora", MODE_PRIVATE).edit().putString("crash", info).commit();
            } catch (Throwable ignored) { }
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, "glamora-eroare.txt");
                    v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u != null) try (OutputStream os = getContentResolver().openOutputStream(u)) {
                        if (os != null) os.write(info.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
