package testsupport;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<String, Object>();

    @Override public synchronized String getString(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }
    @Override public synchronized int getInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Integer ? ((Integer) value).intValue() : fallback;
    }
    @Override public synchronized long getLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Long ? ((Long) value).longValue() : fallback;
    }
    @Override public synchronized boolean getBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }
    @SuppressWarnings("unchecked")
    @Override public synchronized Set<String> getStringSet(String key, Set<String> fallback) {
        Object value = values.get(key);
        if (!(value instanceof Set)) return fallback;
        return new HashSet<String>((Set<String>) value);
    }
    @Override public synchronized boolean contains(String key) { return values.containsKey(key); }
    @Override public Editor edit() { return new MemoryEditor(); }

    private final class MemoryEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<String, Object>();
        private final Set<String> removed = new HashSet<String>();
        private boolean clear;

        @Override public Editor putString(String key, String value) { pending.put(key, value); removed.remove(key); return this; }
        @Override public Editor putInt(String key, int value) { pending.put(key, Integer.valueOf(value)); removed.remove(key); return this; }
        @Override public Editor putLong(String key, long value) { pending.put(key, Long.valueOf(value)); removed.remove(key); return this; }
        @Override public Editor putBoolean(String key, boolean value) { pending.put(key, Boolean.valueOf(value)); removed.remove(key); return this; }
        @Override public Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value == null ? null : new HashSet<String>(value));
            removed.remove(key); return this;
        }
        @Override public Editor remove(String key) { pending.remove(key); removed.add(key); return this; }
        @Override public Editor clear() { clear = true; pending.clear(); removed.clear(); return this; }

        @SuppressWarnings("unchecked")
        @Override public boolean commit() {
            synchronized (InMemorySharedPreferences.this) {
                if (clear) values.clear();
                for (String key : removed) values.remove(key);
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Set) value = new HashSet<String>((Set<String>) value);
                    values.put(entry.getKey(), value);
                }
            }
            return true;
        }
        @Override public void apply() { commit(); }
    }
}
