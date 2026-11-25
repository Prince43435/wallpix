package com.wallpix.app;

import org.json.JSONException;
import org.json.JSONObject;

/** One wallpaper record stored in wallpapers.json. */
public class Wallpaper {
    public String id = "";
    public String title = "";
    public String category = "Other";
    public String file = "";
    public String thumb = "";
    public long created = 0L;
    public boolean featured = false;

    /** True for wallpapers that come from the cloud server (not in local storage). */
    public boolean remote = false;
    public String imageUrl = "";
    public String thumbUrl = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("category", category);
        o.put("file", file);
        o.put("thumb", thumb);
        o.put("created", created);
        o.put("featured", featured);
        return o;
    }

    public static Wallpaper from(JSONObject o) {
        Wallpaper w = new Wallpaper();
        w.id = o.optString("id", "");
        w.title = o.optString("title", "Wallpaper");
        w.category = o.optString("category", "Other");
        w.file = o.optString("file", "");
        w.thumb = o.optString("thumb", "");
        w.created = o.optLong("created", 0L);
        w.featured = o.optBoolean("featured", false);
        return w;
    }
}
