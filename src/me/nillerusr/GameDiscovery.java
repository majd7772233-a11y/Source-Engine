package me.nillerusr;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded, permission-aware Source game folder discovery.
 *
 * This class only detects filesystem structure. It cannot prove that a particular
 * game/mod is compatible with the native engine; that must be verified at runtime.
 */
public final class GameDiscovery {
    public static final int MAX_DEPTH = 4;
    public static final int MAX_DIRECTORIES = 1200;
    private static final long MAX_GAMEINFO_BYTES = 256L * 1024L;
    private static final String PREFS = "mod";
    private static final String LIBRARY_PATHS = "library_paths";
    private static final String LAST_LAUNCHED_PREFIX = "last_launched:";

    private GameDiscovery() { }

    public static final class GameEntry {
        public final String path;
        public final String launchRoot;
        public final String directoryName;
        public final String displayName;
        public final String status;
        public final String detail;
        public final boolean launchable;
        public final boolean optionalContentAvailable;
        public final long lastLaunched;

        GameEntry(String path, String launchRoot, String directoryName, String displayName,
                  String status, String detail, boolean launchable, boolean optionalContentAvailable, long lastLaunched) {
            this.path = path;
            this.launchRoot = launchRoot;
            this.directoryName = directoryName;
            this.displayName = displayName;
            this.status = status;
            this.detail = detail;
            this.launchable = launchable;
            this.optionalContentAvailable = optionalContentAvailable;
            this.lastLaunched = lastLaunched;
        }
    }

    public static List<GameEntry> scan(Context context, String rootPath) {
        List<GameEntry> results = new ArrayList<GameEntry>();
        if (context == null || rootPath == null || rootPath.trim().length() == 0) {
            return results;
        }
        File root = new File(rootPath.trim());
        try {
            if (!root.isDirectory() || !root.canRead()) return results;
        } catch (SecurityException e) {
            return results;
        }

        Set<String> visited = new HashSet<String>();
        int[] count = new int[] { 0 };
        try {
            scanDirectory(context, root, 0, visited, count, results);
        } catch (SecurityException e) {
            // Preserve entries discovered before Android denied a later path.
        }

        Collections.sort(results, new Comparator<GameEntry>() {
            @Override public int compare(GameEntry left, GameEntry right) {
                int state = right.launchable == left.launchable ? 0 : (right.launchable ? 1 : -1);
                if (state != 0) return state;
                return left.displayName.compareToIgnoreCase(right.displayName);
            }
        });
        return results;
    }

    private static void scanDirectory(Context context, File directory, int depth,
                                      Set<String> visited, int[] count,
                                      List<GameEntry> results) {
        if (directory == null || depth > MAX_DEPTH || count[0] >= MAX_DIRECTORIES
                || !directory.isDirectory() || !directory.canRead()) return;
        final String canonical;
        try {
            canonical = directory.getCanonicalPath();
        } catch (IOException e) {
            return;
        }
        if (!visited.add(canonical)) return;
        count[0]++;

        File[] children = directory.listFiles();
        if (children == null) return;

        boolean hasInfo = findChildIgnoreCase(children, "gameinfo.txt") != null;
        boolean hasContent = hasContentMarkers(directory, children);
        if (hasInfo || hasContent) {
            GameEntry entry = inspect(context, directory, children, hasInfo, hasContent);
            if (entry != null) results.add(entry);
            // A game/mod directory is a leaf for discovery; its asset folders should not
            // be recursively listed as separate games.
            if (hasInfo) return;
        }

        for (File child : children) {
            if (child == null || !child.isDirectory() || !child.canRead()) continue;
            String name = child.getName();
            if (name.startsWith(".") || "Android".equalsIgnoreCase(name)
                    || "cache".equalsIgnoreCase(name) || "obb".equalsIgnoreCase(name)
                    || "downloads".equalsIgnoreCase(name)) continue;
            if (count[0] >= MAX_DIRECTORIES) break;
            scanDirectory(context, child, depth + 1, visited, count, results);
        }
    }

