package com.wallpix.app;

import android.app.ProgressDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Tiny UI helpers shared by both panels. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static ProgressDialog progress(Context c, String msg) {
        ProgressDialog p = new ProgressDialog(c);
        p.setMessage(msg);
        p.setCancelable(false);
        p.show();
        return p;
    }

    /** Rounded filter chip; call setSelected(true) to highlight it. */
    public static TextView chip(Context c, String label) {
        TextView t = new TextView(c);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(c.getResources().getColorStateList(R.color.chip_text));
        t.setBackgroundResource(R.drawable.chip_bg);
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setFocusable(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }
}
