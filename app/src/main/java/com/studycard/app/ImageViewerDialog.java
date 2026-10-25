package com.studycard.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Görseli tam ekranda gösterir; iki parmakla yakınlaştırma ve sürükleme destekler. */
public final class ImageViewerDialog {

    private ImageViewerDialog() {
    }

    public static void show(Activity a, String imageName) {
        Dialog dlg = new Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window w = dlg.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        FrameLayout root = new FrameLayout(a);
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        root.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView close = new ImageView(a);
        int s = Ui.dp(a, 48);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(s, s);
        clp.topMargin = Ui.dp(a, 36);
        clp.leftMargin = Ui.dp(a, 12);
        close.setLayoutParams(clp);
        close.setImageResource(R.drawable.ic_close);
        close.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        int p = Ui.dp(a, 12);
        close.setPadding(p, p, p, p);
        close.setBackgroundResource(R.drawable.bg_ripple_round);
        close.setOnClickListener(v -> dlg.dismiss());
        root.addView(close);

        dlg.setContentView(root);
        dlg.show();

        new Thread(() -> {
            int side = Math.max(a.getResources().getDisplayMetrics().widthPixels,
                    a.getResources().getDisplayMetrics().heightPixels);
            Bitmap b = ImageStore.loadBitmap(a, imageName, side);
            a.runOnUiThread(() -> {
                if (b == null || !dlg.isShowing()) return;
                iv.setImageBitmap(b);
                iv.post(() -> new ZoomHandler(iv, b.getWidth(), b.getHeight()));
            });
        }).start();
    }

    /** Basit yakınlaştırma / kaydırma. */
    private static final class ZoomHandler {
        private final ImageView iv;
        private final Matrix m = new Matrix();
        private final float bw, bh;
        private float baseScale, scale = 1f;
        private float lastX, lastY;
        private boolean dragging;

        ZoomHandler(ImageView iv, int bw, int bh) {
            this.iv = iv;
            this.bw = bw;
            this.bh = bh;
            fit();
            ScaleGestureDetector sgd = new ScaleGestureDetector(iv.getContext(),
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScale(ScaleGestureDetector d) {
                            float f = d.getScaleFactor();
                            float ns = Math.max(1f, Math.min(scale * f, 6f));
                            f = ns / scale;
                            scale = ns;
                            m.postScale(f, f, d.getFocusX(), d.getFocusY());
                            clamp();
                            return true;
                        }
                    });
            iv.setOnTouchListener((v, e) -> {
                sgd.onTouchEvent(e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastX = e.getX();
                        lastY = e.getY();
                        dragging = true;
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        dragging = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (dragging && !sgd.isInProgress() && e.getPointerCount() == 1) {
                            m.postTranslate(e.getX() - lastX, e.getY() - lastY);
                            clamp();
                        }
                        lastX = e.getX();
                        lastY = e.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        break;
                }
                return true;
            });
        }

        private void fit() {
            float vw = iv.getWidth(), vh = iv.getHeight();
            baseScale = Math.min(vw / bw, vh / bh);
            m.reset();
            m.postScale(baseScale, baseScale);
            m.postTranslate((vw - bw * baseScale) / 2f, (vh - bh * baseScale) / 2f);
            scale = 1f;
            iv.setImageMatrix(m);
        }

        private void clamp() {
            RectF r = new RectF(0, 0, bw, bh);
            m.mapRect(r);
            float vw = iv.getWidth(), vh = iv.getHeight();
            float dx = 0, dy = 0;
            if (r.width() <= vw) dx = (vw - r.width()) / 2f - r.left;
            else if (r.left > 0) dx = -r.left;
            else if (r.right < vw) dx = vw - r.right;
            if (r.height() <= vh) dy = (vh - r.height()) / 2f - r.top;
            else if (r.top > 0) dy = -r.top;
            else if (r.bottom < vh) dy = vh - r.bottom;
            m.postTranslate(dx, dy);
            iv.setImageMatrix(m);
        }
    }
}
