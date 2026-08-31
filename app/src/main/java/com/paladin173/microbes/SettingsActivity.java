package com.paladin173.microbes;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = MicrobesSettings.preferences(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = dp(24);
        int verticalPadding = dp(24);
        root.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                topInset = systemBars.top;
                bottomInset = systemBars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(
                    horizontalPadding,
                    verticalPadding + topInset,
                    horizontalPadding,
                    verticalPadding + bottomInset
            );
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(R.string.settings);
        title.setTextSize(28f);
        root.addView(title);

        addSlider(
                root,
                preferences,
                R.string.movement_speed,
                MicrobesSettings.MOVEMENT_SPEED,
                25,
                150,
                MicrobesSettings.DEFAULT_MOVEMENT_SPEED
        );
        addSlider(
                root,
                preferences,
                R.string.lifecycle_speed,
                MicrobesSettings.LIFECYCLE_SPEED,
                50,
                200,
                MicrobesSettings.DEFAULT_LIFECYCLE_SPEED
        );

        Switch backgroundFog = new Switch(this);
        backgroundFog.setText(R.string.background_fog);
        backgroundFog.setTextSize(18f);
        backgroundFog.setChecked(preferences.getBoolean(
                MicrobesSettings.BACKGROUND_FOG,
                true
        ));
        backgroundFog.setOnCheckedChangeListener((button, enabled) ->
                preferences.edit()
                        .putBoolean(MicrobesSettings.BACKGROUND_FOG, enabled)
                        .apply()
        );
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        switchParams.topMargin = dp(28);
        root.addView(backgroundFog, switchParams);

        Button setWallpaper = new Button(this);
        setWallpaper.setText(R.string.set_wallpaper);
        setWallpaper.setOnClickListener(view -> openWallpaperPreview());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.topMargin = dp(40);
        root.addView(setWallpaper, buttonParams);

        Button updates = new Button(this);
        updates.setText(R.string.updates_on_github);
        updates.setOnClickListener(view -> openProjectPage());
        LinearLayout.LayoutParams updatesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        updatesParams.topMargin = dp(16);
        root.addView(updates, updatesParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scrollView);
    }

    private void openWallpaperPreview() {
        ComponentName component = new ComponentName(this, MicrobesWallpaperService.class);
        Intent previewIntent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
        try {
            startActivity(previewIntent);
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        }
    }

    private void openProjectPage() {
        Intent browserIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.project_url))
        );
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.no_browser_available, Toast.LENGTH_LONG).show();
        }
    }

    private void addSlider(
            LinearLayout root,
            SharedPreferences preferences,
            int labelResource,
            String preferenceKey,
            int minimum,
            int maximum,
            int defaultValue
    ) {
        TextView label = new TextView(this);
        label.setTextSize(18f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.topMargin = dp(32);
        root.addView(label, labelParams);

        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum - minimum);
        int currentValue = preferences.getInt(preferenceKey, defaultValue);
        slider.setProgress(currentValue - minimum);
        updateLabel(label, labelResource, currentValue);
        updateSliderDescription(slider, labelResource, currentValue);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = minimum + progress;
                updateLabel(label, labelResource, value);
                updateSliderDescription(seekBar, labelResource, value);
                if (fromUser) {
                    preferences.edit().putInt(preferenceKey, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void updateLabel(TextView label, int labelResource, int value) {
        label.setText(getString(R.string.percentage_setting, getString(labelResource), value));
    }

    private void updateSliderDescription(SeekBar slider, int labelResource, int value) {
        slider.setContentDescription(
                getString(R.string.percentage_setting, getString(labelResource), value)
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
