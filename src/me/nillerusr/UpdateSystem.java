package me.nillerusr;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.magd.source.engine.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Checks a version marker only. This class never downloads, installs, or executes an APK.
 * Updates are advisory and only the pinned HTTPS deployment host is accepted.
 */
public class UpdateSystem extends AsyncTask<String, Integer, String> {
    private static final String TAG = "SRCAPK.Update";
    private static final String DEPLOY_ROOT = "https://raw.githubusercontent.com/nillerusr/srceng-deploy/";
    private static final Pattern SAFE_BRANCH = Pattern.compile("[A-Za-z0-9_-]{1,80}");
    private static final Pattern COMMIT_SHA = Pattern.compile("[0-9a-fA-F]{7,64}");

    private final String deployBranch;
    private final String lastCommit;
    private final Context appContext;

    public UpdateSystem(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
        deployBranch = context == null ? "" : context.getResources().getString(R.string.deploy_branch);
        lastCommit = context == null ? "" : context.getResources().getString(R.string.last_commit);
    }

    @Override
    protected String doInBackground(String... params) {
        if (appContext == null || !SAFE_BRANCH.matcher(deployBranch).matches()
                || deployBranch.contains("..")) {
            Log.w(TAG, "Update check skipped: deployment branch is invalid.");
            return null;
        }

        HttpURLConnection connection = null;
        InputStream input = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(DEPLOY_ROOT + deployBranch + "/version");
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || !"raw.githubusercontent.com".equalsIgnoreCase(url.getHost())) {
                Log.w(TAG, "Update check skipped: deployment endpoint is not trusted.");
                return null;
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            input = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder value = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (value.length() + line.length() > 128) {
                    Log.w(TAG, "Update check skipped: version response is too large.");
                    return null;
                }
                value.append(line);
            }
            String remote = value.toString().trim();
            return COMMIT_SHA.matcher(remote).matches() ? remote : null;
        } catch (IOException e) {
            Log.w(TAG, "Update check failed; the app remains usable offline.", e);
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException e) { Log.w(TAG, "Could not close update response.", e); }
            else if (input != null) try { input.close(); } catch (IOException e) { Log.w(TAG, "Could not close update response.", e); }
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    protected void onPostExecute(String result) {
        // Do not show an arbitrary URL or launch downloaded code. The current project
        // has no signed release manifest or verified update-install flow.
        if (result != null && !result.equalsIgnoreCase(lastCommit)) {
            Log.i(TAG, "A newer deployment marker exists; automatic APK updates are disabled until release verification is implemented.");
        }
    }
}
