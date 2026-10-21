package me.nillerusr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

/**
 * SAF bridge for providers that expose a real filesystem path to the native engine.
 * SAF URI grants alone cannot be passed to the current native filesystem API.
 */
public final class StorageAccess {
    private static final String PREFS = "mod";
    public static final String TREE_URI_KEY = "selected_tree_uri";
    public static final String TREE_PATH_KEY = "selected_tree_path";

    public static final int ERROR_NONE = 0;
    public static final int ERROR_UNSUPPORTED_ANDROID = 1;
    public static final int ERROR_UNSUPPORTED_PROVIDER = 2;
    public static final int ERROR_INVALID_DOCUMENT_ID = 3;
    public static final int ERROR_VOLUME_UNAVAILABLE = 4;
    public static final int ERROR_NOT_DIRECTORY = 5;
    public static final int ERROR_PATH_OUTSIDE_VOLUME = 6;
    public static final int ERROR_READ_PERMISSION_MISSING = 7;
    public static final int ERROR_GRANT_PERSIST_FAILED = 8;
    public static final int ERROR_NOT_READABLE = 9;
    public static final int ERROR_NOT_WRITABLE = 10;
    public static final int ERROR_GAME_DIRECTORY_SELECTED = 11;
    public static final int ERROR_INVALID_SELECTION = 12;

    public static final class SelectionResult {
        public final int error;
        public final String path;

        private SelectionResult(int error, String path) {
            this.error = error;
            this.path = path == null ? "" : path;
        }

        public boolean isSuccess() {
            return error == ERROR_NONE && path.length() > 0;
        }
    }

    private static final class PathResult {
        final int error;
        final String path;

        PathResult(int error, String path) {
            this.error = error;
            this.path = path == null ? "" : path;
        }
    }

    private StorageAccess() { }

