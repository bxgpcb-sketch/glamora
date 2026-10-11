package ro.glamora.sms;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Firebase Auth + Firestore over plain HTTPS (REST), same project as the Glamora web app. */
public class Fb {
    static final String API_KEY = "AIzaSyD2-uXZ8_AsSmLiBgUKSvzn2A0OTMjmnwY";
    static final String PROJECT = "glamora-fc7b4";
    static final String DOCS = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents";

    public static class Doc {
        public String id;
        public JSONObject fields;
        public String str(String k) {
            try {
                if (fields == null || !fields.has(k)) return "";
                JSONObject v = fields.getJSONObject(k);
                if (v.has("stringValue")) return v.getString("stringValue");
                if (v.has("integerValue")) return v.getString("integerValue");
                if (v.has("doubleValue")) return String.valueOf(v.getDouble("doubleValue"));
                if (v.has("booleanValue")) return String.valueOf(v.getBoolean("booleanValue"));
                return "";
            } catch (Exception e) { return ""; }
        }
        public int num(String k, int def) {
            try { String s = str(k); if (s.isEmpty()) return def; return (int) Math.round(Double.parseDouble(s)); }
            catch (Exception e) { return def; }
        }
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("glamora", Context.MODE_PRIVATE); }

    public static boolean isSignedIn(Context c) { return !prefs(c).getString("refreshToken", "").isEmpty(); }

    public static void signIn(Context c, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        body.put("returnSecureToken", true);
        JSONObject r = new JSONObject(request("POST",
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY,
                body.toString(), "application/json", null));
        prefs(c).edit()
                .putString("idToken", r.getString("idToken"))
                .putString("refreshToken", r.getString("refreshToken"))
                .putString("uid", r.getString("localId"))
                .putString("email", r.optString("email", email))
                .putLong("expiresAt", System.currentTimeMillis() + Long.parseLong(r.getString("expiresIn")) * 1000L)
                .apply();
    }

    public static void signOut(Context c) {
        prefs(c).edit().remove("idToken").remove("refreshToken").remove("uid").remove("email")
                .remove("expiresAt").remove("staff").remove("staffName").apply();
    }

    static synchronized String token(Context c) throws Exception {
        SharedPreferences p = prefs(c);
        if (System.currentTimeMillis() < p.getLong("expiresAt", 0) - 120000L) return p.getString("idToken", "");
        String rt = p.getString("refreshToken", "");
        if (rt.isEmpty()) throw new Exception("Nu ești conectată");
        String form = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(rt, "UTF-8");
        JSONObject r = new JSONObject(request("POST", "https://securetoken.googleapis.com/v1/token?key=" + API_KEY,
                form, "application/x-www-form-urlencoded", null));
        p.edit().putString("idToken", r.getString("id_token"))
                .putString("refreshToken", r.getString("refresh_token"))
                .putLong("expiresAt", System.currentTimeMillis() + Long.parseLong(r.getString("expires_in")) * 1000L)
                .apply();
        return r.getString("id_token");
    }

    public static Doc get(Context c, String path) throws Exception {
        try {
            JSONObject r = new JSONObject(request("GET", DOCS + "/" + path, null, null, token(c)));
            return toDoc(r);
        } catch (HttpError e) {
            if (e.code == 404) return null;
            throw e;
        }
    }

    /** Appointments on a given date (yyyy-MM-dd) for one stylist. */
    public static List<Doc> appointments(Context c, String date, String staff) throws Exception {
        JSONObject q = new JSONObject("{\"structuredQuery\":{\"from\":[{\"collectionId\":\"appointments\"}],"
                + "\"where\":{\"compositeFilter\":{\"op\":\"AND\",\"filters\":["
                + "{\"fieldFilter\":{\"field\":{\"fieldPath\":\"date\"},\"op\":\"EQUAL\",\"value\":{\"stringValue\":" + JSONObject.quote(date) + "}}},"
                + "{\"fieldFilter\":{\"field\":{\"fieldPath\":\"staff\"},\"op\":\"EQUAL\",\"value\":{\"stringValue\":" + JSONObject.quote(staff) + "}}}"
                + "]}}}}");
        JSONArray arr = new JSONArray(request("POST", DOCS + ":runQuery", q.toString(), "application/json", token(c)));
        List<Doc> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o.has("document")) out.add(toDoc(o.getJSONObject("document")));
        }
        return out;
    }

    /** Update some string fields of an existing document. */
    public static void update(Context c, String path, Map<String, String> values) throws Exception {
        StringBuilder url = new StringBuilder(DOCS + "/" + path + "?currentDocument.exists=true");
        JSONObject fields = new JSONObject();
        for (Map.Entry<String, String> e : values.entrySet()) {
            url.append("&updateMask.fieldPaths=").append(URLEncoder.encode(e.getKey(), "UTF-8"));
            fields.put(e.getKey(), new JSONObject().put("stringValue", e.getValue()));
        }
        request("PATCH", url.toString(), new JSONObject().put("fields", fields).toString(), "application/json", token(c));
    }

    /** Set one boolean inside a map field, e.g. config/salon autoSms.tina = true. */
    public static void setFlag(Context c, String path, String mapField, String key, boolean value) throws Exception {
        String url = DOCS + "/" + path + "?currentDocument.exists=true&updateMask.fieldPaths="
                + URLEncoder.encode(mapField + "." + key, "UTF-8");
        JSONObject inner = new JSONObject().put(key, new JSONObject().put("booleanValue", value));
        JSONObject fields = new JSONObject().put(mapField, new JSONObject().put("mapValue", new JSONObject().put("fields", inner)));
        request("PATCH", url, new JSONObject().put("fields", fields).toString(), "application/json", token(c));
    }

    /** Documents of a collection where field == value. */
    public static List<Doc> where(Context c, String collection, String field, String value) throws Exception {
        JSONObject q = new JSONObject("{\"structuredQuery\":{\"from\":[{\"collectionId\":" + JSONObject.quote(collection) + "}],"
                + "\"where\":{\"fieldFilter\":{\"field\":{\"fieldPath\":" + JSONObject.quote(field) + "},\"op\":\"EQUAL\",\"value\":{\"stringValue\":" + JSONObject.quote(value) + "}}}}}");
        JSONArray arr = new JSONArray(request("POST", DOCS + ":runQuery", q.toString(), "application/json", token(c)));
        List<Doc> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o.has("document")) out.add(toDoc(o.getJSONObject("document")));
        }
        return out;
    }

    static Doc toDoc(JSONObject d) {
        Doc doc = new Doc();
        String name = d.optString("name", "");
        doc.id = name.substring(name.lastIndexOf('/') + 1);
        doc.fields = d.optJSONObject("fields");
        if (doc.fields == null) doc.fields = new JSONObject();
        return doc;
    }

    public static class HttpError extends Exception {
        public final int code;
        HttpError(int code, String msg) { super(msg); this.code = code; }
    }

    static String request(String method, String url, String body, String type, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        if ("PATCH".equals(method)) {
            con.setRequestMethod("POST");
            con.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        } else {
            con.setRequestMethod(method);
        }
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", type + "; charset=utf-8");
            try (OutputStream os = con.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        }
        if (code >= 400) {
            String msg = text;
            try { msg = new JSONObject(text).getJSONObject("error").optString("message", text); } catch (Exception ignored) { }
            throw new HttpError(code, msg);
        }
        return text;
    }

    static String keys(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) sb.append(it.next()).append(' ');
        return sb.toString();
    }
}
