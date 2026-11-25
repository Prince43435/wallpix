package com.wallpix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Local store shared by the Admin panel and the User panel.
 * Images live in files/wallpapers, thumbnails in files/thumbs and the
 * index in files/wallpapers.json — all inside the app's private storage.
 */
public final class Store {

    public static final String PREFS = "wallpix";
    private static final String INDEX = "wallpapers.json";
    private static final String KEY_PIN = "pin";
    private static final String KEY_FAVS = "favs";
    public static final String DEFAULT_PIN = "1234";

    private Store() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "wallpapers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File thumbDir(Context c) {
        File d = new File(c.getFilesDir(), "thumbs");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File imageFile(Context c, Wallpaper w) {
        return new File(dir(c), w.file);
    }

    public static File thumbFile(Context c, Wallpaper w) {
        return new File(thumbDir(c), w.thumb);
    }

    private static File indexFile(Context c) {
        return new File(c.getFilesDir(), INDEX);
    }

    public static synchronized List<Wallpaper> all(Context c) {
        List<Wallpaper> out = new ArrayList<>();
        File f = indexFile(c);
        if (!f.exists()) return out;
        try {
            String s = readText(f);
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                out.add(Wallpaper.from(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void saveAll(Context c, List<Wallpaper> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Wallpaper w : list) arr.put(w.toJson());
            File f = indexFile(c);
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            if (f.exists() && !f.delete()) {
                // keep going; rename below will replace on most file systems
            }
            tmp.renameTo(f);
        } catch (Exception ignored) {
        }
    }

    public static Wallpaper byId(Context c, String id) {
        for (Wallpaper w : all(c)) {
            if (w.id.equals(id)) return w;
        }
        return null;
    }

    /** Copies a gallery image into app storage and indexes it. Blocking — call off the UI thread. */
    public static Wallpaper add(Context c, Uri uri, String title, String category, String fallbackTitle)
            throws IOException {
        String id = UUID.randomUUID().toString();
        File full = new File(dir(c), id + ".jpg");
        File thumb = new File(thumbDir(c), id + ".jpg");
        File tmp = new File(c.getCacheDir(), "import_" + id + ".tmp");

        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Cannot open the selected image");
        try {
            OutputStream out = new FileOutputStream(tmp);
            copy(in, out);
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }

        try {
            ImageUtil.save(tmp, full, 2560, 93);
            ImageUtil.save(tmp, thumb, 720, 82);
        } finally {
            tmp.delete();
        }

        List<Wallpaper> list = all(c);
        Wallpaper w = new Wallpaper();
        w.id = id;
        w.title = cleanTitle(title, fallbackTitle, list.size() + 1);
        w.category = (category == null || category.trim().isEmpty()) ? "Other" : category.trim();
        w.file = full.getName();
        w.thumb = thumb.getName();
        w.created = System.currentTimeMillis();
        w.featured = false;
        list.add(0, w);
        saveAll(c, list);
        return w;
    }

    public static void delete(Context c, Wallpaper w) {
        List<Wallpaper> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(w.id)) list.remove(i);
        }
        saveAll(c, list);
        File f = imageFile(c, w);
        File t = thumbFile(c, w);
        if (f.exists()) f.delete();
        if (t.exists()) t.delete();
        setFav(c, w.id, false);
    }

    public static void update(Context c, Wallpaper w) {
        List<Wallpaper> list = all(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(w.id)) {
                list.set(i, w);
                break;
            }
        }
        saveAll(c, list);
    }

    public static String getPin(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PIN, DEFAULT_PIN);
    }

    public static void setPin(Context c, String pin) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PIN, pin).apply();
    }

    public static boolean isFav(Context c, String id) {
        return prefs(c).getStringSet(KEY_FAVS, new HashSet<String>()).contains(id);
    }

    public static void setFav(Context c, String id, boolean fav) {
        Set<String> set = new HashSet<>(prefs(c).getStringSet(KEY_FAVS, new HashSet<String>()));
        if (fav) set.add(id); else set.remove(id);
        prefs(c).edit().putStringSet(KEY_FAVS, set).apply();
    }

    public static void toggleFav(Context c, String id) {
        setFav(c, id, !isFav(c, id));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String cleanTitle(String title, String fallback, int n) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) t = fallback == null ? "" : fallback.trim();
        if (t.isEmpty()) t = "Wallpaper " + n;
        if (t.length() > 60) t = t.substring(0, 60);
        return t;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
        out.close();
    }

    private static String readText(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int r = in.read(data, off, data.length - off);
                if (r < 0) break;
                off += r;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