    private static GameEntry inspect(Context context, File gameDir, File[] children,
                                     boolean hasInfo, boolean hasContent) {
        String path;
        String launchRoot;
        try {
            path = gameDir.getCanonicalPath();
            File parent = gameDir.getCanonicalFile().getParentFile();
            launchRoot = parent == null ? path : parent.getCanonicalPath();
        } catch (IOException e) {
            return null;
        }

        File info = findChildIgnoreCase(children, "gameinfo.txt");
        String title = null;
        boolean validInfo = false;
        if (info != null && info.isFile() && info.canRead()
                && info.length() > 0 && info.length() <= MAX_GAMEINFO_BYTES) {
            title = readTitle(info);
            validInfo = validateGameInfo(info);
        }

        String displayName = title;
        if (displayName == null || displayName.trim().length() == 0) {
            displayName = gameDir.getName();
        }
        boolean optionalContent = hasOptionalContent(children);
        boolean abiSupported = supportsPackagedAbi();
        boolean nativeRuntimeAvailable = hasNativeRuntime(context);
        boolean launchable = hasInfo && validInfo && hasContent && abiSupported && nativeRuntimeAvailable;
        String status;
        String detail;
        if (!hasInfo) {
            status = context.getString(R.string.srceng_library_status_missing_info);
            detail = context.getString(R.string.srceng_library_detail_missing_info);
        } else if (!validInfo) {
            status = context.getString(R.string.srceng_library_status_invalid_info);
            detail = context.getString(R.string.srceng_library_detail_invalid_info);
        } else if (!hasContent) {
            status = context.getString(R.string.srceng_library_status_missing_assets);
            detail = context.getString(R.string.srceng_library_detail_missing_assets);
        } else if (!abiSupported) {
            status = context.getString(R.string.srceng_library_status_unsupported_abi);
            detail = context.getString(R.string.srceng_library_detail_unsupported_abi);
        } else if (!nativeRuntimeAvailable) {
            status = context.getString(R.string.srceng_library_status_native_missing);
            detail = context.getString(R.string.srceng_library_detail_native_missing);
        } else {
            status = context.getString(R.string.srceng_library_status_ready);
            detail = context.getString(R.string.srceng_library_detail_ready);
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, 0);
        long last = preferences.getLong(LAST_LAUNCHED_PREFIX + path, 0L);
        return new GameEntry(path, launchRoot, gameDir.getName(), displayName,
                status, detail, launchable, optionalContent, last);
    }

