package com.wallpix.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

/** Full screen wallpaper preview with home / lock / both actions. */
public class PreviewActivity extends Activity {

    public static final String EXTRA_ID = "wallpaper_id";

    private Wallpaper wallpaper;
    private ImageView image, favBtn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_preview);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        String id = getIntent().getStringExtra(EXTRA_ID);
        wallpaper = id == null ? null : findWallpaper(id);
        if (wallpaper == null) {
            finish();
            return;
        }

        image = findViewById(R.id.image);
        TextView title = findViewById(R.id.title);
        TextView meta = findViewById(R.id.meta);
        favBtn = findViewById(R.id.favBtn);

        title.setText(wallpaper.title);
        String metaText = wallpaper.category;
        if (wallpaper.featured) metaText += " • Featured";
        if (wallpaper.remote) metaText += " • Cloud";
        meta.setText(metaText);

        File f = wallpaper.remote ? Cloud.fullFile(this, wallpaper) : Store.imageFile(this, wallpaper);
        if (f.exists()) {
            showImage(f);
        } else if (wallpaper.remote) {
            downloadThenShow();
        }

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        favBtn.setOnClickListener(v -> {
            Store.toggleFav(this, wallpaper.id);
            updateFavIcon();
        });

        findViewById(R.id.setHome).setOnClickListener(v -> set(WallpaperSetter.HOME));
        findViewById(R.id.setLock).setOnClickListener(v -> set(WallpaperSetter.LOCK));
        findViewById(R.id.setBoth).setOnClickListener(v -> set(WallpaperSetter.BOTH));

        updateFavIcon();
    }

    /** Local item first, then the cached cloud catalog. */
    private Wallpaper findWallpaper(String id) {
        Wallpaper w = Store.byId(this, id);
        if (w != null) return w;
        for (Wallpaper cw : Cloud.cachedCatalog(this)) {
            if (cw.id.equals(id)) return cw;
        }
        return null;
    }

    private void showImage(File f) {
        Bitmap bmp = ImageUtil.decodePreview(f,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        image.setImageBitmap(bmp);
    }

    private void downloadThenShow() {
        final ProgressDialog pd = Ui.progress(this, "Downloading wallpaper…");
        new Thread(() -> {
            String err = null;
            try {
                Cloud.ensureFull(this, wallpaper);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String error = err;
            runOnUiThread(() -> {
                pd.dismiss();
                if (error != null) {
                    Ui.toast(this, "Download failed: " + error);
                } else {
                    showImage(Cloud.fullFile(this, wallpaper));
                }
            });
        }).start();
    }

    private void updateFavIcon() {
        boolean fav = Store.isFav(this, wallpaper.id);
        favBtn.setImageResource(fav ? R.drawable.ic_star_fill : R.drawable.ic_star);
        favBtn.setColorFilter(fav
                ? getResources().getColor(R.color.gold, getTheme())
                : getResources().getColor(R.color.muted, getTheme()));
    }

    private void set(int mode) {
        String err = WallpaperSetter.apply(this, wallpaper, mode);
        if (err == null) {
            Ui.toast(this, getString(R.string.wallpaper_set));
        } else {
            Ui.toast(this, "Failed: " + err);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
