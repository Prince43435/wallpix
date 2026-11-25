package com.wallpix.app;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** ImageView clipped to a rounded rectangle — used for wallpaper cards. */
public class RoundImageView extends ImageView {

    private final float radius;

    public RoundImageView(Context c) {
        this(c, null);
    }

    public RoundImageView(Context c, AttributeSet attrs) {
        super(c, attrs);
        radius = 18f * getResources().getDisplayMetrics().density;
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }
}
