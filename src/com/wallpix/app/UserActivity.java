package com.wallpix.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** User gallery: browse, filter, favorite and preview wallpapers added by the admin. */
public class UserActivity extends Activity {

    private final List<Wallpaper> local = new ArrayList<>();
    private final List<Wallpaper> cloud = new ArrayList<>();
    private final List<Wallpaper> shown = new ArrayList<>();

    private WallAdapter adapter;
    private GridView grid;
    private LinearLayout chips, empty;
    private TextView count;
    private ImageView favBtn, cloudBtn;

    private String category = "All";
    private boolean favOnly;
    private boolean syncing, cloudErrorShown;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_user);

        grid = findViewById(R.id.grid);
        chips = findViewById(R.id.chips);
        empty = findViewById(R.id.empty);
        count = findViewById(R.id.count);
        favBtn = findViewById(R.id.favBtn);
        cloudBtn = findViewById(R.id.cloudBtn);

        adapter = new WallAdapter(this);
        adapter.setListener(this::openPreview);
        grid.setAdapter(adapter);
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            showQuickMenu(adapter.getItem(pos));
            return true;
        });

        favBtn.setOnClickListener(v -> {
            favOnly = !favOnly;
            if (favOnly) category = "All";
            apply();
        });

        cloudBtn.setOnClickListener(v -> Cloud.showSettings(this, false, this::refreshCloud));

        View openAdmin = findViewById(R.id.openAdmin);
        if (adminAvailable()) {
            openAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        } else {
            openAdmin.setVisibility(View.GONE);
        }
    }

    /** True only in the combined/admin package where the admin activity exists. */
    private boolean adminAvailable() {
        try {
            getPackageManager().getActivityInfo(
                    new android.content.ComponentName(this, AdminActivity.class), 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        local.clear();
        local.addAll(Store.all(this));

        cloud.clear();
        if (Cloud.enabled(this)) cloud.addAll(Cloud.cachedCatalog(this));

        apply();
        if (Cloud.enabled(this)) refreshCloud();
        maybePromptCloud();
    }

    /** First run of the user-only app: ask for the cloud server once. */
    private void maybePromptCloud() {
        if (Cloud.enabled(this) || !local.isEmpty()) return;
        android.content.SharedPreferences p =
                getSharedPreferences(Store.PREFS, MODE_PRIVATE);
        if (p.getBoolean("cloud_prompted", false)) return;
        p.edit().putBoolean("cloud_prompted", true).apply();
        Cloud.showSettings(this, false, this::refreshCloud);
    }

    // ------------------------------------------------------------- cloud

    private void refreshCloud() {
        if (!Cloud.enabled(this)) {
            cloud.clear();
            syncing = false;
            apply();
            return;
        }
        if (syncing) return;

        syncing = true;
        apply();

        new Thread(() -> {
            List<Wallpaper> fetched = null;
            String error = null;
            try {
                fetched = Cloud.fetchCatalog(this);
                for (Wallpaper w : fetched) {
                    if (!Cloud.thumbFile(this, w).exists()) {
                        try { Cloud.ensureThumb(this, w); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "unreachable" : e.getMessage();
            }

            final List<Wallpaper> result = fetched;
            final String err = error;
            runOnUiThread(() -> {
                syncing = false;
                if (result != null) {
                    cloud.clear();
                    cloud.addAll(result);
                    cloudErrorShown = false;
                } else if (!cloudErrorShown) {
                    cloudErrorShown = true;
                    Ui.toast(this, "Cloud: " + err);
                }
                apply();
            });
        }).start();
    }

    // ------------------------------------------------------------- lists

    private void apply() {
        shown.clear();
        Set<String> ids = new HashSet<>();

        for (Wallpaper w : local) {
            ids.add(w.id);
            if (matches(w)) shown.add(w);
        }
        for (Wallpaper w : cloud) {
            if (ids.contains(w.id)) continue;
            if (matches(w)) shown.add(w);
        }

        Collections.sort(shown, (a, b) -> {
            if (a.featured != b.featured) return a.featured ? -1 : 1;
            return Long.compare(b.created, a.created);
        });

        adapter.setItems(shown);

        if (syncing) {
            count.setText("Syncing with cloud…");
        } else {
            int n = shown.size();
            String label = n + (n == 1 ? " wallpaper" : " wallpapers");
            if (favOnly) label += " • favorites";
            if (Cloud.enabled(this)) label += " • cloud";
            count.setText(label);
        }

        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        grid.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);

        favBtn.setColorFilter(favOnly
                ? getResources().getColor(R.color.gold, getTheme())
                : getResources().getColor(R.color.muted, getTheme()));
        cloudBtn.setColorFilter(Cloud.enabled(this)
                ? getResources().getColor(R.color.accent2, getTheme())
                : getResources().getColor(R.color.muted, getTheme()));

        rebuildChips();
    }

    private boolean matches(Wallpaper w) {
        if (favOnly) return Store.isFav(this, w.id);
        return "All".equals(category) || category.equals(w.category);
    }

    private void rebuildChips() {
        chips.removeAllViews();

        chips.addView(filterChip("All", !favOnly && "All".equals(category), () -> {
            category = "All";
            favOnly = false;
            apply();
        }));

        Set<String> cats = new TreeSet<>();
        for (Wallpaper w : local) cats.add(w.category);
        for (Wallpaper w : cloud) cats.add(w.category);
        for (String cat : cats) {
            chips.addView(filterChip(cat, !favOnly && cat.equals(category), () -> {
                category = cat;
                favOnly = false;
                apply();
            }));
        }

        chips.addView(filterChip("Favorites", favOnly, () -> {
            favOnly = true;
            category = "All";
            apply();
        }));
    }

    private TextView filterChip(String label, boolean selected, Runnable onClick) {
        TextView t = Ui.chip(this, label);
        t.setSelected(selected);
        t.setOnClickListener(v -> onClick.run());
        return t;
    }

    // ----------------------------------------------------------- actions

    private void openPreview(Wallpaper w) {
        Intent i = new Intent(this, PreviewActivity.class);
        i.putExtra(PreviewActivity.EXTRA_ID, w.id);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showQuickMenu(Wallpaper w) {
        boolean fav = Store.isFav(this, w.id);
        String[] options = {
                "Open",
                fav ? "Remove favorite" : "Add to favorites",
                "Set home screen",
                "Set lock screen",
                "Set both"
        };
        new android.app.AlertDialog.Builder(this)
                .setTitle(w.title)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            openPreview(w);
                            break;
                        case 1:
                            Store.toggleFav(this, w.id);
                            apply();
                            break;
                        case 2:
                            applyWallpaper(w, WallpaperSetter.HOME);
                            break;
                        case 3:
                            applyWallpaper(w, WallpaperSetter.LOCK);
                            break;
                        default:
                            applyWallpaper(w, WallpaperSetter.BOTH);
                    }
                })
                .show();
    }

    private void applyWallpaper(Wallpaper w, int mode) {
        if (!w.remote || Cloud.fullFile(this, w).exists()) {
            setNow(w, mode);
            return;
        }

        final ProgressDialog pd = Ui.progress(this, "Downloading wallpaper…");
        new Thread(() -> {
            String err = null;
            try {
                Cloud.ensureFull(this, w);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String error = err;
            runOnUiThread(() -> {
                pd.dismiss();
                if (error != null) {
                    Ui.toast(this, "Download failed: " + error);
                } else {
                    setNow(w, mode);
                }
            });
        }).start();
    }

    private void setNow(Wallpaper w, int mode) {
        String err = WallpaperSetter.apply(this, w, mode);
        if (err == null) {
            Ui.toast(this, getString(R.string.wallpaper_set));
        } else {
            Ui.toast(this, "Failed: " + err);
        }
    }
}
