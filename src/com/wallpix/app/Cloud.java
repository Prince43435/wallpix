package com.wallpix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional cloud sync. Admin uploads wallpapers to a WallPix server and user
 * panels on other phones download the catalog, thumbnails and full images.
 */
public final class Cloud {

    public static final String DEFAULT_KEY = "wallpix-admin";

    private static final String K_URL = "cloud_url";
    private static final String K_KEY = "cloud_key";
    private static final String K_CACHE = "cloud_cache";
    private static final String K_CACHE_URL = "cloud_cache_url";

    private Cloud() {}

    public static String url(Context c) {
        return normalize(prefs(c).getString(K_URL, ""));
    }

    public static String key(Context c) {
        String k = prefs(c).getString(K_KEY, DEFAULT_KEY);
        return (k == null || k.trim().isEmpty()) ? DEFAULT_KEY : k.trim();
    }

    public static boolean enabled(Context c) {
        return !url(c).isEmpty();
    }

    public static void save(Context c, String url, String key) {
        prefs(c).edit()
                .putString(K_URL, normalize(url))
                .putString(K_KEY, (key == null || key.trim().isEmpty()) ? DEFAULT_KEY : key.trim())
                .apply();
        if (normalize(url).isEmpty()) {
            prefs(c).edit().remove(K_CACHE).remove(K_CACHE_URL).apply();
        }
    }

    private static String normalize(String raw) {
        String u = raw == null ? "" : raw.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ catalog

    public static List<Wallpaper> fetchCatalog(Context c) throws Exception {
        String base = url(c);
        if (base.isEmpty()) throw new IOException("Cloud URL not set");

        HttpURLConnection con = open(base + "/api/wallpapers");
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("Server returned " + code);
            String body = readText(con.getInputStream());
            List<Wallpaper> list = parse(body, base);
            prefs(c).edit().putString(K_CACHE, body).putString(K_CACHE_URL, base).apply();
            return list;
        } finally {
            con.disconnect();
        }
    }

    /** Last successfully fetched catalog, for instant/offline display. */
    public static List<Wallpaper> cachedCatalog(Context c) {
        String base = url(c);
        if (base.isEmpty()) return new ArrayList<>();
        String cachedFor = prefs(c).getString(K_CACHE_URL, "");
        if (!base.equals(cachedFor)) return new ArrayList<>();
        String body = prefs(c).getString(K_CACHE, "");
        if (body.isEmpty()) return new ArrayList<>();
        try {
            return parse(body, base);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<Wallpaper> parse(String body, String base) throws Exception {
        List<Wallpaper> out = new ArrayList<>();
        JSONObject root = new JSONObject(body);
        JSONArray arr = root.optJSONArray("wallpapers");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Wallpaper w = new Wallpaper();
            w.id = o.optString("id", "");
            if (w.id.isEmpty()) continue;
            w.title = o.optString("title", "Wallpaper");
            w.category = o.optString("category", "Other");
            w.featured = o.optBoolean("featured", false);
            w.created = o.optLong("created", 0L);
            w.remote = true;
            w.imageUrl = absolute(base, o.optString("image", ""));
            w.thumbUrl = absolute(base, o.optString("thumb", ""));
            if (w.thumbUrl.isEmpty()) w.thumbUrl = w.imageUrl;
            out.add(w);
        }
        return out;
    }

    private static String absolute(String base, String path) {
        if (path == null || path.isEmpty()) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    // ------------------------------------------------------------ uploads

    public static void upload(Context c, Wallpaper w, File full, File thumb) throws Exception {
        String base = url(c);
        if (base.isEmpty()) throw new IOException("Cloud URL not set");

        String query = "?id=" + enc(w.id)
                + "&title=" + enc(w.title)
                + "&category=" + enc(w.category)
                + "&featured=" + (w.featured ? "1" : "0")
                + "&created=" + w.created;
        postFile(base + "/api/wallpapers" + query, key(c), full);

        if (thumb != null && thumb.exists()) {
            postFile(base + "/api/thumbs?id=" + enc(w.id), key(c), thumb);
        }
    }

    public static void updateMeta(Context c, Wallpaper w) throws Exception {
        String base = url(c);
        if (base.isEmpty()) return;
        String query = "?id=" + enc(w.id)
                + "&title=" + enc(w.title)
                + "&category=" + enc(w.category)
                + "&featured=" + (w.featured ? "1" : "0");
        HttpURLConnection con = open(base + "/api/meta" + query);
        try {
            con.setRequestMethod("POST");
            con.setRequestProperty("X-Admin-Key", key(c));
            con.setDoOutput(true);
            con.setFixedLengthStreamingMode(0);
            con.getOutputStream().close();
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("Server returned " + code);
        } finally {
            con.disconnect();
        }
    }

    public static void remove(Context c, String id) throws Exception {
        if (!enabled(c)) return;
        HttpURLConnection con = open(url(c) + "/api/wallpapers/" + enc(id));
        try {
            con.setRequestMethod("DELETE");
            con.setRequestProperty("X-Admin-Key", key(c));
            int code = con.getResponseCode();
            if (code != 200 && code != 404) throw new IOException("Server returned " + code);
        } finally {
            con.disconnect();
        }
    }

    private static void postFile(String url, String adminKey, File file) throws Exception {
        long len = file.length();
        if (len <= 0) throw new IOException("Empty file");
        HttpURLConnection con = open(url);
        try {
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setFixedLengthStreamingMode(len);
            con.setRequestProperty("X-Admin-Key", adminKey);
            con.setRequestProperty("Content-Type", "image/jpeg");

            InputStream in = new java.io.FileInputStream(file);
            try {
                OutputStream out = con.getOutputStream();
                copy(in, out);
                out.flush();
                out.close();
            } finally {
                try { in.close(); } catch (Exception ignored) {}
            }

            int code = con.getResponseCode();
            if (code != 200) {
                throw new IOException("Server returned " + code);
            }
        } finally {
            con.disconnect();
        }
    }

    // ---------------------------------------------------------- downloads

    public static File thumbFile(Context c, Wallpaper w) {
        return new File(dir(c, "cloud_thumbs"), w.id + ".jpg");
    }

    public static File fullFile(Context c, Wallpaper w) {
        return new File(dir(c, "cloud_full"), w.id + ".jpg");
    }

    public static boolean ensureThumb(Context c, Wallpaper w) throws IOException {
        String u = (w.thumbUrl == null || w.thumbUrl.isEmpty()) ? w.imageUrl : w.thumbUrl;
        try {
            return ensure(c, u, thumbFile(c, w));
        } catch (IOException e) {
            if (u != null && !u.equals(w.imageUrl) && w.imageUrl != null && !w.imageUrl.isEmpty()) {
                return ensure(c, w.imageUrl, thumbFile(c, w));
            }
            throw e;
        }
    }

    public static boolean ensureFull(Context c, Wallpaper w) throws IOException {
        String u = (w.imageUrl == null || w.imageUrl.isEmpty()) ? w.thumbUrl : w.imageUrl;
        return ensure(c, u, fullFile(c, w));
    }

    private static boolean ensure(Context c, String urlStr, File dst) throws IOException {
        if (dst.exists() && dst.length() > 0) return true;
        if (urlStr == null || urlStr.isEmpty()) throw new IOException("Missing image URL");

        HttpURLConnection con = open(urlStr);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("Download failed (" + code + ")");
            File tmp = new File(dst.getAbsolutePath() + ".part");
            InputStream in = con.getInputStream();
            OutputStream out = new FileOutputStream(tmp);
            try {
                copy(in, out);
                out.flush();
            } finally {
                try { in.close(); } catch (Exception ignored) {}
                try { out.close(); } catch (Exception ignored) {}
            }
            if (tmp.length() <= 0) {
                tmp.delete();
                throw new IOException("Empty download");
            }
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) {
                tmp.delete();
                throw new IOException("Could not save download");
            }
            return true;
        } finally {
            con.disconnect();
        }
    }

