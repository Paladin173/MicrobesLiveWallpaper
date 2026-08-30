package com.paladin173.microbes;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
                canvas.drawColor(Color.BLACK);
                drawAtmosphere(canvas);
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

        private void drawAtmosphere(Canvas canvas) {
            float[] centers = {0.18f, 0.55f, 0.88f};
            float[] heights = {0.18f, 0.78f, 0.42f};
            for (int i = 0; i < centers.length; i++) {
                float radius = Math.max(width, height) * 0.42f;
                glowPaint.setShader(new RadialGradient(
                        centers[i] * width, heights[i] * height, radius,
                        Color.argb(48, 18, 36, 130), Color.TRANSPARENT, Shader.TileMode.CLAMP));
                canvas.drawCircle(centers[i] * width, heights[i] * height, radius, glowPaint);
            }
            glowPaint.setShader(null);
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
            int[] palette = {
                    Color.rgb(255, 70, 70), Color.rgb(255, 205, 45),
                    Color.rgb(40, 220, 135), Color.rgb(55, 125, 255)
            };
            color = palette[random.nextInt(palette.length)];
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
            float radius = size * pulse;
            paint.setShader(new RadialGradient(x, y, radius * 2.8f,
                    Color.argb(115, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, radius * 2.8f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1.5f, radius * 0.16f));
            paint.setColor(color);
            paint.setAlpha(235);
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(245);
            canvas.drawCircle(x, y, Math.max(1.5f, radius * 0.18f), paint);
            paint.setColor(Color.WHITE);
            paint.setAlpha(220);
            canvas.drawCircle(x - radius * 0.3f, y - radius * 0.3f, Math.max(1f, radius * 0.1f), paint);
        }
    }
}
