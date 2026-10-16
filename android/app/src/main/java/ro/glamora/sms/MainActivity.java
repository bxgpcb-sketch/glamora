package ro.glamora.sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** Glamora inside the app (same pages as the website) + a bridge to send SMS from this phone. */
public class MainActivity extends Activity {
    static final String HOME = "https://bxgpcb-sketch.github.io/glamora/";
    static final String HOST = "bxgpcb-sketch.github.io";
    static final int PINK = Color.parseColor("#f8eef2");

    WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(PINK);
            getWindow().setNavigationBarColor(PINK);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } catch (Throwable ignored) { }

        web = new WebView(this);
        web.setBackgroundColor(PINK);
        web.setFitsSystemWindows(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " GlamoraApp/1");
        web.addJavascriptInterface(new Bridge(), "GlamoraAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/glamora")
                        && !u.getPath().endsWith(".apk")) return false;
                openOutside(u);
                return true;
            }
        });
        setContentView(web);

        if (b != null) web.restoreState(b);
        else web.loadUrl(HOME);

        new Thread(() -> {
            Updater.check(getApplicationContext());
            runOnUiThread(() -> web.evaluateJavascript("window.glamoraUpdate&&window.glamoraUpdate()", null));
        }).start();
        try { ReminderJob.schedule(this); } catch (Throwable t) { Sender.log(this, "Programarea verificării a eșuat: " + t); }
        if (!smsOk()) requestSms();
    }

    void openOutside(Uri u) {
        try {
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            Intent i = scheme.equals("sms") || scheme.equals("smsto")
                    ? new Intent(Intent.ACTION_SENDTO, u) : new Intent(Intent.ACTION_VIEW, u);
            if (scheme.equals("tel")) i = new Intent(Intent.ACTION_DIAL, u);
            startActivity(i);
        } catch (Exception e) {
            Sender.log(this, "Nu pot deschide " + u);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.glamoraBack?String(window.glamoraBack()):'false'", r -> {
            if (r == null || !r.contains("true")) {
                if (web.canGoBack()) web.goBack(); else MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.glamoraRefresh&&window.glamoraRefresh();window.glamoraUpdate&&window.glamoraUpdate()", null);
        new Thread(() -> Sender.scanInbox(getApplicationContext())).start();
    }

    boolean smsOk() {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    void requestSms() {
        requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        web.evaluateJavascript("window.glamoraRefresh&&window.glamoraRefresh()", null);
    }

    /** Methods the Glamora page can call (window.GlamoraAndroid.*). */
    class Bridge {
        @JavascriptInterface
        public boolean isApp() { return true; }

        @JavascriptInterface
        public void setSession(String refreshToken, String uid, String email) {
            if (refreshToken == null || refreshToken.isEmpty()) return;
            SharedPreferences p = Fb.prefs(MainActivity.this);
            boolean changed = !refreshToken.equals(p.getString("refreshToken", "")) || !uid.equals(p.getString("uid", ""));
            if (!uid.equals(p.getString("uid", ""))) p.edit().remove("staff").remove("pending").remove("sentIds").remove("confirmed").apply();
            if (changed) p.edit().putString("refreshToken", refreshToken).putString("uid", uid).putString("email", email)
                    .putLong("expiresAt", 0).apply();
            new Thread(() -> {
                try { Sender.loadStaff(MainActivity.this); Sender.publishAuto(MainActivity.this); }
                catch (Exception e) { Sender.log(MainActivity.this, "Nu pot citi contul: " + e.getMessage()); }
            }).start();
        }

        @JavascriptInterface
        public void clearSession() { Fb.signOut(MainActivity.this); }

        @JavascriptInterface
        public String getSettings() {
            SharedPreferences p = Fb.prefs(MainActivity.this);
            PowerManager pm = getSystemService(PowerManager.class);
            try {
                return new JSONObject()
                        .put("confirm", p.getBoolean("autoConfirm", true))
                        .put("remind", p.getBoolean("enabled", true))
                        .put("sendHour", p.getInt("sendHour", 10))
                        .put("smsOk", smsOk())
                        .put("batteryOk", pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()))
                        .put("staff", p.getString("staff", ""))
                        .put("log", p.getString("log", ""))
                        .put("signedIn", Fb.isSignedIn(MainActivity.this))
                        .put("lastJobAt", p.getLong("lastJobAt", 0))
                        .put("lastScanAt", p.getLong("lastScanAt", 0))
                        .put("version", Updater.currentName(MainActivity.this))
                        .put("latest", p.getString("latestVersion", ""))
                        .put("updateAvailable", Updater.updateAvailable(MainActivity.this))
                        .put("canInstall", Updater.canInstall(MainActivity.this))
                        .toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void setSettings(String json) {
            try {
                JSONObject o = new JSONObject(json);
                Fb.prefs(MainActivity.this).edit()
                        .putBoolean("autoConfirm", o.optBoolean("confirm", true))
                        .putBoolean("enabled", o.optBoolean("remind", true))
                        .putInt("sendHour", o.optInt("sendHour", 10))
                        .apply();
                new Thread(() -> Sender.publishAuto(MainActivity.this)).start();
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void requestSmsPermission() { runOnUiThread(MainActivity.this::requestSms); }

        @JavascriptInterface
        public void openBatterySettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void newAppointment(String id) {
            new Thread(() -> Sender.log(MainActivity.this, Sender.sendConfirmation(MainActivity.this, id))).start();
        }

        @JavascriptInterface
        public void openInstallSettings() {
            runOnUiThread(() -> {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                catch (Exception e) { Sender.log(MainActivity.this, "Nu pot deschide setarea de instalare."); }
            });
        }

        /** The colleague tapped "Actualizează": download, then open Android's installer to confirm. */
        @JavascriptInterface
        public void installUpdate() {
            new Thread(() -> {
                String err = Updater.prepare(MainActivity.this);
                if (err != null) { report(err); return; }
                runOnUiThread(() -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(ApkProvider.URI, "application/vnd.android.package-archive");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(i);
                        report("Confirmă instalarea pe ecranul care s-a deschis.");
                    } catch (Exception e) {
                        new Thread(() -> {
                            try { Updater.install(MainActivity.this, new java.io.File(getCacheDir(), "update.apk")); report("Confirmă instalarea."); }
                            catch (Exception x) { report("Instalarea nu a pornit: " + x.getMessage()); }
                        }).start();
                    }
                });
            }).start();
        }

        void report(String r) {
            Sender.log(MainActivity.this, r);
            runOnUiThread(() -> web.evaluateJavascript("window.glamoraUpdateResult&&window.glamoraUpdateResult(" + JSONObject.quote(r) + ")", null));
        }

        @JavascriptInterface
        public void checkReplies() {
            new Thread(() -> {
                Sender.log(MainActivity.this, Sender.scanInbox(MainActivity.this, true));
                runOnUiThread(() -> web.evaluateJavascript("window.glamoraRefresh&&window.glamoraRefresh()", null));
            }).start();
        }

        @JavascriptInterface
        public void runNow() {
            new Thread(() -> {
                Sender.log(MainActivity.this, Sender.run(MainActivity.this, true));
                runOnUiThread(() -> web.evaluateJavascript("window.glamoraRefresh&&window.glamoraRefresh()", null));
            }).start();
        }
    }
}
