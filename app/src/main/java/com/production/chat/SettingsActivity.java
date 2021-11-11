package com.production.chat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToggle();
    }

    private void setupToggle() {
        SharedPreferences sharedPreferences = getSharedPreferences(ConnectService.SETTINGS, MODE_PRIVATE);
        ToggleButton safeMode = findViewById(R.id.safeModeToggle);
        safeMode.setChecked(sharedPreferences.getBoolean(ConnectService.SAFE_MODE, false));
        SharedPreferences.Editor editor = sharedPreferences.edit();
        safeMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                editor.putBoolean(ConnectService.SAFE_MODE, isChecked);
                editor.apply();
            }
        });
    }

    public void backFromSettings(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    public void clearBlockedUsers(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.clear_blocked_confirm_title);
        builder.setMessage(R.string.clear_blocked_confirm_message);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                SharedPreferences blockedUsers = getSharedPreferences(ConnectService.BLOCKED_MACS, MODE_PRIVATE);
                SharedPreferences.Editor editor = blockedUsers.edit();
                editor.clear();
                editor.apply();
                TextView clearNotify = findViewById(R.id.clearNotify);
                clearNotify.setText(getString(R.string.clear_blocked_notify));
            }
        });
        builder.setNegativeButton(android.R.string.no, null);
        builder.show();
    }
}