    public static Intent createTreePickerIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        return intent;
    }

    /**
     * Resolves only the filesystem mapping. This does not mean that a persistent grant
     * exists or that the app can actually read/write the path.
     */
    public static String resolveFilesystemPath(Context context, Uri uri) {
        if (context == null || uri == null || Build.VERSION.SDK_INT < 21) return null;
        PathResult result = Api21.resolvePath(context, uri);
        return result.error == ERROR_NONE ? result.path : null;
    }

    /**
     * Validates and persists one selection, returning a user-actionable failure code.
     * The grant is saved only after a real path, access and path-scope have been checked.
     */
    public static SelectionResult selectTree(Context context, Uri uri, int resultFlags) {
        if (context == null || uri == null) return new SelectionResult(ERROR_INVALID_SELECTION, null);
        if (Build.VERSION.SDK_INT < 21) return new SelectionResult(ERROR_UNSUPPORTED_ANDROID, null);

        int grants = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return new SelectionResult(ERROR_READ_PERMISSION_MISSING, null);
        }

        PathResult mapped = Api21.resolvePath(context, uri);
        if (mapped.error != ERROR_NONE) return new SelectionResult(mapped.error, null);

        // The engine launches using a parent root + game-directory name. Selecting the
        // game directory itself would make the engine path wider than this SAF tree grant.
        if (containsGameInfo(new File(mapped.path))) {
            return new SelectionResult(ERROR_GAME_DIRECTORY_SELECTED, mapped.path);
        }

        if (!Api19.persistGrant(context, uri, grants)) {
            return new SelectionResult(ERROR_GRANT_PERSIST_FAILED, null);
        }

        File selected = new File(mapped.path);
        int accessError = accessError(selected);
        if (accessError != ERROR_NONE) {
            Api19.releaseGrant(context, uri, grants);
            return new SelectionResult(accessError, mapped.path);
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(TREE_URI_KEY, uri.toString())
                .putString(TREE_PATH_KEY, mapped.path).apply();
        return new SelectionResult(ERROR_NONE, mapped.path);
    }

    /** Backwards-compatible boolean API for any existing Java/native callers. */
    public static boolean persistSelection(Context context, Uri uri, int resultFlags) {
        return selectTree(context, uri, resultFlags).isSuccess();
    }

    /** Revalidate saved grant, volume state, filesystem path and current access before launch. */
    public static String getValidatedSelectedPath(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 21) return null;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uriValue = preferences.getString(TREE_URI_KEY, null);
        String pathValue = preferences.getString(TREE_PATH_KEY, null);
        if (uriValue == null || pathValue == null) return null;

        Uri uri;
        try {
            uri = Uri.parse(uriValue);
        } catch (RuntimeException e) {
            clearSelection(context);
            return null;
        }
        if (!Api19.hasPersistedReadGrant(context, uri)) {
            clearSelection(context);
            return null;
        }

        PathResult resolved = Api21.resolvePath(context, uri);
        if (resolved.error != ERROR_NONE || !resolved.path.equals(pathValue)) return null;
        return accessError(new File(resolved.path)) == ERROR_NONE ? resolved.path : null;
    }

    public static void clearSelection(Context context) {
        if (context == null) return;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = preferences.getString(TREE_URI_KEY, null);
        if (value != null && Build.VERSION.SDK_INT >= 19) {
            try {
                Uri uri = Uri.parse(value);
                int grants = Api19.persistedGrantFlags(context, uri);
                if (grants != 0) Api19.releaseGrant(context, uri, grants);
            } catch (RuntimeException ignored) { }
        }
        preferences.edit().remove(TREE_URI_KEY).remove(TREE_PATH_KEY).apply();
    }

    private static boolean containsGameInfo(File directory) {
        File[] children;
        try {
            children = directory.listFiles();
        } catch (SecurityException e) {
            return false;
        }
        if (children == null) return false;
        for (File child : children) {
            if (child != null && child.isFile()
                    && "gameinfo.txt".equalsIgnoreCase(child.getName())) return true;
        }
        return false;
    }

    private static int accessError(File directory) {
        try {
            if (!directory.isDirectory()) return ERROR_NOT_DIRECTORY;
            if (!directory.canRead()) return ERROR_NOT_READABLE;
            // The native engine can write configuration/cache data; a SAF grant by itself
            // does not make a read-only filesystem usable through java.io.File/native paths.
            if (!directory.canWrite()) return ERROR_NOT_WRITABLE;
            return ERROR_NONE;
        } catch (SecurityException e) {
            return ERROR_NOT_READABLE;
        }
    }

    /** API 19+ URI permission calls; kept out of the outer class for API 17-18. */
    private static final class Api19 {
        static boolean persistGrant(Context context, Uri uri, int flags) {
            try {
                context.getContentResolver().takePersistableUriPermission(uri, flags);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        static boolean hasPersistedReadGrant(Context context, Uri uri) {
            for (android.content.UriPermission permission
                    : context.getContentResolver().getPersistedUriPermissions()) {
                if (uri.equals(permission.getUri()) && permission.isReadPermission()) return true;
            }
            return false;
        }

        static int persistedGrantFlags(Context context, Uri uri) {
            for (android.content.UriPermission permission
                    : context.getContentResolver().getPersistedUriPermissions()) {
                if (uri.equals(permission.getUri())) {
                    int flags = 0;
                    if (permission.isReadPermission()) flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    if (permission.isWritePermission()) flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    return flags;
                }
            }
            return 0;
        }

        static void releaseGrant(Context context, Uri uri, int flags) {
            try { context.getContentResolver().releasePersistableUriPermission(uri, flags); }
            catch (RuntimeException ignored) { }
        }
    }

    /** API 21+ document-provider and mounted-volume path resolution. */
    private static final class Api21 {
        static PathResult resolvePath(Context context, Uri uri) {
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) {
                return new PathResult(ERROR_UNSUPPORTED_PROVIDER, null);
            }

            final String documentId;
            try {
                documentId = android.provider.DocumentsContract.getTreeDocumentId(uri);
            } catch (RuntimeException e) {
                return new PathResult(ERROR_INVALID_DOCUMENT_ID, null);
            }
            if (documentId == null) return new PathResult(ERROR_INVALID_DOCUMENT_ID, null);
            int separator = documentId.indexOf(':');
            if (separator <= 0) return new PathResult(ERROR_INVALID_DOCUMENT_ID, null);

            String volume = documentId.substring(0, separator);
            String relative = documentId.substring(separator + 1);
            File base;
            if ("primary".equalsIgnoreCase(volume)) {
                base = Environment.getExternalStorageDirectory();
            } else {
                if (volume.contains("/") || volume.contains("\\")
                        || ".".equals(volume) || "..".equals(volume)) {
                    return new PathResult(ERROR_INVALID_DOCUMENT_ID, null);
                }
                base = new File("/storage", volume);
            }

            final String state;
            try {
                state = Environment.getExternalStorageState(base);
            } catch (RuntimeException e) {
                return new PathResult(ERROR_VOLUME_UNAVAILABLE, null);
            }
            if (!Environment.MEDIA_MOUNTED.equals(state)
                    && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                return new PathResult(ERROR_VOLUME_UNAVAILABLE, null);
            }

            File candidate = relative.length() == 0 ? base : new File(base, relative);
            try {
                String canonicalBase = base.getCanonicalPath();
                String canonicalCandidate = candidate.getCanonicalPath();
                boolean inside = canonicalCandidate.equals(canonicalBase)
                        || canonicalCandidate.startsWith(canonicalBase.endsWith(File.separator)
                                ? canonicalBase : canonicalBase + File.separator);
                if (!inside) return new PathResult(ERROR_PATH_OUTSIDE_VOLUME, null);
                if (!candidate.isDirectory()) return new PathResult(ERROR_NOT_DIRECTORY, null);
                return new PathResult(ERROR_NONE, canonicalCandidate);
            } catch (IOException e) {
                return new PathResult(ERROR_INVALID_DOCUMENT_ID, null);
            } catch (SecurityException e) {
                return new PathResult(ERROR_NOT_READABLE, null);
            }
        }
    }
}
