package ro.glamora.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsManager;

import org.json.JSONObject;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sends the day-before 1/0 reminders and records replies. */
public class Sender {
    static final String[] STAFF_IDS = {"tina", "mymy", "irina"};
    static final String[] STAFF_NAMES = {"Tina G", "Mymy C", "Irina B"};
    static final String[] DAYS = {"duminica", "luni", "marti", "miercuri", "joi", "vineri", "sambata"};
    static final String[] MONTHS = {"ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie",
            "august", "septembrie", "octombrie", "noiembrie", "decembrie"};

    static SharedPreferences prefs(Context c) { return Fb.prefs(c); }

    public static String staffName(String id) {
        for (int i = 0; i < STAFF_IDS.length; i++) if (STAFF_IDS[i].equals(id)) return STAFF_NAMES[i];
        return id == null ? "" : id;
    }

    /** Reads which colleague this account belongs to (users/{uid}.staff), cached. */
    public static String loadStaff(Context c) throws Exception {
        String uid = prefs(c).getString("uid", "");
        Fb.Doc d = Fb.get(c, "users/" + uid);
        String staff = d == null ? "" : d.str("staff");
        prefs(c).edit().putString("staff", staff).apply();
        return staff;
    }

    static String plain(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('ș', 's').replace('ş', 's').replace('ț', 't').replace('ţ', 't')
                .replace('Ș', 'S').replace('Ş', 'S').replace('Ț', 'T').replace('Ţ', 'T');
    }

    static String digits(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }

    /** Last 9 digits identify a Romanian mobile number regardless of +40 / 0 prefix. */
    static String key(String phone) {
        String d = digits(phone);
        return d.length() > 9 ? d.substring(d.length() - 9) : d;
    }

    static String message(Fb.Doc a, Calendar day) {
        String client = a.str("client");
        String service = a.str("service");
        return plain("Buna ziua" + (client.isEmpty() ? "" : ", " + client) + "! Va reamintim programarea la Salon Vintage maine, "
                + DAYS[day.get(Calendar.DAY_OF_WEEK) - 1] + " " + day.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[day.get(Calendar.MONTH)]
                + ", ora " + a.str("start") + ", la " + staffName(a.str("staff"))
                + (service.isEmpty() ? "" : " (" + service + ")")
                + ". Raspundeti cu 1 pentru confirmare sau 0 pentru anulare. Multumim!");
    }

    static boolean isBlock(String service) {
        String s = plain(service).trim().toLowerCase(Locale.ROOT);
        return s.equals("pauza") || s.equals("ocupat");
    }

    static void sendSms(Context c, String phone, String text) {
        SmsManager sms;
        if (android.os.Build.VERSION.SDK_INT >= 31) sms = c.getSystemService(SmsManager.class);
        else sms = SmsManager.getDefault();
        ArrayList<String> parts = sms.divideMessage(text);
        if (parts.size() == 1) sms.sendTextMessage(phone, null, text, null, null);
        else sms.sendMultipartTextMessage(phone, null, parts, null, null);
    }

