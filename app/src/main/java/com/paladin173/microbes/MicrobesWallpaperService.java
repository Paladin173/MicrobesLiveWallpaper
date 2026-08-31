package com.paladin173.microbes;

import android.service.wallpaper.WallpaperService;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.paladin173.microbes.render.MicrobesRenderController;
import com.paladin173.microbes.simulation.MicrobeWorld;

public final class MicrobesWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new MicrobesEngine();
    }

    private final class MicrobesEngine extends Engine {
        private final MicrobeWorld world = new MicrobeWorld();
        private final MicrobesTouchHandler touchHandler = new MicrobesTouchHandler(world);
        private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
                (preferences, key) -> MicrobesSettings.apply(
                        MicrobesWallpaperService.this,
                        world
                );
        private MicrobesRenderController renderer;
        private int width;
        private int height;

        MicrobesEngine() {
            setTouchEventsEnabled(true);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            MicrobesSettings.apply(MicrobesWallpaperService.this, world);
            MicrobesSettings.preferences(MicrobesWallpaperService.this)
                    .registerOnSharedPreferenceChangeListener(settingsListener);
            renderer = new MicrobesRenderController(surfaceHolder, world);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (renderer != null) {
                renderer.setVisible(visible);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            renderer.surfaceCreated();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
            renderer.surfaceChanged(width, height);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            renderer.surfaceDestroyed();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(
                float xOffset,
                float yOffset,
                float xStep,
                float yStep,
                int xPixels,
                int yPixels
        ) {
            if (renderer != null) {
                renderer.setOffset(xOffset);
            }
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            touchHandler.onTouch(event, width, height);
            super.onTouchEvent(event);
        }

        @Override
        public void onDestroy() {
            MicrobesSettings.preferences(MicrobesWallpaperService.this)
                    .unregisterOnSharedPreferenceChangeListener(settingsListener);
            if (renderer != null) {
                renderer.destroy();
                renderer = null;
            }
            super.onDestroy();
        }
    }
}
