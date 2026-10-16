package me.nillerusr;
import android.content.SharedPreferences;
import java.io.FileOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import android.util.Log;
import android.content.Context;
import android.content.pm.ApplicationInfo;

public class ExtractAssets
{
	public static String TAG = "ExtractAssets";
	static SharedPreferences mPref;

	public static final String VPK_NAME = "extras_dir.vpk";
	public static int PAK_VERSION = 9;

    private static int chmod(String path, int mode)
    {
		int ret = -1;

		try
		{
			ret = Runtime.getRuntime().exec("chmod " + Integer.toOctalString(mode) + " " + path).waitFor();
			Log.d(TAG, "chmod " + Integer.toOctalString(mode) + " " + path + ": " + ret );
		}
		catch(Exception e)
		{
			ret = -1;
			Log.d(TAG, "chmod: Runtime not worked: " + e.toString() );
		}

		try
		{
			Class fileUtils = Class.forName("android.os.FileUtils");
			Method setPermissions = fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
			ret = (Integer) setPermissions.invoke(null, path, mode, -1, -1);
		}
		catch(Exception e)
		{
			ret = -1;
			Log.d(TAG, "chmod: FileUtils not worked: " + e.toString() );
		}

		return ret;
	}

    public static boolean extractVPK(Context context, boolean force) {
        FileOutputStream output = null;
        InputStream input = null;
        try {
            if (mPref == null) {
                mPref = context.getSharedPreferences("mod", 0);
            }

            File file = new File(context.getFilesDir(), VPK_NAME);
            if (file.isFile() && file.length() > 0
                    && mPref.getInt("pakversion", 0) == PAK_VERSION && !force) {
                return true;
            }

            input = context.getAssets().open(VPK_NAME);
            output = new FileOutputStream(file, false);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (length > 0) {
                    output.write(buffer, 0, length);
                }
            }
            output.flush();
            output.getFD().sync();

            if (!file.isFile() || file.length() <= 0) {
                throw new java.io.IOException("Extracted VPK is empty");
            }

            SharedPreferences.Editor editor = mPref.edit();
            editor.putInt("pakversion", PAK_VERSION);
            if (!editor.commit()) {
                Log.w(TAG, "Could not persist VPK version; extraction will be checked again next launch.");
            }

            chmod(context.getApplicationInfo().dataDir, 0777);
            chmod(context.getFilesDir().getPath(), 0777);
            chmod(file.getAbsolutePath(), 0777);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract VPK asset " + VPK_NAME, e);
            return false;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception e) {
                    Log.w(TAG, "Unable to close VPK input stream", e);
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (Exception e) {
                    Log.w(TAG, "Unable to close VPK output stream", e);
                }
            }
        }
    }
}