    static String iso() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date());
    }

    /**
     * @param force true when the colleague presses "Trimite acum" (ignores the hour window).
     * @return a short summary for the log.
     */
    public static synchronized String run(Context c, boolean force) {
        SharedPreferences p = prefs(c);
        try {
            if (!Fb.isSignedIn(c)) return "Nu ești conectată.";
            if (!force && !p.getBoolean("enabled", true)) return "Trimiterea automată e oprită.";
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int sendHour = p.getInt("sendHour", 10);
            if (!force && (hour < sendHour || hour >= 21)) return null; // outside the sending window, stay quiet
            if (c.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return "Lipsește permisiunea de trimitere SMS.";

            String staff = loadStaff(c);
            if (staff.isEmpty()) return "Contul nu e legat de o colegă. Alege în Glamora (Detalii cont → Schimbă cine sunt).";

            Calendar day = (Calendar) now.clone();
            day.add(Calendar.DAY_OF_MONTH, 1);
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day.getTime());

            List<Fb.Doc> list = Fb.appointments(c, date, staff);
            JSONObject sentIds = new JSONObject(p.getString("sentIds", "{}"));
            JSONObject pending = new JSONObject(p.getString("pending", "{}"));
            int sent = 0, skipped = 0;
            for (Fb.Doc a : list) {
                if (isBlock(a.str("service"))) continue;
                String phone = a.str("phone");
                if (digits(phone).length() < 9) { skipped++; continue; }
                if ("anulata".equals(a.str("status"))) continue;
                if (!a.str("confirmRequestedAt").isEmpty() || sentIds.has(a.id)) continue;

                sendSms(c, digits(phone), message(a, day));
                sentIds.put(a.id, date);
                pending.put(key(phone), new JSONObject().put("id", a.id).put("date", date).put("client", a.str("client")));
                p.edit().putString("sentIds", sentIds.toString()).putString("pending", pending.toString()).apply();
                Map<String, String> upd = new HashMap<>();
                String t = iso();
                upd.put("confirmRequestedAt", t);
                upd.put("confirmRequestedVia", "auto");
                upd.put("remindedAt", t);
                upd.put("remindedVia", "sms");
                try { Fb.update(c, "appointments/" + a.id, upd); } catch (Exception e) { log(c, "Trimis, dar nu s-a putut marca în agendă: " + e.getMessage()); }
                log(c, "Trimis SMS 1/0 către " + a.str("client") + " (" + a.str("start") + ")");
                sent++;
                try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            }
            cleanup(c, sentIds, pending);
            p.edit().putString("lastRun", new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(now.getTime())).apply();
            if (sent == 0 && !force) return null;
            return "Mâine (" + date + "): " + sent + " SMS trimise" + (skipped > 0 ? ", " + skipped + " fără număr" : "") + ".";
        } catch (Exception e) {
            return "Eroare: " + e.getMessage();
        }
    }

    /** Forget requests for days already past. */
    static void cleanup(Context c, JSONObject sentIds, JSONObject pending) {
        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
            List<String> drop = new ArrayList<>();
            for (Iterator<String> it = sentIds.keys(); it.hasNext(); ) { String k = it.next(); if (sentIds.getString(k).compareTo(today) < 0) drop.add(k); }
            for (String k : drop) sentIds.remove(k);
            drop.clear();
            for (Iterator<String> it = pending.keys(); it.hasNext(); ) { String k = it.next(); if (pending.getJSONObject(k).getString("date").compareTo(today) < 0) drop.add(k); }
            for (String k : drop) pending.remove(k);
            prefs(c).edit().putString("sentIds", sentIds.toString()).putString("pending", pending.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Handles an incoming SMS. Returns a log line or null when it isn't a reply we're waiting for. */
    public static String handleReply(Context c, String from, String body) {
        try {
            JSONObject pending = new JSONObject(prefs(c).getString("pending", "{}"));
            String k = key(from);
            if (!pending.has(k)) return null;
            String b = plain(body).trim().toLowerCase(Locale.ROOT);
            String status;
            if (b.matches("^(1|da)(\\b.*|$)")) status = "confirmata";
            else if (b.matches("^(0|nu)(\\b.*|$)")) status = "anulata";
            else return null;
            JSONObject info = pending.getJSONObject(k);
            Map<String, String> upd = new HashMap<>();
            upd.put("status", status);
            upd.put("replyText", body.trim());
            upd.put("replyAt", iso());
            Fb.update(c, "appointments/" + info.getString("id"), upd);
            pending.remove(k);
            prefs(c).edit().putString("pending", pending.toString()).apply();
            return (status.equals("confirmata") ? "✓ Confirmat: " : "✕ Anulat: ") + info.optString("client") + " (" + info.getString("date") + ")";
        } catch (Exception e) {
            return "Eroare la răspuns: " + e.getMessage();
        }
    }

    public static void log(Context c, String line) {
        if (line == null) return;
        SharedPreferences p = prefs(c);
        String stamp = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(new java.util.Date());
        String all = stamp + "  " + line + "\n" + p.getString("log", "");
        if (all.length() > 6000) all = all.substring(0, 6000);
        p.edit().putString("log", all).apply();
    }
}
