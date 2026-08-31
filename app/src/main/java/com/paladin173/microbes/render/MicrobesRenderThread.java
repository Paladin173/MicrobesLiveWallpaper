package com.paladin173.microbes.render;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;

import com.paladin173.microbes.simulation.MicrobeWorld;

final class MicrobesRenderThread extends Thread {
    private static final String TAG = "MicrobesRenderer";
    private static final long FRAME_INTERVAL_MS = 16L;

    private final SurfaceHolder holder;
    private final MicrobeWorld world;
    private volatile boolean running = true;
    private volatile int width;
    private volatile int height;
    private volatile float offset = 0.5f;

    MicrobesRenderThread(SurfaceHolder holder, MicrobeWorld world) {
        super("Microbes-Renderer");
        this.holder = holder;
        this.world = world;
    }

    void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    void setOffset(float offset) {
        this.offset = offset;
    }

    void requestStop() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        MicrobesRenderer renderer = null;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) {
                throw eglFailure("Unable to obtain EGL display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw eglFailure("Unable to initialize EGL");
            }

            EGLConfig config = chooseConfig(display);
            context = EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE},
                    0
            );
            if (context == EGL14.EGL_NO_CONTEXT) {
                throw eglFailure("Unable to create EGL context");
            }

            surface = EGL14.eglCreateWindowSurface(
                    display,
                    config,
                    holder,
                    new int[]{EGL14.EGL_NONE},
                    0
            );
            if (surface == EGL14.EGL_NO_SURFACE) {
                throw eglFailure("Unable to create EGL window surface");
            }
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw eglFailure("Unable to make EGL context current");
            }

            renderer = new MicrobesRenderer(world);
            renderer.create();
            long lastFrame = SystemClock.uptimeMillis();
            while (running) {
                long frameStart = SystemClock.uptimeMillis();
                float deltaSeconds = Math.min(0.05f, (frameStart - lastFrame) / 1000f);
                lastFrame = frameStart;
                renderer.draw(width, height, offset, deltaSeconds);
                if (!EGL14.eglSwapBuffers(display, surface)) {
                    throw eglFailure("Unable to swap EGL buffers");
                }
                long remaining = FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - frameStart);
                if (remaining > 0L) {
                    SystemClock.sleep(remaining);
                }
            }
        } catch (RuntimeException error) {
            Log.e(TAG, "Renderer stopped after a fatal EGL or GL error", error);
        } finally {
            if (renderer != null && display != EGL14.EGL_NO_DISPLAY
                    && context != EGL14.EGL_NO_CONTEXT) {
                renderer.release();
            }
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                );
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface);
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                }
                EGL14.eglTerminate(display);
            }
        }
    }

    private static EGLConfig chooseConfig(EGLDisplay display) {
        int[] attributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        boolean selected = EGL14.eglChooseConfig(
                display,
                attributes,
                0,
                configs,
                0,
                configs.length,
                count,
                0
        );
        if (!selected || count[0] == 0 || configs[0] == null) {
            throw eglFailure("No compatible EGL configuration");
        }
        return configs[0];
    }

    private static IllegalStateException eglFailure(String message) {
        return new IllegalStateException(message + ": 0x" + Integer.toHexString(EGL14.eglGetError()));
    }
}
