package com.paladin173.microbes;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Handler;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** First modern baseline for the original Microbes live-wallpaper concept. */
public final class MicrobesWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new MicrobesEngine();
    }

    private final class MicrobesEngine extends Engine {
        private final Handler handler = new Handler();
        private final Random random = new Random(0x4D4943524FL);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Microbe> microbes = new ArrayList<>();
        private final Runnable drawTask = this::drawFrame;
        private boolean visible;
        private int width;
        private int height;
        private float xOffset = 0.5f;
        private long lastFrame;

        MicrobesEngine() {
            setTouchEventsEnabled(true);
            for (int i = 0; i < 28; i++) {
                microbes.add(new Microbe(random));
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) drawFrame(); else handler.removeCallbacks(drawTask);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            this.width = width;
            this.height = height;
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawTask);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
                                     int xPixels, int yPixels) {
            this.xOffset = xOffset;
            drawFrame();
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                for (Microbe microbe : microbes) microbe.nudge(event.getX() / Math.max(1, width));
                drawFrame();
            }
            super.onTouchEvent(event);
        }

        private void drawFrame() {
            handler.removeCallbacks(drawTask);
            if (!visible && width == 0) return;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                long now = SystemClock.uptimeMillis();
                float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
                lastFrame = now;
                canvas.drawColor(Color.rgb(5, 8, 24));
                paint.setStyle(Paint.Style.FILL);
                for (Microbe microbe : microbes) {
                    microbe.update(dt);
                    microbe.draw(canvas, paint, width, height, xOffset);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            if (visible) handler.postDelayed(drawTask, 33L);
        }
    }

    private static final class Microbe {
        private final PointF position = new PointF();
        private float phase;
        private float speed;
        private float size;
        private int color;

        Microbe(Random random) {
            position.set(random.nextFloat(), random.nextFloat());
            phase = random.nextFloat() * 6.28f;
            speed = 0.015f + random.nextFloat() * 0.035f;
            size = 7f + random.nextFloat() * 15f;
            color = Color.rgb(120 + random.nextInt(100), 180 + random.nextInt(70), 220 + random.nextInt(35));
        }

        void update(float dt) {
            phase += dt * speed * 18f;
            position.x += (float) Math.cos(phase * 0.7f) * dt * speed;
            position.y += (float) Math.sin(phase) * dt * speed;
            if (position.x < -0.1f) position.x = 1.1f;
            if (position.x > 1.1f) position.x = -0.1f;
            if (position.y < -0.1f) position.y = 1.1f;
            if (position.y > 1.1f) position.y = -0.1f;
        }

        void nudge(float touchX) {
            position.x += (position.x < touchX ? -0.02f : 0.02f);
        }

        void draw(Canvas canvas, Paint paint, int width, int height, float xOffset) {
            float x = (position.x - (xOffset - 0.5f) * 0.25f) * width;
            float y = position.y * height;
            float pulse = 1f + 0.18f * (float) Math.sin(phase * 2f);
            paint.setColor(color);
            paint.setAlpha(190);
            canvas.drawCircle(x, y, size * pulse, paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha(210);
            canvas.drawCircle(x - size * 0.25f, y - size * 0.25f, size * 0.18f, paint);
        }
    }
}

