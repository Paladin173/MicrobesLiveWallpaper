package com.paladin173.microbes;

import android.content.Context;
import android.content.SharedPreferences;

import com.paladin173.microbes.simulation.MicrobeWorld;

final class MicrobesSettings {
    static final String PREFERENCES = "microbes_settings";
    static final String MOVEMENT_SPEED = "movement_speed";
    static final String LIFECYCLE_SPEED = "lifecycle_speed";
    static final String BACKGROUND_FOG = "background_fog";
    static final int DEFAULT_MOVEMENT_SPEED = 60;
    static final int DEFAULT_LIFECYCLE_SPEED = 100;

    private MicrobesSettings() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static void apply(Context context, MicrobeWorld world) {
        SharedPreferences preferences = preferences(context);
        world.setMovementScale(
                preferences.getInt(MOVEMENT_SPEED, DEFAULT_MOVEMENT_SPEED) / 100f
        );
        world.setLifecycleScale(
                preferences.getInt(LIFECYCLE_SPEED, DEFAULT_LIFECYCLE_SPEED) / 100f
        );
        world.setDecorationsEnabled(preferences.getBoolean(BACKGROUND_FOG, true));
    }
}
