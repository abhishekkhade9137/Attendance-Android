package com.example.facerecognitionimages;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show();
                        com.example.facerecognitionimages.utils.BackupUtils.exportBackup(this, uri, new com.example.facerecognitionimages.utils.BackupUtils.BackupCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Backup exported successfully!", Toast.LENGTH_LONG).show());
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Export failed: " + message, Toast.LENGTH_LONG).show());
                            }
                        });
                    }
                }
            });

    private final ActivityResultLauncher<Intent> importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        Toast.makeText(this, "Importing...", Toast.LENGTH_SHORT).show();
                        com.example.facerecognitionimages.utils.BackupUtils.importBackup(this, uri, new com.example.facerecognitionimages.utils.BackupUtils.BackupCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Backup imported successfully! Please restart the app.", Toast.LENGTH_LONG).show());
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Import failed: " + message, Toast.LENGTH_LONG).show());
                            }
                        });
                    }
                }
            });

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
        
        findViewById(R.id.btnExport).setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "facerecognition_backup.zip");
            exportLauncher.launch(intent);
        });
        
        findViewById(R.id.btnImport).setOnClickListener(v -> {
            if (com.example.facerecognitionimages.utils.ClickUtils.isFastDoubleClick()) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            importLauncher.launch(intent);
        });
    }
}
