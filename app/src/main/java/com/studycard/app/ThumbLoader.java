package com.studycard.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Görselleri arka planda küçültüp yükler ve bellekte önbelleğe alır. */
public final class ThumbLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ThumbLoader() {
    }

    public static void load(Context c, String name, int sidePx, ImageView target) {
        final String key = name + "@" + sidePx;
        target.setTag(R.id.tag_image_key, key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageDrawable(null);
        final Context app = c.getApplicationContext();
        POOL.execute(() -> {
            Bitmap b = ImageStore.loadBitmap(app, name, sidePx);
            if (b == null) return;
            CACHE.put(key, b);
            MAIN.post(() -> {
                if (key.equals(target.getTag(R.id.tag_image_key))) target.setImageBitmap(b);
            });
        });
    }

    public static void evict(String name) {
        CACHE.evictAll();
    }
}
