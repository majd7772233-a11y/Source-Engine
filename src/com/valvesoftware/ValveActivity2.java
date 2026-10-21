package com.valvesoftware;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.magd.source.engine.R;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;

import me.nillerusr.ExtractAssets;
import me.nillerusr.LauncherActivity;
import me.nillerusr.LauncherLogic;

/**
 * Java bridge between the Android launcher and the native Source runtime.
 * Keep native declarations stable: native code may resolve these methods by JNI name.
 */
public final class ValveActivity2 {
    private static final String TAG = "SRCAPK";
    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static Activity mSingleton;
    public static SharedPreferences mPref;

    public static native void setArgs(String args);
    public static native int setenv(String name, String value, int overwrite);
    private static native void nativeOnActivityResult(Activity activity, int requestCode, int resultCode, Intent data);

    private ValveActivity2() {
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getGamePath(Context context, Intent intent) {
        String extra = intent == null ? null : intent.getStringExtra("gamepath");
        String selected = extra != null && !extra.trim().isEmpty()
                ? extra : context.getSharedPreferences("mod", 0).getString(
                        "gamepath", LauncherActivity.getDefaultDir() + "/srceng");
        return LauncherLogic.normalizePath(selected);
    }

    private static String getGameDirectory(Intent intent, SharedPreferences preferences) {
        String name = intent == null ? null : intent.getStringExtra("gamedir");
        if (name == null || name.trim().isEmpty()) {
            name = preferences.getString("gamedir", "hl2");
        }
        return LauncherLogic.normalizeGameDirectory(name, "hl2");
    }

    private static boolean hasGameInfo(File directory) {
        if (directory == null || !directory.isDirectory() || !directory.canRead()) {
            return false;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child != null && child.isFile()
                    && "gameinfo.txt".equalsIgnoreCase(child.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Compatibility helper retained for existing Java/native integrations. */
    public static boolean findGameinfo(String path) {
        File root = new File(clean(path));
        if (!root.isDirectory() || !root.canRead()) {
            return false;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child != null && child.isDirectory() && hasGameInfo(child)) {
                return true;
            }
        }
        return false;
    }

    /** Compatibility helper retained for existing Java/native integrations. */
    public static boolean isModGameinfoExists(String path) {
        return hasGameInfo(new File(clean(path)));
    }

    public static String getStartupError(Context context, Intent intent) {
        if (context == null) {
            return "Android context is unavailable. Close and reopen the launcher.";
        }

        SharedPreferences preferences = context.getSharedPreferences("mod", 0);
        String path = getGamePath(context, intent);
        if (path.isEmpty()) {
            return "The game folder path is empty. Select the folder containing your game files.";
        }
        if (path.length() > 4096 || path.indexOf('\0') >= 0) {
            return "The selected game folder path is invalid or too long. Select the folder again.";
        }

        File root = new File(path);
        if (!root.isDirectory()) {
            return "The selected game folder does not exist or is not a directory:\n" + path;
        }
        if (!root.canRead()) {
            return "Android cannot read the selected game folder:\n" + path
                    + "\nCheck storage permission and select it again.";
        }

        String gameDirectory = getGameDirectory(intent, preferences);
        if (gameDirectory.isEmpty()) {
            return "The game folder name is invalid. Use one folder name, such as hl2.";
        }

        File selectedGame = new File(root, gameDirectory);
        if (!hasGameInfo(selectedGame)) {
            return "Required game metadata was not found or cannot be read:\n"
                    + new File(selectedGame, "gameinfo.txt").getPath()
                    + "\n\nSelect the correct game folder. The APK does not include proprietary game files.";
        }
        return null;
    }

    public static boolean preInit(Context context, Intent intent) {
        mPref = context.getSharedPreferences("mod", 0);
        String error = getStartupError(context, intent);
        if (error != null) {
            Log.e(TAG, "Startup validation failed; details are shown in the launcher.");
        }
        return error == null;
    }

    private static boolean setRequiredEnvironment(String name, String value) {
        int result = setenv(name, value, 1);
        if (result != 0) {
            Log.e(TAG, "setenv failed for " + name + " (result " + result + ")");
        }
        return result == 0;
    }

    /**
     * Validates the entire override list before mutating the process environment. Invalid
     * or reserved entries fail startup visibly instead of being silently ignored after
     * earlier entries have already been applied.
     */
    private static String applyUserEnvironment(Context context, String rawEnvironment) {
        if (rawEnvironment == null || rawEnvironment.trim().isEmpty()) return null;
        if (rawEnvironment.length() > 8192 || rawEnvironment.indexOf('\0') >= 0) {
            return context.getString(R.string.srceng_environment_invalid);
        }

        String[] entries = rawEnvironment.split("[\\r\\n;]+");
        if (entries.length > 64) return context.getString(R.string.srceng_environment_invalid);

        String[] names = new String[entries.length];
        String[] values = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            String item = entries[i];
            if (item.length() > 2048) return context.getString(R.string.srceng_environment_invalid);
            String entry = item.trim();
            if (entry.isEmpty()) continue;

            int separator = entry.indexOf('=');
            if (separator <= 0) return context.getString(R.string.srceng_environment_invalid);
            String name = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!ENV_NAME.matcher(name).matches()) {
                return context.getString(R.string.srceng_environment_invalid);
            }

            // Engine-critical paths are controlled by startup initialization.
            if ("VALVE_GAME_PATH".equals(name) || "APP_DATA_PATH".equals(name)
                    || "APP_LIB_PATH".equals(name) || "EXTRAS_VPK_PATH".equals(name)
                    || "LANG".equals(name)) {
                return context.getString(R.string.srceng_environment_invalid);
            }
            names[i] = name;
            values[i] = value;
        }

        for (int i = 0; i < names.length; i++) {
            if (names[i] != null && !setRequiredEnvironment(names[i], values[i])) {
                return context.getString(R.string.srceng_environment_apply_failed, names[i]);
            }
        }
        return null;
    }

    public static String initNatives(Context context, Intent intent) {
        mPref = context.getSharedPreferences("mod", 0);
        ApplicationInfo appInfo = context.getApplicationInfo();
        String gamePath = getGamePath(context, intent);
        String gameDirectory = getGameDirectory(intent, mPref);
        String arguments = intent == null ? null : intent.getStringExtra("argv");
        if (arguments == null) {
            arguments = mPref.getString("argv", "-console");
        }
        arguments = clean(arguments);
        if (arguments.length() > 4096 || arguments.indexOf('\0') >= 0) {
            return "Launch arguments are invalid or too long (maximum 4096 characters). Shorten the arguments and retry.";
        }

        if (!ExtractAssets.extractVPK(context, false)) {
            return "Runtime data extraction failed. Check free internal storage and reopen the app. "
                    + "See Android logs (tag ExtractAssets).";
        }

        File bundledVpk = new File(context.getFilesDir(), ExtractAssets.VPK_NAME);
        if (!bundledVpk.isFile() || bundledVpk.length() <= 0) {
            return "Runtime data file is missing or empty: " + bundledVpk.getAbsolutePath();
        }

        String customVpk = intent == null ? null : intent.getStringExtra("vpk");
        if (customVpk != null && !customVpk.trim().isEmpty()) {
            String[] customPaths = customVpk.split(",", -1);
            if (customPaths.length > 16 || customVpk.length() > 8192 || customVpk.indexOf('\0') >= 0) {
                return "Too many or invalid custom VPK paths are configured. Keep at most 16 readable files.";
            }
            for (String name : customPaths) {
                if (name.trim().isEmpty() || name.trim().length() > 4096) {
                    return "A custom VPK path is empty or too long. Correct the path and retry.";
                }
                File customFile = new File(name.trim());
                if (!customFile.isFile() || !customFile.canRead()) {
                    return "Configured VPK is missing or unreadable: " + name.trim();
                }
            }
        }

        String environment = intent == null ? mPref.getString("env", "") : intent.getStringExtra("env");
        String environmentError = applyUserEnvironment(context, environment);
        if (environmentError != null) return environmentError;

        String vpkPaths = bundledVpk.getAbsolutePath();
        if (customVpk != null && !customVpk.trim().isEmpty()) {
            vpkPaths = customVpk.trim() + "," + vpkPaths;
        }

        String gameLibraryDirectory = intent == null ? null : intent.getStringExtra("gamelibdir");
        if (gameLibraryDirectory != null && !gameLibraryDirectory.trim().isEmpty()
                && !setRequiredEnvironment("APP_MOD_LIB", gameLibraryDirectory.trim())) {
            return "The game library path could not be configured. Check the selected game files and retry.";
        }

        String gameRootForEngine = gamePath;
        if (mPref.getBoolean("rodir", false)) {
            gameRootForEngine = LauncherActivity.getAndroidDataDir();
            if (gameRootForEngine == null || gameRootForEngine.isEmpty()) {
                return "Could not prepare the optional game data directory. Check storage availability and permissions.";
            }
        }
        if (!setRequiredEnvironment("EXTRAS_VPK_PATH", vpkPaths)
                || !setRequiredEnvironment("LANG", Locale.getDefault().toString())
                || !setRequiredEnvironment("APP_DATA_PATH", appInfo.dataDir)
                || !setRequiredEnvironment("APP_LIB_PATH", appInfo.nativeLibraryDir)
                || !setRequiredEnvironment("VALVE_GAME_PATH", gameRootForEngine)) {
            return "The native runtime rejected a required startup setting. Check Android logs (tag SRCAPK).";
        }

        final String finalArguments;
        try {
            finalArguments = LauncherLogic.buildGameArguments(gameDirectory, arguments);
        } catch (IllegalArgumentException invalidArguments) {
            return "Launch arguments or game directory are invalid. Correct the advanced settings and retry.";
        }

        Log.i(TAG, "Preparing native launch for game '" + gameDirectory
                + "' (argument length " + finalArguments.length() + ")");
        setArgs(finalArguments);
        return null;
    }
}
