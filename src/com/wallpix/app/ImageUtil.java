package com.wallpix.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Bitmap decoding, EXIF rotation and compressed saving. */
public final class ImageUtil {

    private ImageUtil() {}

    /** Decodes {@code src}, fixes rotation, scales the long edge to maxDim and writes JPEG to dst. */
    public static void save(File src, File dst, int maxDim, int quality) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Unsupported or corrupt image");
        }

        int sample = 1;
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        while (longest / (sample * 2) >= maxDim) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(src.getAbsolutePath(), opts);
        if (bmp == null) throw new IOException("Could not decode image");

        bmp = fixRotation(src, bmp);

        int l = Math.max(bmp.getWidth(), bmp.getHeight());
        if (l > maxDim) {
            float ratio = maxDim / (float) l;
            int w = Math.max(1, Math.round(bmp.getWidth() * ratio));
            int h = Math.max(1, Math.round(bmp.getHeight() * ratio));
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }

        FileOutputStream out = new FileOutputStream(dst);
        try {
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
            out.flush();
        } finally {
            try { out.close(); } catch (Exception ignored) {}
            bmp.recycle();
        }
    }

    /** Loads a bitmap that fits comfortably in memory for a full screen preview. */
    public static Bitmap decodePreview(File f, int maxW, int maxH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0) return null;

        int sample = 1;
        while ((bounds.outWidth / (sample * 2)) >= maxW
                && (bounds.outHeight / (sample * 2)) >= maxH) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    private static Bitmap fixRotation(File src, Bitmap bmp) {
        int orientation;
        try {
            ExifInterface exif = new ExifInterface(src.getAbsolutePath());
            orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return bmp;
        }

        Matrix m = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: m.postRotate(90); break;
            case ExifInterface.ORIENTATION_ROTATE_180: m.postRotate(180); break;
            case ExifInterface.ORIENTATION_ROTATE_270: m.postRotate(270); break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: m.postScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE: m.postRotate(90); m.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_TRANSVERSE: m.postRotate(270); m.postScale(-1, 1); break;
            default: return bmp;
        }

        Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (rotated != bmp) bmp.recycle();
        return rotated;
    }
}
