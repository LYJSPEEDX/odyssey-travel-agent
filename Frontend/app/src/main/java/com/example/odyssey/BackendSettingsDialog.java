package com.example.odyssey;

import android.text.InputType;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

final class BackendSettingsDialog {
    private final AppCompatActivity activity;
    private final Runnable updated;
    BackendSettingsDialog(AppCompatActivity activity, Runnable updated) { this.activity = activity; this.updated = updated; }
    void show() {
        BackendConfig config = new BackendConfig(activity);
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * activity.getResources().getDisplayMetrics().density); form.setPadding(pad, 0, pad, 0);
        AppCompatEditText url = new AppCompatEditText(activity); url.setSingleLine();
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint(BuildConfig.DEBUG ? BackendConfig.DEFAULT_URL : "https://your-server.example");
        url.setContentDescription("Backend server URL"); url.setTag("backend-url"); url.setText(config.url()); form.addView(url);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle("Live demo server")
                .setMessage(BuildConfig.DEBUG
                        ? "Android emulator: http://10.0.2.2:8000. On a phone, use your computer’s LAN address and the same Wi-Fi. Use a trusted demo network for HTTP. Each server keeps a separate anonymous session."
                        : "Enter your HTTPS API address. Each server keeps a separate anonymous session.")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save server", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String value = BackendConfig.normalize(url.getText().toString(), BuildConfig.DEBUG);
                if (!config.saveUrl(value)) { url.setError("Cannot save server URL."); return; }
                dialog.dismiss(); updated.run();
            } catch (IllegalArgumentException error) { url.setError(error.getMessage()); }
        }));
        dialog.show();
    }
}
