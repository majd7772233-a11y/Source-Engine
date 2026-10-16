package me.nillerusr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.IOException;

/** SAF bridge for providers whose tree can be mapped to a real, readable filesystem path. */
public final class StorageAccess {
    private static final String PREFS = "mod";
    public static final String TREE_URI_KEY = "selected_tree_uri";
    public static final String TREE_PATH_KEY = "selected_tree_path";

    private StorageAccess() { }

    public static Intent createTreePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        // No guessed initial URI: the system picker chooses an available provider/volume.
        return intent;
    }

    public static String resolveFilesystemPath(Context context, Uri uri) {
        if (context == null || uri == null || Build.VERSION.SDK_INT < 21) return null;
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        final String documentId;
        try {
            documentId = DocumentsContract.getTreeDocumentId(uri);
        } catch (RuntimeException e) {
            return null;
        }
        if (documentId == null) return null;
        int separator = documentId.indexOf(':');
        if (separator <= 0) return null;
        String volume = documentId.substring(0, separator);
        String relative = documentId.substring(separator + 1);
        File base;
        if ("primary".equalsIgnoreCase(volume)) {
            base = Environment.getExternalStorageDirectory();
        } else {
            // External-storage document IDs normally use the mounted volume UUID here.
            if (volume.contains("/") || volume.contains("\\") || ".".equals(volume) || "..".equals(volume)) return null;
            base = new File("/storage", volume);
            String state;
            try { state = Environment.getExternalStorageState(base); }
            catch (RuntimeException e) { return null; }
            if (!Environment.MEDIA_MOUNTED.equals(state)) return null;
        }
        File candidate = relative.length() == 0 ? base : new File(base, relative);
        try {
            String canonicalBase = base.getCanonicalPath();
            String canonicalCandidate = candidate.getCanonicalPath();
            if (!canonicalCandidate.equals(canonicalBase)
                    && !canonicalCandidate.startsWith(canonicalBase + File.separator)) return null;
            if (!candidate.isDirectory() || !candidate.canRead() || !candidate.canWrite()) return null;
            return canonicalCandidate;
        } catch (IOException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        }
    }

    public static boolean persistSelection(Context context, Uri uri, int resultFlags) {
        if (context == null || uri == null) return false;
        try {
            int grants = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if ((grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) return false;
            context.getContentResolver().takePersistableUriPermission(uri, grants);
        } catch (SecurityException e) {
            return false;
        } catch (UnsupportedOperationException e) {
            return false;
        }
        String path = resolveFilesystemPath(context, uri);
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (path == null) {
            try { context.getContentResolver().releasePersistableUriPermission(uri, grants); }
            catch (RuntimeException ignored) { }
            preferences.edit().remove(TREE_URI_KEY).remove(TREE_PATH_KEY).apply();
            return false;
        }
        preferences.edit().putString(TREE_URI_KEY, uri.toString()).putString(TREE_PATH_KEY, path).apply();
        return true;
    }

    /** Revalidate persisted SAF grant and volume presence before using the saved path. */
    public static String getValidatedSelectedPath(Context context) {
        if (context == null) return null;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uriValue = preferences.getString(TREE_URI_KEY, null);
        String pathValue = preferences.getString(TREE_PATH_KEY, null);
        if (uriValue == null || pathValue == null) return null;
        Uri uri;
        try { uri = Uri.parse(uriValue); }
        catch (RuntimeException e) { clearSelection(context); return null; }
        boolean hasGrant = false;
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isReadPermission()) { hasGrant = true; break; }
        }
        if (!hasGrant) { clearSelection(context); return null; }
        String resolved = resolveFilesystemPath(context, uri);
        if (resolved == null || !resolved.equals(pathValue)) return null;
        return resolved;
    }

    public static void clearSelection(Context context) {
        if (context == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(TREE_URI_KEY, null);
        if (value != null) {
            Uri uri = Uri.parse(value);
            try {
                context.getContentResolver().releasePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (RuntimeException ignored) { }
        }
        preferences.edit().remove(TREE_URI_KEY).remove(TREE_PATH_KEY).apply();
    }
}
