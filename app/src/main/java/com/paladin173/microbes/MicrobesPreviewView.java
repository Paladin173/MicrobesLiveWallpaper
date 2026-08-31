package com.paladin173.microbes;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.paladin173.microbes.render.MicrobesRenderController;
import com.paladin173.microbes.simulation.MicrobeWorld;

@SuppressLint("ViewConstructor")
final class MicrobesPreviewView extends SurfaceView implements SurfaceHolder.Callback {
    private final MicrobeWorld world;
    private final MicrobesTouchHandler touchHandler;
    private final MicrobesRenderController renderer;

    MicrobesPreviewView(Context context, MicrobeWorld world) {
        super(context);
        this.world = world;
        touchHandler = new MicrobesTouchHandler(world);
        getHolder().addCallback(this);
        renderer = new MicrobesRenderController(getHolder(), world);
        MicrobesSettings.apply(context, world);
    }

    void setRenderingVisible(boolean visible) {
        if (visible) {
            MicrobesSettings.apply(getContext(), world);
        }
        renderer.setVisible(visible);
    }

    void destroy() {
        getHolder().removeCallback(this);
        renderer.destroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        renderer.surfaceCreated();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        renderer.surfaceChanged(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        renderer.surfaceDestroyed();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = touchHandler.onTouch(event, getWidth(), getHeight());
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return handled;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
