package com.example.facerecognitionimages;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import com.example.facerecognitionimages.utils.ClickUtils;
import com.example.facerecognitionimages.utils.UIHelper;
import android.widget.Toast;
import android.util.Log;

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
        Log.d("LoginFlow", "onCreate: App starting, layout setting up");
        setContentView(R.layout.activity_login);

        pinInput = findViewById(R.id.pinInput);
        btnLogin = findViewById(R.id.btnLogin);
        subtitleText = findViewById(R.id.subtitleText);
        
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        checkInitialState();

        btnLogin.setOnClickListener(v -> {
            Log.d("LoginFlow", "btnLogin clicked");
            if (ClickUtils.isFastDoubleClick(v)) {
                Log.d("LoginFlow", "btnLogin click ignored due to fast double click");
                return;
            }
            handleLogin();
        });
    }

    private void checkInitialState() {
        String savedPin = prefs.getString(KEY_PIN, null);
        Log.d("LoginFlow", "checkInitialState: savedPin present = " + (savedPin != null));
        if (savedPin == null) {
            subtitleText.setText("Create an Admin PIN to secure the app");
            btnLogin.setText("Set PIN");
        } else {
            subtitleText.setText("Please enter your admin PIN");
            btnLogin.setText("Login");
        }
    }

    private void handleLogin() {
        Log.d("LoginFlow", "handleLogin started");
        if (pinInput.getText() == null) {
            Log.d("LoginFlow", "PIN validation failed: Input is null");
            pinInput.setError("PIN cannot be empty");
            return;
        }
        String enteredPin = pinInput.getText().toString().trim();
        Log.d("LoginFlow", "PIN entered. Length: " + enteredPin.length());
        
        if (TextUtils.isEmpty(enteredPin)) {
            Log.d("LoginFlow", "PIN validation failed: Input is empty");
            pinInput.setError("PIN cannot be empty");
            return;
        }

        if (!enteredPin.matches("^[0-9]+$")) {
            Log.d("LoginFlow", "PIN validation failed: Input contains non-digits");
            pinInput.setError("PIN must contain only digits");
            return;
        }

        String savedPin = prefs.getString(KEY_PIN, null);

        if (savedPin == null) {
            Log.d("LoginFlow", "Setting new PIN");
            // Setting PIN for the first time
            if (enteredPin.length() < 4) {
                Log.d("LoginFlow", "PIN setting failed: Less than 4 digits");
                pinInput.setError("PIN must be at least 4 digits");
                return;
            }
            prefs.edit().putString(KEY_PIN, enteredPin).apply();
            Log.d("LoginFlow", "New PIN saved successfully");
            UIHelper.showSuccessSnackbar(findViewById(android.R.id.content), "PIN set successfully!");
            navigateToMain();
        } else {
            Log.d("LoginFlow", "Verifying existing PIN");
            // Verifying existing PIN
            if (enteredPin.equals(savedPin)) {
                Log.d("LoginFlow", "PIN validation SUCCESS");
                navigateToMain();
            } else {
                Log.d("LoginFlow", "PIN validation FAILED: Incorrect PIN");
                pinInput.setError("Incorrect PIN");
                pinInput.setText("");
            }
        }
    }

    private void navigateToMain() {
        Log.d("LoginFlow", "Navigating to MainActivity");
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
