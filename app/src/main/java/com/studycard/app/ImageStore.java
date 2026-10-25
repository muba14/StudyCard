package com.studycard.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 * Not görsellerini uygulamanın özel klasöründe (files/images) saklar.
 * Görseller içe aktarılırken küçültülüp döndürme bilgisine göre düzeltilir.
 */
public final class ImageStore {

    private static final int MAX_SIDE = 2048;
    private static final Random RANDOM = new Random();

    private ImageStore() {
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "images");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File file(Context c, String name) {
        return new File(dir(c), name);
    }

    public static String newName() {
        return "img_" + System.currentTimeMillis() + "_" + Integer.toHexString(RANDOM.nextInt(0xFFFFFF)) + ".jpg";
    }

    /** Galeriden seçilen görseli kopyalar. Başarısızsa null döner. */
    public static String importUri(Context c, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            int rotation = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in != null) rotation = rotationOf(new ExifInterface(in));
            } catch (Exception ignored) {
            }

            String name = newName();
            writeJpeg(scaleAndRotate(bmp, rotation), file(c, name));
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    /** Kameranın yazdığı dosyayı yerinde küçültür ve düzeltir. */
    public static boolean processCameraFile(Context c, String name) {
        File f = file(c, name);
        if (!f.exists() || f.length() == 0) return false;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE);
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bmp == null) return false;
            int rotation = rotationOf(new ExifInterface(f.getAbsolutePath()));
            writeJpeg(scaleAndRotate(bmp, rotation), f);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Listeler ve bildirimler için küçük boyutlu görsel yükler. */
    public static Bitmap loadBitmap(Context c, String name, int reqSide) {
        File f = file(c, name);
        if (!f.exists()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSide);
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    public static void delete(Context c, String name) {
        if (name == null) return;
        File f = file(c, name);
        if (f.exists()) f.delete();
    }

    // ---- yardımcılar ----

    private static int sampleSize(int w, int h, int maxSide) {
        int s = 1;
        while (Math.max(w, h) / (s * 2) >= maxSide) s *= 2;
        return s;
    }

    private static int rotationOf(ExifInterface exif) {
        int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (o) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    private static Bitmap scaleAndRotate(Bitmap src, int rotation) {
        int w = src.getWidth(), h = src.getHeight();
        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(w, h));
        if (scale >= 1f && rotation == 0) return src;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (rotation != 0) m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, w, h, m, true);
        if (out != src) src.recycle();
        return out;
    }

    private static void writeJpeg(Bitmap bmp, File target) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            copy(tmp, target);
            tmp.delete();
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }
}
