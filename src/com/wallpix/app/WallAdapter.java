package com.wallpix.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Grid adapter used by both the admin console and the user gallery. */
public class WallAdapter extends BaseAdapter {

    public interface Listener {
        void onWallpaper(Wallpaper w);
    }

    private final Activity act;
    private final List<Wallpaper> items = new ArrayList<>();
    private final LruCache<String, Bitmap> cache;
    private Listener listener;

    public WallAdapter(Activity act) {
        this.act = act;
        cache = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setItems(List<Wallpaper> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Wallpaper getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        Holder h;
        if (v == null) {
            v = act.getLayoutInflater().inflate(R.layout.item_wall, parent, false);
            h = new Holder();
            h.thumb = v.findViewById(R.id.thumb);
            h.title = v.findViewById(R.id.title);
            h.category = v.findViewById(R.id.category);
            h.star = v.findViewById(R.id.star);
            v.setTag(h);
        } else {
            h = (Holder) v.getTag();
        }

        final Wallpaper w = items.get(position);
        h.title.setText(w.title);
        h.category.setText(w.category);
        h.star.setVisibility(w.featured ? View.VISIBLE : View.GONE);

        String cacheKey = (w.remote ? "c:" : "l:") + w.id;
        Bitmap bmp = cache.get(cacheKey);
        if (bmp == null) {
            bmp = loadThumb(w);
            if (bmp != null) cache.put(cacheKey, bmp);
        }
        h.thumb.setImageBitmap(bmp);

        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) listener.onWallpaper(w);
            }
        });
        return v;
    }

    private Bitmap loadThumb(Wallpaper w) {
        File f = w.remote
                ? Cloud.thumbFile(act, w)
                : new File(Store.thumbDir(act), w.thumb);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static class Holder {
        ImageView thumb;
        TextView title;
        TextView category;
        ImageView star;
    }
}
