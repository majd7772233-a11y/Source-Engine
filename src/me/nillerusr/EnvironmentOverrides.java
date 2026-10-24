package me.nillerusr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates user-provided native environment overrides before changing process state. */
public final class EnvironmentOverrides {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_INVALID = 1;
    public static final int ERROR_APPLY_FAILED = 2;
    private static final int MAX_INPUT_LENGTH = 8192;
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_ENTRY_LENGTH = 2048;
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> RESERVED_NAMES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "VALVE_GAME_PATH", "APP_DATA_PATH", "APP_LIB_PATH",
                    "EXTRAS_VPK_PATH", "APP_MOD_LIB", "LANG",
                    "LD_LIBRARY_PATH", "LD_PRELOAD", "LD_AUDIT", "LD_DEBUG",
                    "LD_DEBUG_OUTPUT", "LD_CONFIG_FILE",
                    "ANDROID_ROOT", "ANDROID_DATA", "ANDROID_ASSETS", "ANDROID_STORAGE",
                    "ANDROID_BOOTLOGO", "ANDROID_PROPERTY_WORKSPACE", "EXTERNAL_STORAGE",
                    "ASEC_MOUNTPOINT", "BOOTCLASSPATH", "CLASSPATH", "PATH"
            )));

    public interface Setter { boolean set(String name, String value); }

    public static final class Entry {
        private final String name;
        private final String value;
        private Entry(String name, String value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public String getValue() { return value; }
    }

    public static final class Result {
        private final int error;
        private final List<Entry> entries;
        private final String failedName;
        private Result(int error, List<Entry> entries, String failedName) {
            this.error = error;
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.failedName = failedName;
        }
        public int getError() { return error; }
        public boolean isSuccess() { return error == ERROR_NONE; }
        public int size() { return entries.size(); }
        public Entry getEntry(int index) { return entries.get(index); }
        public String getFailedName() { return failedName; }
    }

    private EnvironmentOverrides() { }

    /** Parses and validates every entry without mutating the environment. */
    public static Result parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return success(Collections.<Entry>emptyList());
        if (raw.length() > MAX_INPUT_LENGTH || raw.indexOf('\0') >= 0) {
            return failure(ERROR_INVALID, null);
        }
        String[] parts = raw.split("[\\r\\n;]+");
        if (parts.length > MAX_ENTRIES) return failure(ERROR_INVALID, null);
        List<Entry> entries = new ArrayList<Entry>();
        Set<String> seenNames = new HashSet<String>();
        for (String part : parts) {
            if (part.length() > MAX_ENTRY_LENGTH) return failure(ERROR_INVALID, null);
            String item = part.trim();
            if (item.isEmpty()) continue;
            int separator = item.indexOf('=');
            if (separator <= 0) return failure(ERROR_INVALID, null);
            String name = item.substring(0, separator).trim();
            String value = item.substring(separator + 1).trim();
            if (!NAME_PATTERN.matcher(name).matches()) return failure(ERROR_INVALID, null);
            if (isReserved(name) || !seenNames.add(name)) return failure(ERROR_INVALID, null);
            entries.add(new Entry(name, value));
        }
        if (entries.size() > MAX_ENTRIES) return failure(ERROR_INVALID, null);
        return success(entries);
    }

    /** A parse failure invokes no setters. A native failure aborts subsequent assignments. */
    public static Result apply(String raw, Setter setter) {
        Result parsed = parse(raw);
        if (!parsed.isSuccess() || parsed.size() == 0) return parsed;
        if (setter == null) return failure(ERROR_INVALID, null);
        for (int i = 0; i < parsed.size(); i++) {
            Entry entry = parsed.getEntry(i);
            if (!setter.set(entry.getName(), entry.getValue())) {
                return failure(ERROR_APPLY_FAILED, entry.getName());
            }
        }
        return parsed;
    }

    private static boolean isReserved(String name) {
        return RESERVED_NAMES.contains(name) || name.startsWith("LD_") || name.startsWith("ANDROID_");
    }
    private static Result success(List<Entry> entries) {
        return new Result(ERROR_NONE, entries, null);
    }
    private static Result failure(int error, String failedName) {
        return new Result(error, Collections.<Entry>emptyList(), failedName);
    }
}