    private static boolean validateGameInfo(File file) {
        if (file == null || !file.isFile() || !file.canRead()
                || file.length() <= 0 || file.length() > MAX_GAMEINFO_BYTES) return false;
        StringBuilder content = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
                if (content.length() > MAX_GAMEINFO_BYTES) return false;
            }
        } catch (IOException e) {
            return false;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
        return hasBalancedKeyValues(content.toString());
    }

    private static boolean hasBalancedKeyValues(String text) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        boolean comment = false;
        boolean sawBrace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (comment) {
                if (c == '\n' || c == '\r') comment = false;
                continue;
            }
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                comment = true;
                i++;
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{') {
                depth++;
                sawBrace = true;
            } else if (c == '}') {
                depth--;
                if (depth < 0) return false;
                sawBrace = true;
            }
        }
        return sawBrace && !quoted && depth == 0;
    }

    private static boolean hasOptionalContent(File[] children) {
        String[] optionalDirectories = new String[] { "custom", "addons", "workshop" };
        for (String name : optionalDirectories) {
            File candidate = findChildIgnoreCase(children, name);
            if (candidate != null && candidate.isDirectory() && candidate.canRead()) {
                File[] contents = candidate.listFiles();
                if (contents != null && contents.length > 0) return true;
            }
        }
        return false;
    }

    private static String readTitle(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            int total = 0;
            String quote = String.valueOf('"');
            while ((line = reader.readLine()) != null && total < MAX_GAMEINFO_BYTES) {
                total += line.length();
                String lower = line.toLowerCase(Locale.US);
                int keyStart = lower.indexOf(quote + "game" + quote);
                if (keyStart < 0) keyStart = lower.indexOf(quote + "title" + quote);
                if (keyStart < 0) continue;
                int keyEnd = line.indexOf('"', keyStart + 1);
                if (keyEnd < 0) continue;
                int valueStart = line.indexOf('"', keyEnd + 1);
                if (valueStart < 0) continue;
                int valueEnd = line.indexOf('"', valueStart + 1);
                if (valueEnd <= valueStart + 1) continue;
                String value = line.substring(valueStart + 1, valueEnd).trim();
                if (value.length() > 0 && value.length() <= 160) return value;
            }
        } catch (IOException ignored) {
        } catch (SecurityException ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
        return null;
    }

    private static boolean hasContentMarkers(File directory, File[] children) {
        for (File child : children) {
            if (child == null || !child.isFile()) continue;
            String name = child.getName().toLowerCase(Locale.US);
            if (name.endsWith(".vpk") && child.length() > 0) return true;
        }
        File maps = findChildIgnoreCase(children, "maps");
        if (maps != null && maps.isDirectory() && maps.canRead()) {
            File[] files = maps.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file != null && file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".bsp")
                            && file.length() > 0) return true;
                }
            }
        }
        return false;
    }

    private static File findChildIgnoreCase(File[] children, String name) {
        if (children == null) return null;
        for (File child : children) {
            if (child != null && child.getName().equalsIgnoreCase(name)) return child;
        }
        return null;
    }

    private static boolean hasNativeRuntime(Context context) {
        if (context == null || context.getApplicationInfo() == null) return false;
        String directory = context.getApplicationInfo().nativeLibraryDir;
        if (directory == null) return false;
        String[] required = new String[] {
                "libtierhook.so", "libmain.so", "libRegal.so", "libclient.so", "libserver.so"
        };
        File nativeDirectory = new File(directory);
        for (String library : required) {
            File file = new File(nativeDirectory, library);
            if (!file.isFile() || !file.canRead() || file.length() <= 0) return false;
        }
        return true;
    }

    private static boolean supportsPackagedAbi() {
        if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("armeabi-v7a".equals(abi)) return true;
            }
            return false;
        }
        String abi = Build.CPU_ABI;
        return "armeabi-v7a".equals(abi) || "armeabi".equals(abi);
    }

    public static GameEntry inspectPath(Context context, String path) {
        if (context == null || path == null || path.trim().length() == 0) return null;
        try {
            File directory = new File(path.trim());
            if (!directory.isDirectory() || !directory.canRead()) return null;
            File[] children = directory.listFiles();
            if (children == null) return null;
            boolean hasInfo = findChildIgnoreCase(children, "gameinfo.txt") != null;
            boolean hasContent = hasContentMarkers(directory, children);
            if (!hasInfo && !hasContent) return null;
            return inspect(context, directory, children, hasInfo, hasContent);
        } catch (SecurityException e) {
            return null;
        }
    }

    public static Set<String> getSavedPaths(Context context) {
        Set<String> values = context.getSharedPreferences(PREFS, 0).getStringSet(
                LIBRARY_PATHS, new HashSet<String>());
        return values == null ? new HashSet<String>() : new HashSet<String>(values);
    }

    public static void savePaths(Context context, Set<String> paths) {
        context.getSharedPreferences(PREFS, 0).edit()
                .putStringSet(LIBRARY_PATHS, new HashSet<String>(paths)).apply();
    }

    public static void recordLaunch(Context context, String gamePath) {
        if (context == null || gamePath == null) return;
        context.getSharedPreferences(PREFS, 0).edit()
                .putLong(LAST_LAUNCHED_PREFIX + gamePath, System.currentTimeMillis()).apply();
    }
}
