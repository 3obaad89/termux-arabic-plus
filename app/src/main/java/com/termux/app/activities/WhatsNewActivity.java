package com.termux.app.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.widget.Button;
import android.widget.TextView;

import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

/**
 * "What's New" screen — shown once per install/update, and optionally
 * re-openable from the About / Settings screen.
 *
 * A SharedPreferences flag tracks the last version code for which the screen
 * was shown. On launch (via {@link #maybeShowWhatsNew(Context)}), the current
 * version code is compared; if it differs, the activity is started. Once
 * dismissed, the flag is updated so it won't show again until the next update.
 *
 * This is a UI-only feature. It does NOT modify the terminal renderer, BiDi
 * engine, HarfBuzz pipeline, or any terminal logic.
 */
public final class WhatsNewActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "whats_new_prefs";
    private static final String KEY_LAST_SHOWN_VERSION_CODE = "last_shown_version_code";
    private static final String EXTRA_FORCE_SHOW = "force_show";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_whats_new);

        Toolbar toolbar = findViewById(R.id.whats_new_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.whats_new_title);
            }
        }

        // Populate dynamic content from string resources
        TextView headerView = findViewById(R.id.whats_new_header);
        if (headerView != null) headerView.setText(R.string.whats_new_header);

        TextView introView = findViewById(R.id.whats_new_intro);
        if (introView != null) introView.setText(R.string.whats_new_intro);

        TextView feature1 = findViewById(R.id.whats_new_feature_1);
        if (feature1 != null) feature1.setText(R.string.whats_new_feature_1);
        TextView feature2 = findViewById(R.id.whats_new_feature_2);
        if (feature2 != null) feature2.setText(R.string.whats_new_feature_2);
        TextView feature3 = findViewById(R.id.whats_new_feature_3);
        if (feature3 != null) feature3.setText(R.string.whats_new_feature_3);
        TextView feature4 = findViewById(R.id.whats_new_feature_4);
        if (feature4 != null) feature4.setText(R.string.whats_new_feature_4);
        TextView feature5 = findViewById(R.id.whats_new_feature_5);
        if (feature5 != null) feature5.setText(R.string.whats_new_feature_5);
        TextView feature6 = findViewById(R.id.whats_new_feature_6);
        if (feature6 != null) feature6.setText(R.string.whats_new_feature_6);

        TextView devName = findViewById(R.id.whats_new_developer_name);
        if (devName != null) devName.setText(R.string.whats_new_developer_name);
        TextView devTelegram = findViewById(R.id.whats_new_telegram_handle);
        if (devTelegram != null) devTelegram.setText(R.string.whats_new_telegram_handle);

        Button dismissButton = findViewById(R.id.whats_new_dismiss_button);
        if (dismissButton != null) {
            dismissButton.setText(R.string.whats_new_dismiss);
            dismissButton.setOnClickListener(v -> {
                markAsShown();
                finish();
            });
        }

        // Mark as shown immediately so a config change won't re-trigger it
        boolean forceShow = getIntent().getBooleanExtra(EXTRA_FORCE_SHOW, false);
        if (!forceShow) {
            markAsShown();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        markAsShown();
        super.onBackPressed();
    }

    /**
     * Mark the current version as "shown" in SharedPreferences so the screen
     * won't auto-launch again until the next update.
     */
    private void markAsShown() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_LAST_SHOWN_VERSION_CODE, getVersionCode()).apply();
    }

    /**
     * Get the app's version code (package version code on all API levels).
     */
    private int getVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /**
     * Check if the "What's New" screen should be shown for the current version.
     * Compares the stored last-shown version code with the current one.
     *
     * @param context The application/activity context.
     * @return true if the screen has not been shown for the current version yet.
     */
    public static boolean shouldShowWhatsNew(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lastShown = prefs.getInt(KEY_LAST_SHOWN_VERSION_CODE, -1);
        int current = getCurrentVersionCode(context);
        return current > 0 && lastShown != current;
    }

    /**
     * Maybe launch the "What's New" activity if it hasn't been shown for the
     * current version yet. Safe to call from TermuxActivity.onCreate — it
     * returns immediately if the screen was already shown, so it never blocks
     * app startup.
     *
     * @param context The activity context.
     */
    public static void maybeShowWhatsNew(Context context) {
        if (shouldShowWhatsNew(context)) {
            startWhatsNewActivity(context, false);
        }
    }

    /**
     * Launch the "What's New" activity.
     *
     * @param context   The context to start the activity from.
     * @param forceShow If true, the screen is shown regardless of the
     *                  SharedPreferences flag (e.g. when opened from Settings).
     */
    public static void startWhatsNewActivity(Context context, boolean forceShow) {
        Intent intent = new Intent(context, WhatsNewActivity.class);
        intent.putExtra(EXTRA_FORCE_SHOW, forceShow);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
