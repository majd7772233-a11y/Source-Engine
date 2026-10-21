package me.nillerusr;

import java.io.File;
import java.io.IOException;

/** Pure-Java launch input rules shared by the launcher and native startup bridge. */
public final class LauncherLogic {
    public static final int MAX_PATH_LENGTH = 4096;
    public static final int MAX_ARGUMENT_LENGTH = 4096;
    public static final int MAX_GAME_DIRECTORY_LENGTH = 255;

    private LauncherLogic() { }

    /** Canonicalizes a path or returns an empty string for invalid input. */
    public static String normalizePath(String rawPath) {
        if (rawPath == null) return "";
        String path = rawPath.trim();
        if (path.length() == 0 || path.length() > MAX_PATH_LENGTH || path.indexOf('\0') >= 0) {
            return "";
        }
        File file = new File(path);
        String normalized;
        try {
            normalized = file.getCanonicalPath();
        } catch (IOException e) {
            normalized = file.getAbsolutePath();
        } catch (SecurityException e) {
            return "";
        }
        if (normalized == null || normalized.length() == 0
                || normalized.length() > MAX_PATH_LENGTH || normalized.indexOf('\0') >= 0) {
            return "";
        }
        return normalized;
    }

    /** Validates a single game directory name; empty input uses the supplied fallback. */
    public static String normalizeGameDirectory(String rawName, String fallback) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() == 0) name = fallback == null ? "" : fallback.trim();
        if (name.length() == 0 || name.length() > MAX_GAME_DIRECTORY_LENGTH
                || ".".equals(name) || "..".equals(name)
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return "";
        }
        return name;
    }

    /** Constructs the exact command string passed to the native Source runtime. */
    public static String buildGameArguments(String gameDirectory, String rawArguments) {
        String directory = normalizeGameDirectory(gameDirectory, "");
        if (directory.length() == 0) throw new IllegalArgumentException("Invalid game directory.");
        String arguments = rawArguments == null ? "" : rawArguments.trim();
        if (arguments.length() > MAX_ARGUMENT_LENGTH || arguments.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid launch arguments.");
        }
        String quotedDirectory = directory;
        if (directory.matches(".*\\s+.*")) {
            quotedDirectory = "\"" + directory.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return "-game " + quotedDirectory + (arguments.length() == 0 ? "" : " " + arguments);
    }
}
