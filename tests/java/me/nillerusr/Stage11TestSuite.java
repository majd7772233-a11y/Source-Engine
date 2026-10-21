package me.nillerusr;

import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import testsupport.InMemorySharedPreferences;
import testsupport.TestContext;

public final class Stage11TestSuite {
    private static int assertions;
    private static File tempRoot;

    public static void main(String[] args) throws Exception {
        tempRoot = File.createTempFile("source-engine-tests", "root");
        if (!tempRoot.delete() || !tempRoot.mkdirs()) throw new IllegalStateException("Could not create test root.");
        try {
            testPathNormalization();
            testArgumentConstruction();
            testPreferenceMigration();
            testGameDiscoveryAndReadiness();
            testLibraryPreferenceSnapshots();
            testDepthBoundAndEmptyInputs();
            System.out.println("Stage 11 JVM tests passed: " + assertions + " assertions.");
        } finally {
            deleteRecursively(tempRoot);
        }
    }

    private static void testPathNormalization() throws Exception {
        equal("", LauncherLogic.normalizePath(null), "null path is rejected");
        equal("", LauncherLogic.normalizePath("   "), "blank path is rejected");
        equal("", LauncherLogic.normalizePath("bad\0path"), "NUL path is rejected");
        equal("", LauncherLogic.normalizePath(repeat("x", LauncherLogic.MAX_PATH_LENGTH + 1)),
                "overlong path is rejected");

        File parent = new File(tempRoot, "path with spaces");
        File target = new File(parent, "child");
        check(parent.mkdirs(), "create path test directory");
        check(target.mkdirs(), "create nested path test directory");
        equal(parent.getCanonicalPath(), LauncherLogic.normalizePath(new File(target, "..").getPath()),
                "dot-dot path is canonicalized");
        equal(new File(parent, "new folder").getCanonicalPath(),
                LauncherLogic.normalizePath(new File(parent, "new folder").getPath()),
                "nonexistent destination with spaces is normalized without being created");
    }

    private static void testArgumentConstruction() {
        equal("-game hl2 -console", LauncherLogic.buildGameArguments("hl2", "-console"),
                "default game argument and flags");
        equal("-game \"Portal 2\" -novid", LauncherLogic.buildGameArguments("Portal 2", "-novid"),
                "game directory containing spaces is quoted");
        equal("-game hl2", LauncherLogic.buildGameArguments("hl2", "  "),
                "blank arguments do not add trailing whitespace");
        equal("-game \"A \\\"quoted\\\" Game\" -console",
                LauncherLogic.buildGameArguments("A \"quoted\" Game", "-console"),
                "embedded quotes and backslashes are escaped");

        equal("", LauncherLogic.normalizeGameDirectory("..", "hl2"), "parent traversal is rejected");
        equal("", LauncherLogic.normalizeGameDirectory("hl2/../other", "hl2"), "forward slash is rejected");
        equal("", LauncherLogic.normalizeGameDirectory("hl2\\..\\other", "hl2"), "backslash is rejected");
        equal("", LauncherLogic.normalizeGameDirectory(repeat("g", 256), "hl2"), "overlong game name is rejected");
        equal("hl2", LauncherLogic.normalizeGameDirectory(" ", "hl2"), "blank name uses fallback");
        expectIllegalArgument(new Runnable() { @Override public void run() {
            LauncherLogic.buildGameArguments("../escape", "-console");
        }}, "invalid game directory cannot be constructed");
        expectIllegalArgument(new Runnable() { @Override public void run() {
            LauncherLogic.buildGameArguments("hl2", repeat("a", LauncherLogic.MAX_ARGUMENT_LENGTH + 1));
        }}, "overlong arguments are rejected");
        expectIllegalArgument(new Runnable() { @Override public void run() {
            LauncherLogic.buildGameArguments("hl2", "bad\0argument");
        }}, "NUL argument is rejected");
    }

