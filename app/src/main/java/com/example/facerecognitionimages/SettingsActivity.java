package com.example.facerecognitionimages;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREF_NAME = "AppPrefs";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_HAPTICS = "haptics";
    public static final String KEY_COOLDOWN = "cooldown";

    private SwitchMaterial switchTheme;
    private SwitchMaterial switchHaptics;
    private Slider sliderCooldown;
    private TextView tvCooldownValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchTheme = findViewById(R.id.switchTheme);
        switchHaptics = findViewById(R.id.switchHaptics);
        sliderCooldown = findViewById(R.id.sliderCooldown);
        tvCooldownValue = findViewById(R.id.tvCooldownValue);
        ImageButton btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        boolean isHapticsOn = prefs.getBoolean(KEY_HAPTICS, true);
        int cooldown = prefs.getInt(KEY_COOLDOWN, 3);

        switchTheme.setChecked(isDarkMode);
        switchHaptics.setChecked(isHapticsOn);
        sliderCooldown.setValue(cooldown);
        tvCooldownValue.setText(cooldown + "s");

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        switchHaptics.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_HAPTICS, isChecked).apply();
        });

        sliderCooldown.addOnChangeListener((slider, value, fromUser) -> {
            int intValue = (int) value;
            tvCooldownValue.setText(intValue + "s");
            prefs.edit().putInt(KEY_COOLDOWN, intValue).apply();
        });
    }
}
