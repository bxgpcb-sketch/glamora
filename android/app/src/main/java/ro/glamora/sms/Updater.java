package ro.glamora.sms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates with confirmation: checks version.json on the Glamora site; when the colleague taps
 * "Actualizează", downloads the new APK and opens Android's own "Update this app?" screen.
 * Nothing is installed without her pressing Install.
 */
public class Updater {
    static final String BASE = "https://bxgpcb-sketch.github.io/glamora/";

    public static int currentCode(Context c) {
        try { return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception e) { return 0; }
    }

    public static String currentName(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    public static boolean canInstall(Context c) { return c.getPackageManager().canRequestPackageInstalls(); }

    /** Only looks at version.json and remembers whether a newer version exists. */
    public static synchronized JSONObject check(Context c) {
        try {
            JSONObject v = new JSONObject(Fb.request("GET", BASE + "version.json?t=" + System.currentTimeMillis(), null, null, null));
            Fb.prefs(c).edit().putLong("lastUpdateCheck", System.currentTimeMillis())
                    .putInt("latestCode", v.getInt("versionCode"))
                    .putString("latestVersion", v.optString("versionName", "")).apply();
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean updateAvailable(Context c) { return Fb.prefs(c).getInt("latestCode", 0) > currentCode(c); }

    /** Downloads the newer APK into the cache. Returns null when ready, otherwise a message. */
    public static synchronized String prepare(Context c) {
        try {
            JSONObject v = check(c);
            if (v == null) return "Nu pot verifica actualizarea (internet?).";
            int code = v.getInt("versionCode");
            if (code <= currentCode(c)) return "Ai ultima versiune (" + currentName(c) + ").";
            if (!canInstall(c)) return "Permite mai întâi „Instalare aplicații necunoscute” pentru Glamora.";
            download(BASE + v.optString("apk", "glamora.apk") + "?v=" + code, new File(c.getCacheDir(), "update.apk"));
            return null;
        } catch (Exception e) {
            return "Descărcarea nu a mers: " + e.getMessage();
        }
    }

    /** Called when the colleague taps "Actualizează": download, then Android asks her to confirm. */
    public static synchronized String downloadAndAsk(Context c) {
        try {
            JSONObject v = check(c);
            if (v == null) return "Nu pot verifica actualizarea (internet?).";
            int code = v.getInt("versionCode");
            if (code <= currentCode(c)) return "Ai ultima versiune (" + currentName(c) + ").";
            if (!canInstall(c)) return "Permite mai întâi „Instalare aplicații necunoscute” pentru Glamora.";
            File f = new File(c.getCacheDir(), "update.apk");
            download(BASE + v.optString("apk", "glamora.apk") + "?v=" + code, f);
            install(c, f);
            return "Versiunea " + v.optString("versionName") + " descărcată. Confirmă instalarea.";
        } catch (Exception e) {
            return "Actualizarea nu a mers: " + e.getMessage();
        }
    }

    static void download(String url, File f) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    static void install(Context c, File f) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream out = s.openWrite("glamora.apk", 0, f.length()); InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent i = new Intent(c, UpdateReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(c, id, i, flags).getIntentSender());
        }
    }
}
