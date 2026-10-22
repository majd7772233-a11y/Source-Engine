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
    public static final int ERROR_WRITE_PERMISSION_MISSING = 13;
    public static final int ERROR_GRANT_REVOKED = 14;

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

    private static boolean isSameUri(String storedUri, Uri selectedUri) {
        if (storedUri == null || selectedUri == null) return false;
        try {
            return selectedUri.equals(Uri.parse(storedUri));
        } catch (RuntimeException e) {
            return false;
        }
    }

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

        String priorUri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TREE_URI_KEY, null);
        boolean reselectingCurrentTree = isSameUri(priorUri, uri);

        int grants = resultFlags & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return new SelectionResult(ERROR_READ_PERMISSION_MISSING, null);
        }
        if ((grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            return new SelectionResult(ERROR_WRITE_PERMISSION_MISSING, null);
        }

        PathResult mapped = Api21.resolvePath(context, uri);
        if (mapped.error != ERROR_NONE) return new SelectionResult(mapped.error, null);

        // The engine launches using a parent root + game-directory name. Selecting the
        // game directory itself would make the engine path wider than this SAF tree grant.
        if (containsGameInfo(new File(mapped.path))) {
            return new SelectionResult(ERROR_GAME_DIRECTORY_SELECTED, mapped.path);
        }

        if (!Api19.persistGrant(context, uri, grants)
                || !Api19.hasPersistedReadWriteGrant(context, uri)) {
            // Do not revoke a previously active selection merely because reselecting it failed.
            if (!reselectingCurrentTree) {
                Api19.releaseGrant(context, uri, Api19.persistedGrantFlags(context, uri));
            }
            return new SelectionResult(ERROR_GRANT_PERSIST_FAILED, null);
        }

        File selected = new File(mapped.path);
        int accessError = accessError(selected);
        if (accessError != ERROR_NONE) {
            // Preserve an existing active grant if the user reselected the same folder.
            if (!reselectingCurrentTree) Api19.releaseGrant(context, uri, grants);
            return new SelectionResult(accessError, mapped.path);
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().putString(TREE_URI_KEY, uri.toString())
                .putString(TREE_PATH_KEY, mapped.path).apply();
        // Replace the grant only after this new selection has fully passed validation and
        // its URI/path pair has been stored. Failed attempts must keep the old selection usable.
        releaseSupersededGrant(context, priorUri, uri);
        return new SelectionResult(ERROR_NONE, mapped.path);
    }

    /** Backwards-compatible boolean API for any existing Java/native callers. */
    public static boolean persistSelection(Context context, Uri uri, int resultFlags) {
        return selectTree(context, uri, resultFlags).isSuccess();
    }

    /** Backwards-compatible validated path API. */
    public static String getValidatedSelectedPath(Context context) {
        SelectionResult result = validateSelectedTree(context);
        return result.isSuccess() ? result.path : null;
    }

    /**
     * Releases a prior tree grant after a different selection is committed. Otherwise every
     * folder change accumulates unused persisted URI grants for the lifetime of the install.
     */
    private static void releaseSupersededGrant(Context context, String storedUri, Uri selectedUri) {
        if (context == null || storedUri == null || selectedUri == null) return;
        try {
            Uri previousUri = Uri.parse(storedUri);
            if (selectedUri.equals(previousUri)) return;
            int flags = Api19.persistedGrantFlags(context, previousUri);
            if (flags != 0) Api19.releaseGrant(context, previousUri, flags);
        } catch (RuntimeException ignored) { }
    }

    /**
     * True if either half of the persisted selection record exists. Callers must validate
     * partial records too; checking only the path key can accidentally skip grant validation.
     */
    public static boolean hasStoredTreeSelection(Context context) {
        if (context == null) return false;
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return preferences.getString(TREE_URI_KEY, null) != null
                || preferences.getString(TREE_PATH_KEY, null) != null;
    }

    /** Revalidate grants, volume state and canonical path before every native launch. */
    public static SelectionResult validateSelectedTree(Context context) {
        if (context == null) return new SelectionResult(ERROR_INVALID_SELECTION, null);
        if (Build.VERSION.SDK_INT < 21) return new SelectionResult(ERROR_UNSUPPORTED_ANDROID, null);
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uriValue = preferences.getString(TREE_URI_KEY, null);
        String pathValue = preferences.getString(TREE_PATH_KEY, null);
        if (uriValue == null && pathValue == null) return new SelectionResult(ERROR_INVALID_SELECTION, null);
        if (uriValue == null || pathValue == null) {
            clearSelection(context);
            return new SelectionResult(ERROR_INVALID_SELECTION, null);
        }
        final Uri uri;
        try { uri = Uri.parse(uriValue); }
        catch (RuntimeException e) {
            clearSelection(context);
            return new SelectionResult(ERROR_INVALID_SELECTION, null);
        }
        int persisted = Api19.persistedGrantFlags(context, uri);
        if ((persisted & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            clearSelection(context);
            return new SelectionResult(ERROR_GRANT_REVOKED, null);
        }
        if ((persisted & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            clearSelection(context);
            return new SelectionResult(ERROR_WRITE_PERMISSION_MISSING, null);
        }
        PathResult resolved = Api21.resolvePath(context, uri);
        if (resolved.error != ERROR_NONE) {
            clearSelection(context);
            return new SelectionResult(resolved.error, null);
        }
        if (!resolved.path.equals(pathValue)) {
            clearSelection(context);
            return new SelectionResult(ERROR_INVALID_SELECTION, null);
        }
        int accessError = accessError(new File(resolved.path));
        if (accessError != ERROR_NONE) {
            clearSelection(context);
            return new SelectionResult(accessError, null);
        }
        return new SelectionResult(ERROR_NONE, resolved.path);
    }

    /** Diagnostic string with provider/grant/access metadata only; no URI/path/file data. */
    public static String diagnosticSummary(Context context, Uri uri, int returnedFlags,
                                           SelectionResult selection) {
        String scheme = safeDiagnosticToken(uri == null ? null : uri.getScheme(), "unknown");
        String authority = safeDiagnosticToken(uri == null ? null : uri.getAuthority(), "unknown");
        int persisted = Build.VERSION.SDK_INT >= 19 ? Api19.persistedGrantFlags(context, uri) : 0;
        PathResult mapping = context != null && uri != null && Build.VERSION.SDK_INT >= 21
                ? Api21.resolvePath(context, uri)
                : new PathResult(Build.VERSION.SDK_INT < 21 ? ERROR_UNSUPPORTED_ANDROID : ERROR_INVALID_SELECTION, null);
        File directory = mapping.error == ERROR_NONE ? new File(mapping.path)
                : (selection != null && selection.path != null && selection.path.length() > 0
                        ? new File(selection.path) : null);
        boolean readable = false, writable = false;
        if (directory != null) {
            try {
                readable = directory.isDirectory() && directory.canRead();
                writable = directory.isDirectory() && directory.canWrite();
            } catch (SecurityException ignored) { }
        }
        int resultError = selection == null ? ERROR_INVALID_SELECTION : selection.error;
        return "api=" + Build.VERSION.SDK_INT + " scheme=" + scheme + " provider=" + authority
                + " returnedRead=" + hasFlag(returnedFlags, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                + " returnedWrite=" + hasFlag(returnedFlags, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                + " returnedPersistable=" + hasFlag(returnedFlags, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                + " persistedRead=" + hasFlag(persisted, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                + " persistedWrite=" + hasFlag(persisted, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                + " volumeState=" + Api21.volumeState(uri)
                + " directoryReadable=" + readable + " directoryWritable=" + writable
                + " mappingReason=" + errorName(mapping.error) + " selectionResult=" + errorName(resultError);
    }
    private static boolean hasFlag(int flags, int flag) { return (flags & flag) != 0; }
    private static String safeDiagnosticToken(String value, String fallback) {
        if (value == null || value.length() == 0) return fallback;
        String safe = value.replaceAll("[^A-Za-z0-9._:-]", "_");
        return safe.length() > 96 ? safe.substring(0, 96) : safe;
    }
    public static String errorName(int error) {
        switch (error) {
            case ERROR_NONE: return "NONE";
            case ERROR_UNSUPPORTED_ANDROID: return "UNSUPPORTED_ANDROID";
            case ERROR_UNSUPPORTED_PROVIDER: return "UNSUPPORTED_PROVIDER";
            case ERROR_INVALID_DOCUMENT_ID: return "INVALID_DOCUMENT_ID";
            case ERROR_VOLUME_UNAVAILABLE: return "VOLUME_UNAVAILABLE";
            case ERROR_NOT_DIRECTORY: return "NOT_DIRECTORY";
            case ERROR_PATH_OUTSIDE_VOLUME: return "PATH_OUTSIDE_VOLUME";
            case ERROR_READ_PERMISSION_MISSING: return "READ_PERMISSION_MISSING";
            case ERROR_GRANT_PERSIST_FAILED: return "GRANT_PERSIST_FAILED";
            case ERROR_NOT_READABLE: return "NOT_READABLE";
            case ERROR_NOT_WRITABLE: return "NOT_WRITABLE";
            case ERROR_GAME_DIRECTORY_SELECTED: return "GAME_DIRECTORY_SELECTED";
            case ERROR_INVALID_SELECTION: return "INVALID_SELECTION";
            case ERROR_WRITE_PERMISSION_MISSING: return "WRITE_PERMISSION_MISSING";
            case ERROR_GRANT_REVOKED: return "GRANT_REVOKED";
            default: return "UNKNOWN";
        }
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
            // An already-persisted grant is valid; avoid depending on provider behavior for
            // taking the exact same permission a second time.
            if ((persistedGrantFlags(context, uri) & flags) == flags) return true;
            try {
                context.getContentResolver().takePersistableUriPermission(uri, flags);
                return (persistedGrantFlags(context, uri) & flags) == flags;
            } catch (RuntimeException e) { return false; }
        }
        static boolean hasPersistedReadWriteGrant(Context context, Uri uri) {
            int required = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            return (persistedGrantFlags(context, uri) & required) == required;
        }
        static int persistedGrantFlags(Context context, Uri uri) {
            if (context == null || uri == null) return 0;
            try {
                java.util.List<android.content.UriPermission> permissions = context.getContentResolver().getPersistedUriPermissions();
                if (permissions == null) return 0;
                for (android.content.UriPermission permission : permissions) {
                    if (permission != null && uri.equals(permission.getUri())) {
                        int flags = 0;
                        if (permission.isReadPermission()) flags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                        if (permission.isWritePermission()) flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        return flags;
                    }
                }
            } catch (RuntimeException ignored) { }
            return 0;
        }
        static void releaseGrant(Context context, Uri uri, int flags) {
            if (context == null || uri == null || flags == 0) return;
            try { context.getContentResolver().releasePersistableUriPermission(uri, flags); }
            catch (RuntimeException ignored) { }
        }
    }

    /** API 21+ document-provider and mounted-volume path resolution. */
    private static final class Api21 {
        static String volumeState(Uri uri) {
            if (uri == null || !"com.android.externalstorage.documents".equals(uri.getAuthority())) return "unknown";
            try {
                String id = android.provider.DocumentsContract.getTreeDocumentId(uri);
                if (id == null) return "unknown";
                int separator = id.indexOf(':');
                if (separator <= 0) return "unknown";
                String volume = id.substring(0, separator);
                File base;
                if ("primary".equalsIgnoreCase(volume)) base = Environment.getExternalStorageDirectory();
                else {
                    if (volume.contains("/") || volume.contains("\\")
                            || ".".equals(volume) || "..".equals(volume)) return "unknown";
                    base = new File("/storage", volume);
                }
                return Environment.getExternalStorageState(base);
            } catch (RuntimeException e) { return "unavailable"; }
        }

        private static boolean hasTraversalSegment(String relative) {
            if (relative == null || relative.length() == 0) return false;
            String[] segments = relative.split("/", -1);
            for (String segment : segments) {
                if (".".equals(segment) || "..".equals(segment)) return true;
            }
            return false;
        }

        static PathResult resolvePath(Context context, Uri uri) {
            if (context == null || uri == null) return new PathResult(ERROR_INVALID_SELECTION, null);
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
            // A tree document ID describes the exact selected folder. Canonical volume
            // containment alone is not enough: "Games/../Sibling" stays on-volume but
            // would broaden the mapping outside the selected tree's scope.
            if (hasTraversalSegment(relative)) {
                return new PathResult(ERROR_PATH_OUTSIDE_VOLUME, null);
            }
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
            if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                return new PathResult(ERROR_NOT_WRITABLE, null);
            }
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
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