    private static void testPreferenceMigration() throws Exception {
        InMemorySharedPreferences old = new InMemorySharedPreferences();
        String existingPath = new File(tempRoot, "existing game root").getCanonicalPath();
        old.edit().putString("argv", "-console -novid")
                .putString("gamepath", existingPath)
                .putString("env", "LIBGL_USEVBO=1")
                .putString("gamedir", "portal")
                .putBoolean("liquid_motion", false).commit();

        check(LauncherPreferences.migrate(old, new File(tempRoot, "default root").getPath()),
                "unversioned settings are migrated");
        equal("-console -novid", old.getString("argv", ""), "existing launch flags are preserved");
        equal(existingPath, old.getString("gamepath", ""), "existing game path is preserved");
        equal("LIBGL_USEVBO=1", old.getString("env", ""), "existing environment is preserved");
        equal("portal", old.getString("gamedir", ""), "existing game directory is preserved");
        check(!old.getBoolean("liquid_motion", true), "unrelated visual preference is preserved");
        equal(LauncherPreferences.CURRENT_SCHEMA_VERSION,
                old.getInt(LauncherPreferences.SCHEMA_VERSION_KEY, 0), "schema version recorded");
        check(!LauncherPreferences.migrate(old, "/ignored"), "migration is idempotent after schema update");
        equal(existingPath, old.getString("gamepath", ""), "repeat migration does not overwrite values");

        InMemorySharedPreferences fresh = new InMemorySharedPreferences();
        String defaultPath = LauncherLogic.normalizePath(new File(tempRoot, "srceng").getPath());
        check(LauncherPreferences.migrate(fresh, new File(tempRoot, "srceng").getPath()),
                "fresh install defaults are initialized");
        equal("-console", fresh.getString("argv", ""), "default arguments initialized");
        equal(defaultPath, fresh.getString("gamepath", ""), "default game path is normalized");
        equal("LIBGL_USEVBO=0", fresh.getString("env", ""), "default environment initialized");
        equal("hl2", fresh.getString("gamedir", ""), "default game directory initialized");

        fresh.edit().putInt(LauncherPreferences.SCHEMA_VERSION_KEY, 9)
                .putString("argv", "-user-change").commit();
        check(!LauncherPreferences.migrate(fresh, "/ignored"), "newer schema is left untouched");
        equal("-user-change", fresh.getString("argv", ""), "future migration cannot overwrite user settings");
    }

    private static void testGameDiscoveryAndReadiness() throws Exception {
        File nativeDir = new File(tempRoot, "native-libs");
        check(nativeDir.mkdirs(), "create simulated app native library directory");
        String[] requiredLibraries = new String[] {
                "libtierhook.so", "libmain.so", "libRegal.so", "libclient.so", "libserver.so"
        };
        for (String name : requiredLibraries) write(new File(nativeDir, name), "fake nonempty native payload");
        TestContext context = new TestContext(nativeDir.getAbsolutePath());
        Build.VERSION.SDK_INT = 30;
        Build.SUPPORTED_ABIS = new String[] { "armeabi-v7a" };
        Build.CPU_ABI = "armeabi-v7a";

        File root = new File(tempRoot, "games");
        check(root.mkdirs(), "create game root");
        File valid = createGame(root, "hl2", "GAMEINFO.TXT",
                "\"GameInfo\"\n{\n  \"game\" \"Half Life Test\"\n  \"FileSystem\" { \"SteamAppId\" \"220\" }\n}\n", true);
        File invalid = createGame(root, "broken", "gameinfo.txt", "\"GameInfo\"\n{\n  \"game\" \"Broken\"\n", true);
        File noAssets = createGame(root, "missing-assets", "gameinfo.txt",
                "\"GameInfo\"\n{\n  \"game\" \"No Assets\"\n}\n", false);
        File mods = new File(valid, "addons");
        check(mods.mkdirs(), "create optional add-ons directory");
        write(new File(mods, "example.vpk"), "optional content");

        List<GameDiscovery.GameEntry> entries = GameDiscovery.scan(context, root.getAbsolutePath());
        GameDiscovery.GameEntry ready = find(entries, valid.getCanonicalPath());
        GameDiscovery.GameEntry invalidEntry = find(entries, invalid.getCanonicalPath());
        GameDiscovery.GameEntry assetsEntry = find(entries, noAssets.getCanonicalPath());
        check(ready != null, "valid game is discovered");
        check(ready.launchable, "valid metadata, content, ABI and native payload are launch-ready");
        equal("Half Life Test", ready.displayName, "display title is read from gameinfo.txt");
        equal(root.getCanonicalPath(), ready.launchRoot, "launch root is the game folder parent");
        equal("hl2", ready.directoryName, "game folder name is preserved");
        check(ready.optionalContentAvailable, "optional add-on content is detected");
        check(invalidEntry != null && !invalidEntry.launchable, "malformed gameinfo is discovered but cannot launch");
        equal("INVALID_INFO", invalidEntry.status, "malformed metadata reports invalid readiness");
        check(assetsEntry != null && !assetsEntry.launchable, "metadata without game assets cannot launch");
        equal("MISSING_ASSETS", assetsEntry.status, "missing assets report explicit readiness");

        GameDiscovery.recordLaunch(context, ready.path);
        GameDiscovery.GameEntry relaunched = GameDiscovery.inspectPath(context, ready.path);
        check(relaunched.lastLaunched > 0L, "last-launch timestamp is persisted");

        TestContext noNativeContext = new TestContext(new File(tempRoot, "empty-native").getAbsolutePath());
        check(new File(noNativeContext.getApplicationInfo().nativeLibraryDir).mkdirs(), "create empty native directory");
        GameDiscovery.GameEntry missingNative = GameDiscovery.inspectPath(noNativeContext, valid.getCanonicalPath());
        check(missingNative != null && !missingNative.launchable, "missing native payload prevents launch");
        equal("NATIVE_MISSING", missingNative.status, "missing native library has explicit readiness state");

        Build.SUPPORTED_ABIS = new String[] { "arm64-v8a" };
        GameDiscovery.GameEntry unsupported = GameDiscovery.inspectPath(context, valid.getCanonicalPath());
        check(unsupported != null && !unsupported.launchable, "unsupported ABI prevents launch");
        equal("UNSUPPORTED_ABI", unsupported.status, "unsupported ABI has explicit readiness state");
        Build.SUPPORTED_ABIS = new String[] { "armeabi-v7a" };

        File quoted = createGame(root, "quoted-title", "gameinfo.txt",
                "\"GameInfo\"\n{\n \"game\" \"A \\\"Quoted\\\" Title\"\n}\n", true);
        GameDiscovery.GameEntry quotedEntry = GameDiscovery.inspectPath(context, quoted.getCanonicalPath());
        check(quotedEntry != null && quotedEntry.launchable,
                "escaped quotes in valid gameinfo do not break brace validation");
    }

