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
import java.util.HashSet;
import java.util.Set;
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

    static final String ADDR = "la Salon Vintage piata Mihail Kogalniceanu nr. 6 bl. I2 sc. D ap. 46 zona muzeului de istorie.";
    static final String DEF_CONFIRM = "Buna ziua, {zi} {data} la {ora} aveti programare " + ADDR + " Va asteptam!";
    static final String DEF_REMIND10 = "Buna ziua, {zi} {data} la {ora} aveti programare " + ADDR + " Raspundeti cu 1 pentru confirmare sau 0 pentru anulare. Va asteptam!";

    /** Message templates from config/salon.templates (set by the Glamora web app), with built-in fallbacks. */
    static String template(Context c, String name, String def) {
        try {
            Fb.Doc d = Fb.get(c, "config/salon");
            if (d != null && d.fields.has("templates")) {
                JSONObject f = d.fields.getJSONObject("templates").getJSONObject("mapValue").getJSONObject("fields");
                if (f.has(name)) {
                    String v = f.getJSONObject(name).optString("stringValue", "");
                    if (!v.isEmpty()) return v;
                }
            }
        } catch (Exception ignored) { }
        return def;
    }

    static String fill(String tpl, Fb.Doc a) {
        Calendar day = parseDate(a.str("date"));
        String data = String.format(Locale.ROOT, "%02d.%02d.%d", day.get(Calendar.DAY_OF_MONTH), day.get(Calendar.MONTH) + 1, day.get(Calendar.YEAR));
        return plain(tpl.replace("{zi}", DAYS[day.get(Calendar.DAY_OF_WEEK) - 1]).replace("{data}", data)
                .replace("{ora}", a.str("start")).replace("{nume}", a.str("client"))
                .replace("{lucratoare}", staffName(a.str("staff"))).replace("{serviciu}", a.str("service")));
    }

    static String message(Context c, Fb.Doc a) { return fill(template(c, "remind10", DEF_REMIND10), a); }

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
            if (!force && !p.getBoolean("enabled", true)) return null;
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

                sendSms(c, digits(phone), message(c, a));
                sentIds.put(a.id, date);
                pending.put(key(phone), new JSONObject().put("id", a.id).put("date", date).put("client", a.str("client")));
                p.edit().putString("sentIds", sentIds.toString()).putString("pending", pending.toString()).apply();
                Map<String, String> upd = new HashMap<>();
                String t = iso();
                upd.put("confirmRequestedAt", t);
                upd.put("confirmRequestedVia", "auto");
                upd.put("remindedAt", t);
                upd.put("remindedVia", "sms");
                upd.put("phoneKey", key(phone));
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

    static Calendar parseDate(String ymd) {
        Calendar d = Calendar.getInstance();
        try { d.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(ymd)); } catch (Exception ignored) { }
        return d;
    }

    static String confirmationText(Context c, Fb.Doc a) { return fill(template(c, "confirm", DEF_CONFIRM), a); }

    static final Set<String> inFlight = new HashSet<>();

    static void clearNeeds(Context c, String id) {
        try { Map<String, String> u = new HashMap<>(); u.put("needsConfirm", ""); Fb.update(c, "appointments/" + id, u); } catch (Exception ignored) { }
    }

    /** Sends the "programarea a fost acceptată" SMS for one appointment, if it belongs to this phone's stylist. */
    public static String sendConfirmation(Context c, String id) {
        synchronized (inFlight) { if (inFlight.contains(id)) return null; inFlight.add(id); }
        try {
            SharedPreferences p = prefs(c);
            if (!Fb.isSignedIn(c) || !p.getBoolean("autoConfirm", true)) return null;
            JSONObject done = new JSONObject(p.getString("confirmed", "{}"));
            if (done.has(id)) return null;
            String staff = p.getString("staff", "");
            if (staff.isEmpty()) staff = loadStaff(c);
            if (!staff.equals("tina") && !staff.equals("irina")) return null;
            Fb.Doc a = Fb.get(c, "appointments/" + id);
            if (a == null || !staff.equals(a.str("staff"))) return null;
            if (!a.str("confirmSentAt").isEmpty()) { if (!a.str("needsConfirm").isEmpty()) clearNeeds(c, id); return null; }
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
            if (isBlock(a.str("service")) || digits(a.str("phone")).length() < 9 || "anulata".equals(a.str("status"))
                    || a.str("date").compareTo(today) < 0) { clearNeeds(c, id); return null; }
            if (c.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                return "Lipsește permisiunea SMS: confirmarea pentru " + a.str("client") + " nu a plecat.";
            sendSms(c, digits(a.str("phone")), confirmationText(c, a));
            done.put(id, a.str("date"));
            p.edit().putString("confirmed", done.toString()).apply();
            Map<String, String> upd = new HashMap<>();
            upd.put("confirmSentAt", iso());
            upd.put("confirmVia", "auto");
            upd.put("needsConfirm", "");
            try { Fb.update(c, "appointments/" + id, upd); } catch (Exception e) { return "Confirmare trimisă, dar nemarcată în agendă: " + e.getMessage(); }
            return "Confirmare trimisă către " + a.str("client") + " (" + a.str("date") + ", " + a.str("start") + ")";
        } catch (Exception e) {
            return "Eroare la confirmare: " + e.getMessage();
        } finally {
            synchronized (inFlight) { inFlight.remove(id); }
        }
    }

    /** Confirmations for appointments colleagues created for this stylist (needsConfirm == staff). */
    public static void sendPendingConfirmations(Context c) {
        try {
            SharedPreferences p = prefs(c);
            if (!Fb.isSignedIn(c) || !p.getBoolean("autoConfirm", true)) return;
            String staff = loadStaff(c);
            if (!staff.equals("tina") && !staff.equals("irina")) return;
            for (Fb.Doc d : Fb.where(c, "appointments", "needsConfirm", staff)) log(c, sendConfirmation(c, d.id));
        } catch (Exception e) {
            log(c, "Eroare la verificarea confirmărilor: " + e.getMessage());
        }
    }

    /** Tells the web app whether this stylist's confirmations are automatic (config/salon.autoSms). */
    public static void publishAuto(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String staff = p.getString("staff", "");
            if (!staff.equals("tina") && !staff.equals("irina")) return;
            boolean on = p.getBoolean("autoConfirm", true)
                    && c.checkSelfPermission(android.Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Fb.setFlag(c, "config/salon", "autoSms", staff, on);
        } catch (Exception e) {
            log(c, "Nu pot salva setarea automată: " + e.getMessage());
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
            JSONObject done = new JSONObject(prefs(c).getString("confirmed", "{}"));
            drop.clear();
            for (Iterator<String> it = done.keys(); it.hasNext(); ) { String k = it.next(); if (done.getString(k).compareTo(today) < 0) drop.add(k); }
            for (String k : drop) done.remove(k);
            prefs(c).edit().putString("sentIds", sentIds.toString()).putString("pending", pending.toString()).putString("confirmed", done.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Status a reply means: "confirmata", "anulata" or null when it isn't a 1/0 answer. */
    static String replyStatus(String body) {
        String b = plain(body).trim().toLowerCase(Locale.ROOT);
        if (b.matches("^(1|da)(\\b.*|$)")) return "confirmata";
        if (b.matches("^(0|nu)(\\b.*|$)")) return "anulata";
        return null;
    }

    /**
     * Handles an incoming SMS (from the live receiver or from the inbox scan).
     * Finds the client's waiting appointment in Firestore by the last 9 digits of her number.
     */
    public static synchronized String handleReply(Context c, String from, String body, long when) {
        try {
            String status = replyStatus(body);
            if (status == null || !Fb.isSignedIn(c)) return null;
            SharedPreferences p = prefs(c);
            String k = key(from);
            String msgId = k + "|" + plain(body).trim().toLowerCase(Locale.ROOT);
            JSONObject seen = new JSONObject(p.getString("seenReplies3", "{}"));
            if (isDuplicate(seen, msgId, when)) return null; // same message seen by another path (SMS / inbox / notification)

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date());
            String staff = p.getString("staff", "");
            Fb.Doc best = null;
            for (Fb.Doc a : Fb.where(c, "appointments", "phoneKey", k)) {
                if (a.str("confirmRequestedAt").isEmpty()) continue;
                if (a.str("date").compareTo(today) < 0) continue;
                if (!a.str("replyAt").isEmpty()) continue;
                boolean mine = staff.isEmpty() || staff.equals(a.str("staff"));
                if (best == null || (mine && !staff.equals(best.str("staff"))) || (mine == staff.equals(best.str("staff")) && a.str("date").compareTo(best.str("date")) < 0)) best = a;
            }
            if (best == null && !staff.isEmpty()) {
                Calendar d = Calendar.getInstance();
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                for (int i = 0; i < 8 && best == null; i++) {
                    for (Fb.Doc a : Fb.appointments(c, f.format(d.getTime()), staff)) {
                        if (!k.equals(key(a.str("phone")))) continue;
                        if (a.str("confirmRequestedAt").isEmpty() || !a.str("replyAt").isEmpty()) continue;
                        best = a;
                        break;
                    }
                    d.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
            String id = best == null ? null : best.id;
            String client = best == null ? "" : best.str("client");
            String date = best == null ? "" : best.str("date");
            if (id == null) { // older appointments without phoneKey: use what this phone sent
                JSONObject pending = new JSONObject(p.getString("pending", "{}"));
                if (pending.has(k)) {
                    JSONObject info = pending.getJSONObject(k);
                    id = info.getString("id"); client = info.optString("client"); date = info.optString("date");
                    pending.remove(k);
                    p.edit().putString("pending", pending.toString()).apply();
                }
            }
            if (id == null) {
                // keep it and try again later (e.g. after the appointment is saved again)
                JSONObject un = new JSONObject(p.getString("unmatched", "{}"));
                String uk = msgId + "@" + (when / 600000L);
                boolean first = !un.has(uk);
                un.put(uk, new JSONObject().put("from", from).put("body", body).put("when", when));
                p.edit().putString("unmatched", un.toString()).apply();
                return first ? "Răspuns „" + body.trim() + "” de la …" + k.substring(Math.max(0, k.length() - 4)) + ": nu am găsit încă programarea; reîncerc automat." : null;
            }
            seen.put(msgId, when);
            if (seen.length() > 300) seen = new JSONObject();
            JSONObject un = new JSONObject(p.getString("unmatched", "{}"));
            un.remove(msgId + "@" + (when / 600000L));
            p.edit().putString("seenReplies3", seen.toString()).putString("unmatched", un.toString()).apply();

            Map<String, String> upd = new HashMap<>();
            upd.put("status", status);
            upd.put("replyText", body.trim());
            upd.put("replyAt", iso());
            Fb.update(c, "appointments/" + id, upd);
            return (status.equals("confirmata") ? "✓ Confirmat: " : "✕ Anulat: ") + client + " (" + date + ")";
        } catch (Exception e) {
            return "Eroare la răspuns: " + e.getMessage();
        }
    }

    /** The same reply can reach us 2-3 times (SMS, inbox, notification) within minutes; a later "1" is a new answer. */
    static boolean isDuplicate(JSONObject seen, String id, long when) {
        long prev = seen.optLong(id, 0);
        return prev != 0 && Math.abs(when - prev) < 10L * 60 * 1000;
    }

    static String fold(String s) { return plain(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }

    /**
     * A reply seen in a chat (RCS) notification. The sender is a phone number when the client is not in
     * the phone's contacts, otherwise the contact name: then we match it to the client name of a waiting appointment.
     */
    public static synchronized String handleReplyFromChat(Context c, String sender, String body, long when) {
        try {
            String s = sender.startsWith("tel:") ? sender.substring(4) : sender;
            if (digits(s).length() >= 9) return handleReply(c, s, body, when);
            String status = replyStatus(body);
            if (status == null) return null;
            if (!Fb.isSignedIn(c)) return "Răspuns primit, dar aplicația nu e conectată pentru SMS: ieși din cont și intră din nou.";
            SharedPreferences p = prefs(c);
            String name = fold(s);
            if (name.isEmpty()) return null;
            String msgId = "n:" + name + "|" + plain(body).trim().toLowerCase(Locale.ROOT);
            JSONObject seen = new JSONObject(p.getString("seenReplies3", "{}"));
            if (isDuplicate(seen, msgId, when)) return null;
            String staff = p.getString("staff", "");
            if (staff.isEmpty()) staff = loadStaff(c);
            if (staff.isEmpty()) return null;
            Calendar d = Calendar.getInstance();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            List<Fb.Doc> hits = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                for (Fb.Doc a : Fb.appointments(c, f.format(d.getTime()), staff)) {
                    if (a.str("confirmRequestedAt").isEmpty() || !a.str("replyAt").isEmpty()) continue;
                    String cn = fold(a.str("client"));
                    if (cn.isEmpty()) continue;
                    if (cn.equals(name) || cn.startsWith(name + " ") || name.startsWith(cn + " ") || cn.contains(name) || name.contains(cn)) hits.add(a);
                }
                if (!hits.isEmpty()) break;
                d.add(Calendar.DAY_OF_MONTH, 1);
            }
            if (hits.size() != 1) return hits.isEmpty() ? null : "Răspuns „" + body.trim() + "” de la " + sender + ": mai multe programări cu numele acesta, marchează manual.";
            Fb.Doc a = hits.get(0);
            Map<String, String> upd = new HashMap<>();
            upd.put("status", status);
            upd.put("replyText", body.trim());
            upd.put("replyAt", iso());
            Fb.update(c, "appointments/" + a.id, upd);
            seen.put(msgId, when);
            p.edit().putString("seenReplies3", seen.toString()).apply();
            return (status.equals("confirmata") ? "✓ Confirmat (chat): " : "✕ Anulat (chat): ") + a.str("client") + " (" + a.str("date") + ")";
        } catch (Exception e) {
            return "Eroare la răspuns din chat: " + e.getMessage();
        }
    }

    /** Tries again the replies that had no matching appointment (kept up to 3 days). */
    static void retryUnmatched(Context c) {
        try {
            JSONObject un = new JSONObject(prefs(c).getString("unmatched", "{}"));
            long limit = System.currentTimeMillis() - 3L * 24 * 3600 * 1000;
            List<String> ids = new ArrayList<>();
            for (Iterator<String> it = un.keys(); it.hasNext(); ) ids.add(it.next());
            for (String id : ids) {
                JSONObject m = un.getJSONObject(id);
                if (m.optLong("when") < limit) {
                    JSONObject cur = new JSONObject(prefs(c).getString("unmatched", "{}"));
                    cur.remove(id);
                    prefs(c).edit().putString("unmatched", cur.toString()).apply();
                    continue;
                }
                log(c, handleReply(c, m.getString("from"), m.getString("body"), m.optLong("when")));
            }
        } catch (Exception ignored) { }
    }

    /** Safety net: reads 1/0 replies from the SMS inbox in case the live receiver was blocked by the phone. */
    public static void scanInbox(Context c) { scanInbox(c, false); }

    /** Reads 1/0 replies from the SMS inbox. With report=true returns a short summary for the colleague. */
    public static String scanInbox(Context c, boolean report) {
        if (c.checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return report ? "Lipsește permisiunea de citire SMS: apasă „Permite SMS”." : null;
        if (!Fb.isSignedIn(c)) return report ? "Aplicația nu e conectată pentru SMS: ieși din cont și intră din nou." : null;
        SharedPreferences p = prefs(c);
        retryUnmatched(c);
        if (!p.getBoolean("rescan107", false)) p.edit().putBoolean("rescan107", true).remove("inboxSince").apply();
        long since = p.getLong("inboxSince", System.currentTimeMillis() - 3L * 24 * 3600 * 1000);
        if (report) since = Math.min(since, System.currentTimeMillis() - 3L * 24 * 3600 * 1000); // manual check looks back 3 days
        long max = p.getLong("inboxSince", since);
        int total = 0, answers = 0;
        try (android.database.Cursor cur = c.getContentResolver().query(android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{"address", "body", "date"}, "date > ?", new String[]{String.valueOf(since)}, "date ASC")) {
            if (cur != null) while (cur.moveToNext()) {
                String addr = cur.getString(0), body = cur.getString(1);
                long when = cur.getLong(2);
                total++;
                if (when > max) max = when;
                if (addr == null || body == null || replyStatus(body) == null) continue;
                answers++;
                log(c, handleReply(c, addr, body, when));
            }
        } catch (Exception e) {
            return "Nu pot citi mesajele primite: " + e.getMessage();
        }
        p.edit().putLong("inboxSince", max).putLong("lastScanAt", System.currentTimeMillis()).apply();
        return report ? "Verificat: " + total + " SMS primite în ultimele 3 zile, dintre care " + answers + " răspunsuri 1/0." : null;
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
