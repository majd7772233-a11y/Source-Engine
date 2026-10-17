package me.nillerusr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts the bundled runtime VPK atomically. A partial write must never be
 * mistaken for a valid cached asset after a failed/forced extraction.
 */
public final class ExtractAssets {
    public static final String TAG = "ExtractAssets";
    public static final String VPK_NAME = "extras_dir.vpk";
    public static final int PAK_VERSION = 9;
    private static final String PAK_BYTES_KEY = "pakbytes";
    private static final String TEMP_SUFFIX = ".tmp";

    private ExtractAssets() { }

    public static boolean extractVPK(Context context, boolean force) {
        if (context == null) {
            Log.e(TAG, "Cannot extract runtime asset without an Android context.");
            return false;
        }

        SharedPreferences preferences = context.getSharedPreferences("mod", Context.MODE_PRIVATE);
        File destination = new File(context.getFilesDir(), VPK_NAME);
        long expectedBytes = preferences.getLong(PAK_BYTES_KEY, -1L);
        if (!force && destination.isFile() && destination.length() > 0
                && preferences.getInt("pakversion", 0) == PAK_VERSION
                && expectedBytes > 0 && destination.length() == expectedBytes) {
            return true;
        }

        File temporary = new File(context.getFilesDir(), VPK_NAME + TEMP_SUFFIX);
        InputStream input = null;
        FileOutputStream output = null;
        boolean installed = false;
        long bytesWritten = 0L;
        try {
            if (temporary.exists() && !temporary.delete()) {
                throw new IOException("Could not remove a stale temporary runtime file.");
            }

            input = context.getAssets().open(VPK_NAME);
            output = new FileOutputStream(temporary, false);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (length == 0) continue;
                output.write(buffer, 0, length);
                bytesWritten += length;
            }
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            input.close();
            input = null;

            if (!temporary.isFile() || bytesWritten <= 0 || temporary.length() != bytesWritten) {
                throw new IOException("Extracted runtime asset is empty or incomplete.");
            }

            // Rename only after the complete temporary file has been synced.
            File backup = new File(context.getFilesDir(), VPK_NAME + ".bak");
            if (backup.exists() && !backup.delete()) {
                throw new IOException("Could not clear the previous runtime backup.");
            }
            boolean hadDestination = destination.exists();
            if (hadDestination && !destination.renameTo(backup)) {
                throw new IOException("Could not preserve the previous runtime asset.");
            }
            if (!temporary.renameTo(destination)) {
                if (hadDestination && backup.exists() && !backup.renameTo(destination)) {
                    Log.e(TAG, "Failed to restore the previous runtime asset after replacement failure.");
                }
                throw new IOException("Could not install the completed runtime asset.");
            }
            installed = true;
            if (backup.exists() && !backup.delete()) {
                Log.w(TAG, "Could not remove the previous runtime asset backup.");
            }

            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("pakversion", PAK_VERSION);
            editor.putLong(PAK_BYTES_KEY, bytesWritten);
            if (!editor.commit()) {
                Log.w(TAG, "Could not persist runtime asset metadata; it will be validated again next launch.");
            }
            return destination.isFile() && destination.length() == bytesWritten;
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract bundled runtime asset; check available internal storage and retry.", e);
            return false;
        } catch (RuntimeException e) {
            Log.e(TAG, "Runtime asset extraction failed; check available internal storage and retry.", e);
            return false;
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException e) { Log.w(TAG, "Could not close runtime asset input.", e); }
            }
            if (output != null) {
                try { output.close(); } catch (IOException e) { Log.w(TAG, "Could not close runtime asset output.", e); }
            }
            if (!installed && temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "Could not remove incomplete temporary runtime asset.");
            }
        }
    }
}
