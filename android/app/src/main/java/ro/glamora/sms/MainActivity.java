package ro.glamora.sms;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    static final int PINK = Color.parseColor("#f8eef2");
    static final int PLUM = Color.parseColor("#8a3358");
    static final int INK = Color.parseColor("#241a1f");
    static final int MUTED = Color.parseColor("#7f6c75");
    static final int OK = Color.parseColor("#2f7a4f");
    static final int BAD = Color.parseColor("#b03636");

    final Handler ui = new Handler(Looper.getMainLooper());
    LinearLayout root;
    TextView logView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(PINK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(PINK);
        sv.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(28), p, dp(28));
        sv.addView(root);
        setContentView(sv);
        String crash = Fb.prefs(this).getString("crash", "");
        if (!crash.isEmpty()) { showCrash(crash); return; }
        try { ReminderJob.schedule(this); } catch (Throwable t) { Sender.log(this, "Programarea verificării orare a eșuat: " + t); }
        try { render(); } catch (Throwable t) { showCrash(stack(t)); }
    }

    static String stack(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return "Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ") · "
                + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n" + sw;
    }

    void showCrash(String info) {
        root.removeAllViews();
        root.addView(text("Glamora SMS s-a oprit", 22, BAD, true));
        root.addView(text("Fă o captură de ecran și trimite-o. Apoi apasă „Încearcă din nou”.", 15, INK, false));
        Button again = button("Încearcă din nou", true);
        again.setOnClickListener(v -> { Fb.prefs(this).edit().remove("crash").commit(); recreate(); });
        root.addView(again);
        TextView t = text(info, 11, INK, false);
        t.setTextIsSelectable(true);
        t.setPadding(0, dp(16), 0, 0);
        root.addView(t);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (logView != null) refreshLog();
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    void render() {
        root.removeAllViews();
        TextView brand = text("Glamora", 40, PLUM, true);
        brand.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);
        TextView sub = text("SMS automate · Salon Vintage", 13, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);
        if (Fb.isSignedIn(this)) renderMain(); else renderLogin();
    }

    void renderLogin() {
        LinearLayout card = card();
        card.addView(label("Email"));
        EditText email = input(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(email);
        card.addView(label("Parolă"));
        EditText pass = input(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(pass);
        TextView err = text("", 14, BAD, true);
        err.setPadding(0, dp(8), 0, 0);
        card.addView(err);
        Button go = button("Intră în cont", true);
        card.addView(go);
        go.setOnClickListener(v -> {
            go.setEnabled(false);
            go.setText("Se conectează…");
            err.setText("");
            final String e = email.getText().toString().trim(), pw = pass.getText().toString();
            new Thread(() -> {
                String msg = null;
                try {
                    Fb.signIn(this, e, pw);
                    Sender.loadStaff(this);
                } catch (Fb.HttpError x) {
                    msg = x.getMessage() != null && x.getMessage().contains("INVALID") ? "Email sau parolă greșită." : "Nu a mers: " + x.getMessage();
                    Fb.signOut(this);
                } catch (Exception x) {
                    msg = "Verifică internetul. (" + x.getMessage() + ")";
                    Fb.signOut(this);
                }
                final String m = msg;
                ui.post(() -> {
                    if (m == null) { askPermissions(); render(); }
                    else { err.setText(m); go.setEnabled(true); go.setText("Intră în cont"); }
                });
            }).start();
        });
        root.addView(card);
    }

    void renderMain() {
        final SharedPreferences p = Fb.prefs(this);
        String staff = p.getString("staff", "");

        LinearLayout acc = card();
        acc.addView(text(staff.isEmpty() ? "Cont fără colegă aleasă" : Sender.staffName(staff), 20, INK, true));
        acc.addView(text(p.getString("email", ""), 14, MUTED, false));
        if (staff.isEmpty()) {
            TextView w = text("Deschide Glamora pe telefon, intră în cont și alege cine ești. Apoi revino aici.", 14, BAD, false);
            w.setPadding(0, dp(8), 0, 0);
            acc.addView(w);
        } else if (staff.equals("mymy")) {
            TextView w = text("Pentru Mymy mesajele se trimit manual din Glamora (SMS detaliat).", 14, MUTED, false);
            w.setPadding(0, dp(8), 0, 0);
            acc.addView(w);
        }
        root.addView(acc);

        // Status of the permissions the app needs
        boolean smsOk = checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = getSystemService(PowerManager.class);
        boolean battOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        if (!smsOk || !battOk) {
            LinearLayout perm = card();
            perm.addView(text("De făcut o singură dată", 16, BAD, true));
            if (!smsOk) {
                Button bs = button("Permite trimiterea și citirea SMS", true);
                bs.setOnClickListener(v -> askPermissions());
                perm.addView(bs);
            }
            if (!battOk) {
                Button bb = button("Permite să ruleze în fundal (baterie)", false);
                bb.setOnClickListener(v -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                });
                perm.addView(bb);
            }
            root.addView(perm);
        }

        LinearLayout set = card();
        Switch sw = new Switch(this);
        sw.setText("Trimite automat reamintirile (1/0)");
        sw.setTextSize(16);
        sw.setTextColor(INK);
        sw.setChecked(p.getBoolean("enabled", true));
        sw.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean("enabled", on).apply());
        set.addView(sw);

        set.addView(label("Ora la care pleacă mesajele pentru a doua zi"));
        NumberPicker np = new NumberPicker(this);
        np.setMinValue(8);
        np.setMaxValue(20);
        np.setFormatter(v -> String.format("%02d:00", v));
        np.setValue(p.getInt("sendHour", 10));
        np.setOnValueChangedListener((v, o, n) -> p.edit().putInt("sendHour", n).apply());
        np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        set.addView(np, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        TextView hint = text("Programările făcute pentru mâine după ora asta primesc mesajul în următoarea oră (până la 21:00).", 13, MUTED, false);
        set.addView(hint);

        Button now = button("Trimite acum reamintirile pentru mâine", true);
        now.setOnClickListener(v -> {
            now.setEnabled(false);
            now.setText("Se trimite…");
            new Thread(() -> {
                String r = Sender.run(this, true);
                Sender.log(this, r);
                ui.post(() -> { now.setEnabled(true); now.setText("Trimite acum reamintirile pentru mâine"); refreshLog(); });
            }).start();
        });
        set.addView(now);
        root.addView(set);

        LinearLayout lg = card();
        lg.addView(text("Ce s-a trimis / primit", 16, INK, true));
        String last = p.getString("lastRun", "");
        if (!last.isEmpty()) lg.addView(text("Ultima verificare: " + last, 13, MUTED, false));
        logView = text("", 14, INK, false);
        logView.setPadding(0, dp(8), 0, 0);
        logView.setTextIsSelectable(true);
        lg.addView(logView);
        root.addView(lg);
        refreshLog();

        Button out = button("Deconectare", false);
        out.setTextColor(BAD);
        out.setOnClickListener(v -> { Fb.signOut(this); render(); });
        root.addView(out);
    }

    void refreshLog() {
        String l = Fb.prefs(this).getString("log", "");
        logView.setText(l.isEmpty() ? "Nimic încă." : l);
    }

    void askPermissions() {
        requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS}, 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        render();
    }

    // ---- tiny UI helpers ----
    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView label(String s) {
        TextView t = text(s.toUpperCase(), 12, MUTED, true);
        t.setPadding(0, dp(14), 0, dp(6));
        t.setLetterSpacing(0.05f);
        return t;
    }

    EditText input(int type) {
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setTextSize(17);
        e.setTextColor(INK);
        e.setSingleLine(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), Color.parseColor("#ead9e0"));
        e.setBackground(g);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary ? Color.WHITE : PLUM);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? PLUM : Color.WHITE);
        g.setCornerRadius(dp(14));
        if (!primary) g.setStroke(dp(1), Color.parseColor("#ead9e0"));
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), Color.parseColor("#ead9e0"));
        c.setBackground(g);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }
}
