package com.paladin173.microbes;

import android.util.SparseArray;
import android.view.MotionEvent;

import com.paladin173.microbes.simulation.MicrobeWorld;

final class MicrobesTouchHandler {
    private static final float TAP_DISTANCE_SQUARED = 0.0016f;

    private final MicrobeWorld world;
    private final SparseArray<TouchStart> starts = new SparseArray<>();

    MicrobesTouchHandler(MicrobeWorld world) {
        this.world = world;
    }

    boolean onTouch(MotionEvent event, int width, int height) {
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int pointerId = event.getPointerId(actionIndex);
            float x = world.screenToWorldX(normalize(event.getX(actionIndex), width));
            float y = normalize(event.getY(actionIndex), height);
            starts.put(pointerId, new TouchStart(x, y));
            world.motion(x, y);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int index = 0; index < event.getPointerCount(); index++) {
                world.motion(
                        world.screenToWorldX(normalize(event.getX(index), width)),
                        normalize(event.getY(index), height)
                );
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int pointerId = event.getPointerId(actionIndex);
            float x = world.screenToWorldX(normalize(event.getX(actionIndex), width));
            float y = normalize(event.getY(actionIndex), height);
            TouchStart start = starts.get(pointerId);
            if (start != null) {
                float dx = x - start.x;
                float dy = y - start.y;
                if (dx * dx + dy * dy <= TAP_DISTANCE_SQUARED) {
                    world.feed(x, y);
                }
            }
            starts.remove(pointerId);
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            starts.clear();
            return true;
        }
        return false;
    }

    private static float normalize(float value, int extent) {
        return Math.max(0f, Math.min(1f, value / Math.max(1, extent)));
    }

    private static final class TouchStart {
        final float x;
        final float y;

        TouchStart(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
