package com.wallpix.app;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Build;

import java.io.FileInputStream;
import java.io.InputStream;

/** Applies a stored wallpaper to the home screen, lock screen or both. */
public final class WallpaperSetter {

    public static final int HOME = 1;
    public static final int LOCK = 2;
    public static final int BOTH = 3;

    private WallpaperSetter() {}

    /** Returns null on success, otherwise a human readable error message. */
    public static String apply(Context c, Wallpaper w, int mode) {
        int which;
        if (mode == HOME) which = WallpaperManager.FLAG_SYSTEM;
        else if (mode == LOCK) which = WallpaperManager.FLAG_LOCK;
        else which = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK;

        try {
            java.io.File file = w.remote ? Cloud.fullFile(c, w) : Store.imageFile(c, w);
            if (!file.exists()) return "Wallpaper is still downloading";
            InputStream in = new FileInputStream(file);
            try {
                WallpaperManager wm = WallpaperManager.getInstance(c);
                if (Build.VERSION.SDK_INT >= 24) {
                    wm.setStream(in, null, true, which);
                } else {
                    wm.setStream(in);
                }
            } finally {
                try { in.close(); } catch (Exception ignored) {}
            }
            return null;
        } catch (Exception e) {
            String msg = e.getMessage();
            return (msg == null || msg.trim().isEmpty()) ? "Could not set wallpaper" : msg;
        }
    }
}
