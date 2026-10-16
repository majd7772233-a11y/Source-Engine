package com.valvesoftware;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;
import java.util.Locale;
import java.util.regex.Pattern;

import me.nillerusr.ExtractAssets;
import me.nillerusr.LauncherActivity;

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
        if (extra != null && !extra.trim().isEmpty()) {
            return extra.trim();
        }
        return clean(context.getSharedPreferences("mod", 0).getString(
                "gamepath", LauncherActivity.getDefaultDir() + "/srceng"));
    }

    private static String getGameDirectory(Intent intent, SharedPreferences preferences) {
        String name = intent == null ? null : intent.getStringExtra("gamedir");
        if (name == null || name.trim().isEmpty()) {
            name = preferences.getString("gamedir", "hl2");
        }
        name = clean(name);
        if (name.isEmpty()) {
            name = "hl2";
        }

        // A game directory must be a single child name, never a path traversal expression.
        if (".".equals(name) || "..".equals(name) || name.contains("/")
                || name.contains("\\") || name.indexOf('\0') >= 0) {
            return "";
        }
        return name;
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
            Log.e(TAG, "Startup validation failed: " + error.replace('\n', ' '));
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

    private static boolean applyUserEnvironment(String rawEnvironment) {
        if (rawEnvironment == null || rawEnvironment.trim().isEmpty()) {
            return true;
        }

        for (String item : rawEnvironment.split("[\\r\\n;]+")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                Log.w(TAG, "Ignoring malformed environment override; expected NAME=VALUE");
                continue;
            }

            String name = entry.substring(0, separator).trim();
            String value = entry.substring(separator + 1).trim();
            if (!ENV_NAME.matcher(name).matches()) {
                Log.w(TAG, "Ignoring invalid environment name: " + name);
                continue;
            }

            // Keep engine-critical paths controlled by startup initialization.
            if ("VALVE_GAME_PATH".equals(name) || "APP_DATA_PATH".equals(name)
                    || "APP_LIB_PATH".equals(name) || "EXTRAS_VPK_PATH".equals(name)
                    || "LANG".equals(name)) {
                Log.w(TAG, "Ignoring reserved environment override " + name);
                continue;
            }
            if (!setRequiredEnvironment(name, value)) {
                return false;
            }
        }
        return true;
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
            for (String name : customVpk.split(",")) {
                File customFile = new File(name.trim());
                if (!customFile.isFile() || !customFile.canRead()) {
                    return "Configured VPK is missing or unreadable: " + name.trim();
                }
            }
        }

        String environment = intent == null ? mPref.getString("env", "") : intent.getStringExtra("env");
        if (!applyUserEnvironment(environment)) {
            return "An environment override could not be applied. Check overrides and Android logs (tag SRCAPK).";
        }

        String vpkPaths = bundledVpk.getAbsolutePath();
        if (customVpk != null && !customVpk.trim().isEmpty()) {
            vpkPaths = customVpk.trim() + "," + vpkPaths;
        }

        String gameLibraryDirectory = intent == null ? null : intent.getStringExtra("gamelibdir");
        if (gameLibraryDirectory != null && !gameLibraryDirectory.trim().isEmpty()
                && !setRequiredEnvironment("APP_MOD_LIB", gameLibraryDirectory.trim())) {
            return "The game library path could not be configured. Check the selected game files and retry.";
        }

        String gameRootForEngine = mPref.getBoolean("rodir", false)
                ? LauncherActivity.getAndroidDataDir() : gamePath;
        if (!setRequiredEnvironment("EXTRAS_VPK_PATH", vpkPaths)
                || !setRequiredEnvironment("LANG", Locale.getDefault().toString())
                || !setRequiredEnvironment("APP_DATA_PATH", appInfo.dataDir)
                || !setRequiredEnvironment("APP_LIB_PATH", appInfo.nativeLibraryDir)
                || !setRequiredEnvironment("VALVE_GAME_PATH", gameRootForEngine)) {
            return "The native runtime rejected a required startup setting. Check Android logs (tag SRCAPK).";
        }

        String gameArgument = gameDirectory;
        if (gameDirectory.matches(".*\\s+.*")) {
            gameArgument = "\"" + gameDirectory.replace("\\", "\\\\")
                    .replace("\"", "\\\"") + "\"";
        }
        String finalArguments = "-game " + gameArgument
                + (arguments.isEmpty() ? "" : " " + arguments);
        Log.i(TAG, "Preparing native launch for game '" + gameDirectory
                + "' (argument length " + finalArguments.length() + ")");
        setArgs(finalArguments);
        return null;
    }
}
