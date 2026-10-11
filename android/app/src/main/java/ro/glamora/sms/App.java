package ro.glamora.sms;

import android.app.Application;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Keeps the last crash so the app can show it on the next start. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String info = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ") · "
                        + Build.MANUFACTURER + " " + Build.MODEL + "\n" + sw;
                getSharedPreferences("glamora", MODE_PRIVATE).edit().putString("crash", info).commit();
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }
}
