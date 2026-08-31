package com.paladin173.microbes.render;

import android.util.Log;
import android.view.SurfaceHolder;

import com.paladin173.microbes.simulation.MicrobeWorld;

public final class MicrobesRenderController {
    private static final String TAG = "MicrobesRenderer";
    private static final long STOP_TIMEOUT_MS = 2_000L;
    private final SurfaceHolder holder;
    private final MicrobeWorld world;
    private boolean destroyed;
    private boolean surfaceReady;
    private boolean visible;
    private int width;
    private int height;
    private float offset = 0.5f;
    private MicrobesRenderThread thread;
    private Thread stopMonitor;

    public MicrobesRenderController(SurfaceHolder holder, MicrobeWorld world) {
        this.holder = holder;
        this.world = world;
    }

    public synchronized void setVisible(boolean visible) {
        this.visible = visible;
        reconcileRendererLocked();
    }

    public synchronized void surfaceCreated() {
        surfaceReady = true;
        reconcileRendererLocked();
    }

    public synchronized void surfaceChanged(int width, int height) {
        this.width = width;
        this.height = height;
        if (thread != null) {
            thread.setSize(width, height);
        }
    }

    public synchronized void surfaceDestroyed() {
        surfaceReady = false;
        stopRendererLocked();
    }

    public synchronized void setOffset(float offset) {
        this.offset = offset;
        if (thread != null) {
            thread.setOffset(offset);
        }
    }

    public synchronized void destroy() {
        destroyed = true;
        visible = false;
        surfaceReady = false;
        stopRendererLocked();
    }

    private void reconcileRendererLocked() {
        if (thread != null && !thread.isAlive()) {
            thread = null;
        }
        if (!destroyed && visible && surfaceReady) {
            if (thread == null) {
                thread = new MicrobesRenderThread(holder, world);
                thread.setSize(width, height);
                thread.setOffset(offset);
                thread.start();
            }
        } else {
            stopRendererLocked();
        }
    }

    private void stopRendererLocked() {
        if (thread == null) {
            return;
        }
        if (stopMonitor != null && stopMonitor.isAlive()) {
            return;
        }

        MicrobesRenderThread stoppingThread = thread;
        stoppingThread.requestStop();
        boolean interrupted = false;
        try {
            stoppingThread.join(STOP_TIMEOUT_MS);
        } catch (InterruptedException error) {
            interrupted = true;
        }
        if (stoppingThread.isAlive()) {
            Log.e(TAG, "Renderer did not stop within " + STOP_TIMEOUT_MS + " ms");
            monitorRendererExitLocked(stoppingThread);
        } else {
            thread = null;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void monitorRendererExitLocked(MicrobesRenderThread stoppingThread) {
        if (stopMonitor != null && stopMonitor.isAlive()) {
            return;
        }
        stopMonitor = new Thread(() -> {
            boolean interrupted = false;
            while (stoppingThread.isAlive()) {
                try {
                    stoppingThread.join();
                } catch (InterruptedException error) {
                    interrupted = true;
                }
            }
            synchronized (MicrobesRenderController.this) {
                if (thread == stoppingThread) {
                    thread = null;
                    reconcileRendererLocked();
                }
                stopMonitor = null;
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "Microbes-RendererMonitor");
        stopMonitor.setDaemon(true);
        stopMonitor.start();
    }
}