    // ------------------------------------------------------------- dialog

    public static void showSettings(final Activity a, final boolean admin, final Runnable afterSave) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, Ui.dp(a, 8), pad, 0);

        EditText urlField = input(a, "http://192.168.1.5:8080");
        urlField.setText(url(a));
        box.addView(label(a, "Server URL"));
        box.addView(urlField);

        EditText keyField = null;
        if (admin) {
            keyField = input(a, DEFAULT_KEY);
            keyField.setText(key(a));
            keyField.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            box.addView(label(a, "Admin key"));
            box.addView(keyField);
        }

        TextView note = new TextView(a);
        note.setText(admin
                ? "Jis phone me server chal raha hai uska address daalo.\n"
                  + "Same WiFi: http://<phone-IP>:8080\n"
                  + "Save ke baad “Push all to cloud” se purane wallpapers upload karo."
                : "Wahi server URL daalo jo admin ne diya hai.\n"
                  + "Save karte hi catalog refresh ho jayega.");
        note.setTextColor(a.getResources().getColor(R.color.muted, a.getTheme()));
        note.setTextSize(12);
        note.setLineSpacing(Ui.dp(a, 2), 1f);
        note.setPadding(0, Ui.dp(a, 14), 0, 0);
        box.addView(note);

        final EditText kf = keyField;
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle("Cloud sync")
                .setView(box)
                .setPositiveButton("Save", (di, i) -> {
                    save(a, urlField.getText().toString(),
                            kf == null ? key(a) : kf.getText().toString());
                    Ui.toast(a, enabled(a) ? "Cloud sync on" : "Cloud sync off");
                    if (afterSave != null) afterSave.run();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.show();

        Window w = d.getWindow();
        if (w != null) {
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static EditText input(Activity a, String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setTextColor(a.getResources().getColor(R.color.text, a.getTheme()));
        e.setHintTextColor(a.getResources().getColor(R.color.muted2, a.getTheme()));
        e.setBackgroundResource(R.drawable.input_bg);
        e.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, 6);
        e.setLayoutParams(lp);
        return e;
    }

    private static TextView label(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextColor(a.getResources().getColor(R.color.muted, a.getTheme()));
        t.setTextSize(12);
        t.setPadding(0, Ui.dp(a, 12), 0, 0);
        return t;
    }

    // ------------------------------------------------------------ helpers

    private static File dir(Context c, String name) {
        File d = new File(c.getCacheDir(), name);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static HttpURLConnection open(String urlStr) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(45000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("Accept", "*/*");
        return con;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(in, out);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[32 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }
}
