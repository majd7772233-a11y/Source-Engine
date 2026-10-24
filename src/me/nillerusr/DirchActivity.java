package me.nillerusr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.magd.source.engine.R;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/** Storage picker: SAF on Android 5+, constrained legacy browsing on older Android. */
public class DirchActivity extends Activity implements View.OnClickListener {
    private static final int REQUEST_TREE = 604;
    private SharedPreferences preferences;
    private LinearLayout body;
    private TextView header;
    private String currentDirectory;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("mod", MODE_PRIVATE);
        requestWindowFeature(1);
        setContentView(R.layout.activity_directory_choice);
        body = (LinearLayout) findViewById(R.id.bodych);
        header = (TextView) findViewById(R.id.header_txt);
        Button choose = (Button) findViewById(R.id.button_choice);
        if (Build.VERSION.SDK_INT >= 21) {
            header.setText(R.string.srceng_storage_picker_explanation);
            choose.setText(R.string.srceng_storage_choose_folder);
            choose.setOnClickListener(this);
            TextView info = new TextView(this);
            info.setText(R.string.srceng_storage_saf_info);
            info.setTextColor(0xFFFFFFFF);
            info.setTextSize(15);
            LiquidGlassTheme.styleDirectoryEntry(info);
            info.setLineSpacing(dp(3), 1.0f);
            body.addView(info, new LinearLayout.LayoutParams(-1, -2));
            chooseFolder();
        } else {
            choose.setText(R.string.srceng_dir_choice_btn);
            choose.setOnClickListener(this);
            currentDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();
            listDirectory(currentDirectory);
        }
        LauncherActivity.changeButtonsStyle((ViewGroup) getWindow().getDecorView());
        LiquidGlassTheme.apply(this, findViewById(android.R.id.content),
                preferences.getBoolean("liquid_motion", true));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void chooseFolder() {
        try {
            startActivityForResult(StorageAccess.createTreePickerIntent(), REQUEST_TREE);
        } catch (RuntimeException e) {
            new AlertDialog.Builder(this).setTitle(R.string.srceng_storage_error_title)
                    .setMessage(R.string.srceng_storage_picker_unavailable)
                    .setPositiveButton(R.string.srceng_launcher_ok, null).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_TREE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, R.string.srceng_storage_selection_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        StorageAccess.SelectionResult selection = StorageAccess.selectTree(this, uri, data.getFlags());
        Log.i("SRCAPK_STORAGE", "Folder selection: "
                + StorageAccess.diagnosticSummary(this, uri, data.getFlags(), selection));
        if (!selection.isSuccess()) {
            new AlertDialog.Builder(this).setTitle(R.string.srceng_storage_error_title)
                    .setMessage(storageErrorMessage(selection.error))
                    .setPositiveButton(R.string.srceng_launcher_ok, null).show();
            return;
        }

        String gamePath = selection.path;
        String gameDir = preferences.getString("gamedir", "hl2");
        preferences.edit().putString("gamepath", normalizePath(gamePath))
                .putString("gamedir", gameDir).apply();
        Intent result = new Intent();
        result.putExtra("selected_path", normalizePath(gamePath));
        result.putExtra("selected_gamedir", gameDir);
        setResult(RESULT_OK, result);
        finish();
    }

    private int storageErrorMessage(int error) {
        switch (error) {
            case StorageAccess.ERROR_UNSUPPORTED_ANDROID:
                return R.string.srceng_storage_unsupported_android;
            case StorageAccess.ERROR_UNSUPPORTED_PROVIDER:
                return R.string.srceng_storage_unsupported_provider;
            case StorageAccess.ERROR_INVALID_DOCUMENT_ID:
                return R.string.srceng_storage_invalid_document_id;
            case StorageAccess.ERROR_VOLUME_UNAVAILABLE:
                return R.string.srceng_storage_volume_unavailable;
            case StorageAccess.ERROR_NOT_DIRECTORY:
                return R.string.srceng_storage_not_directory;
            case StorageAccess.ERROR_PATH_OUTSIDE_VOLUME:
                return R.string.srceng_storage_path_scope;
            case StorageAccess.ERROR_READ_PERMISSION_MISSING:
                return R.string.srceng_storage_no_read_grant;
            case StorageAccess.ERROR_WRITE_PERMISSION_MISSING:
                return R.string.srceng_storage_no_write_grant;
            case StorageAccess.ERROR_GRANT_REVOKED:
                return R.string.srceng_storage_revoked;
            case StorageAccess.ERROR_GRANT_PERSIST_FAILED:
                return R.string.srceng_storage_grant_failed;
            case StorageAccess.ERROR_SELECTION_SAVE_FAILED:
                return R.string.srceng_storage_selection_save_failed;
            case StorageAccess.ERROR_NOT_READABLE:
                return R.string.srceng_storage_not_readable;
            case StorageAccess.ERROR_NOT_WRITABLE:
                return R.string.srceng_storage_not_writable;
            case StorageAccess.ERROR_GAME_DIRECTORY_SELECTED:
                return R.string.srceng_storage_game_folder_selected;
            default:
                return R.string.srceng_storage_invalid_selection;
        }
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        String value = path.trim();
        while (value.length() > 1 && value.endsWith(File.separator)) value = value.substring(0, value.length() - 1);
        try { return new File(value).getCanonicalPath(); }
        catch (IOException e) { return new File(value).getAbsolutePath(); }
    }

    @Override public void onClick(View view) {
        if (Build.VERSION.SDK_INT >= 21) { chooseFolder(); return; }
        if (currentDirectory == null) return;
        File chosen = new File(currentDirectory);
        if (!chosen.isDirectory() || !chosen.canRead() || !chosen.canWrite()) {
            Toast.makeText(this, R.string.srceng_storage_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        String gamePath = currentDirectory;
        String gameDir = preferences.getString("gamedir", "hl2");
        if (new File(chosen, "gameinfo.txt").isFile()) {
            File parent = chosen.getParentFile();
            if (parent != null) {
                gamePath = parent.getAbsolutePath();
                gameDir = chosen.getName();
            }
        }
        preferences.edit().putString("gamepath", normalizePath(gamePath))
                .putString("gamedir", gameDir).apply();
        Intent result = new Intent();
        result.putExtra("selected_path", normalizePath(gamePath));
        result.putExtra("selected_gamedir", gameDir);
        setResult(RESULT_OK, result);
        finish();
    }

    private void listDirectory(String path) {
        File directory = new File(path);
        File[] children;
        try { children = directory.listFiles(new java.io.FileFilter() {
            @Override public boolean accept(File file) { return file.isDirectory(); }
        }); } catch (SecurityException e) { children = null; }
        if (children == null || !directory.canRead()) {
            Toast.makeText(this, R.string.srceng_storage_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) { return a.getName().compareToIgnoreCase(b.getName()); }
        });
        try { currentDirectory = directory.getCanonicalPath(); }
        catch (IOException ignored) { currentDirectory = directory.getAbsolutePath(); }
        header.setText(currentDirectory);
        body.removeAllViews();
        File parent = directory.getParentFile();
        String primaryRoot = normalizePath(Environment.getExternalStorageDirectory().getAbsolutePath());
        boolean insidePrimary = currentDirectory.equals(primaryRoot)
                || currentDirectory.startsWith(primaryRoot + File.separator);
        addDirectoryEntry("..", !insidePrimary || currentDirectory.equals(primaryRoot) || parent == null
                ? currentDirectory : parent.getAbsolutePath());
        for (File child : children) addDirectoryEntry(child.getName(), child.getAbsolutePath());
    }

    private void addDirectoryEntry(String label, final String path) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setSingleLine(true);
        item.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LiquidGlassTheme.styleDirectoryEntry(item);
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { listDirectory(path); }
        });
        body.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }
}
