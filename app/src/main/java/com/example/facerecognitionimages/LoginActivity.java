package com.example.facerecognitionimages;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import com.example.facerecognitionimages.utils.UIHelper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;


import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText pinInput;
    private Button btnLogin;
    private TextView subtitleText;
    private SharedPreferences prefs;

    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_PIN = "AdminPin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        pinInput = findViewById(R.id.pinInput);
        btnLogin = findViewById(R.id.btnLogin);
        subtitleText = findViewById(R.id.subtitleText);
        
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        checkInitialState();

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void checkInitialState() {
        String savedPin = prefs.getString(KEY_PIN, null);
        if (savedPin == null) {
            subtitleText.setText("Create an Admin PIN to secure the app");
            btnLogin.setText("Set PIN");
        } else {
            subtitleText.setText("Please enter your admin PIN");
            btnLogin.setText("Login");
        }
    }

    private void handleLogin() {
        String enteredPin = pinInput.getText().toString().trim();
        if (TextUtils.isEmpty(enteredPin)) {
            pinInput.setError("PIN cannot be empty");
            return;
        }

        String savedPin = prefs.getString(KEY_PIN, null);

        if (savedPin == null) {
            // Setting PIN for the first time
            if (enteredPin.length() < 4) {
                pinInput.setError("PIN must be at least 4 digits");
                return;
            }
            prefs.edit().putString(KEY_PIN, enteredPin).apply();
            UIHelper.showSuccessSnackbar(findViewById(android.R.id.content), "PIN set successfully!");
            navigateToMain();
        } else {
            // Verifying existing PIN
            if (enteredPin.equals(savedPin)) {
                navigateToMain();
            } else {
                pinInput.setError("Incorrect PIN");
                pinInput.setText("");
            }
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
