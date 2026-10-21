package me.nillerusr;

import android.content.SharedPreferences;

/** Additive one-time migration for launcher settings written by older builds. */
public final class LauncherPreferences {
    public static final String SCHEMA_VERSION_KEY = "launcher_settings_schema_version";
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private LauncherPreferences() { }

    public static boolean migrate(SharedPreferences preferences, String defaultGamePath) {
        if (preferences == null) return false;
        if (preferences.getInt(SCHEMA_VERSION_KEY, 0) >= CURRENT_SCHEMA_VERSION) return false;

        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains("argv")) editor.putString("argv", "-console");
        if (!preferences.contains("gamepath")) {
            editor.putString("gamepath", LauncherLogic.normalizePath(defaultGamePath));
        }
        if (!preferences.contains("env")) editor.putString("env", "LIBGL_USEVBO=0");
        if (!preferences.contains("gamedir")) editor.putString("gamedir", "hl2");
        editor.putInt(SCHEMA_VERSION_KEY, CURRENT_SCHEMA_VERSION);
        return editor.commit();
    }
}
