package com.paladin173.microbes;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.paladin173.microbes.simulation.MicrobeWorld;

public final class MainActivity extends Activity {
    private MicrobesPreviewView preview;
    private MicrobeWorld world;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Object retained = getLastNonConfigurationInstance();
        world = retained instanceof MicrobeWorld
                ? (MicrobeWorld) retained
                : new MicrobeWorld();
        preview = new MicrobesPreviewView(this, world);
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setColorFilter(Color.LTGRAY);
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setAlpha(0.65f);
        settings.setContentDescription(getString(R.string.settings));
        settings.setPadding(dp(12), dp(12), dp(12), dp(12));
        settings.setOnClickListener(view -> startActivity(
                new Intent(this, SettingsActivity.class)
        ));

        FrameLayout root = new FrameLayout(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        int margin = dp(24);
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                Gravity.TOP | Gravity.END
        );
        settingsParams.setMargins(margin, margin, margin, margin);
        root.addView(settings, settingsParams);
        applyTopSystemBarInset(settings, margin);

        setContentView(root);
    }

    @Override
    protected void onStart() {
        super.onStart();
        preview.setRenderingVisible(true);
        preview.setKeepScreenOn(true);
    }

    @Override
    protected void onStop() {
        preview.setKeepScreenOn(false);
        preview.setRenderingVisible(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        preview.destroy();
        super.onDestroy();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return world;
    }

    private void applyTopSystemBarInset(View view, int baseMargin) {
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int topInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                topInset = insets.getInsets(WindowInsets.Type.systemBars()).top;
            } else {
                topInset = insets.getSystemWindowInsetTop();
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) target.getLayoutParams();
            params.topMargin = baseMargin + topInset;
            target.setLayoutParams(params);
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