    private static void testLibraryPreferenceSnapshots() {
        TestContext context = new TestContext(tempRoot.getAbsolutePath());
        Set<String> source = new HashSet<String>(Arrays.asList("/games/hl2", "/games/portal"));
        GameDiscovery.savePaths(context, source);
        source.clear();
        Set<String> loaded = GameDiscovery.getSavedPaths(context);
        equal(2, loaded.size(), "saving library paths copies the input set");
        loaded.clear();
        equal(2, GameDiscovery.getSavedPaths(context).size(), "reading library paths returns a defensive snapshot");
    }

    private static void testDepthBoundAndEmptyInputs() throws Exception {
        TestContext context = new TestContext(new File(tempRoot, "depth-native").getAbsolutePath());
        check(new File(context.getApplicationInfo().nativeLibraryDir).mkdirs(), "create depth native directory");
        for (String name : new String[] {"libtierhook.so","libmain.so","libRegal.so","libclient.so","libserver.so"}) {
            write(new File(context.getApplicationInfo().nativeLibraryDir, name), "x");
        }
        check(GameDiscovery.scan(context, null).isEmpty(), "null root returns an empty result");
        check(GameDiscovery.scan(context, " ").isEmpty(), "blank root returns an empty result");

        File root = new File(tempRoot, "depth-test");
        check(root.mkdirs(), "create depth-test root");
        File nested = root;
        for (int i = 0; i < GameDiscovery.MAX_DEPTH + 1; i++) {
            nested = new File(nested, "level" + i);
            check(nested.mkdirs(), "create bounded depth folder " + i);
        }
        createGame(nested, "too-deep-game", "gameinfo.txt",
                "\"GameInfo\"\n{\n \"game\" \"Too Deep\"\n}\n", true);
        List<GameDiscovery.GameEntry> found = GameDiscovery.scan(context, root.getAbsolutePath());
        check(find(found, new File(nested, "too-deep-game").getCanonicalPath()) == null,
                "scanner does not descend beyond maximum depth");
    }

    private static File createGame(File root, String directoryName, String infoName,
                                   String gameInfo, boolean withVpk) throws Exception {
        File game = new File(root, directoryName);
        check(game.mkdirs(), "create game folder " + directoryName);
        write(new File(game, infoName), gameInfo);
        if (withVpk) write(new File(game, "pak01_dir.vpk"), "vpk");
        return game;
    }

    private static GameDiscovery.GameEntry find(List<GameDiscovery.GameEntry> entries, String path) {
        for (GameDiscovery.GameEntry entry : entries) if (path.equals(entry.path)) return entry;
        return null;
    }

    private static void write(File file, String text) throws Exception {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try { writer.write(text); }
        finally { writer.close(); }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    private static void equal(Object expected, Object actual, String label) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
