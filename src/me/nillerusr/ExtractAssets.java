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
  FileOutputStream os=null;InputStream is=null;
  try {
   if(mPref==null)mPref=context.getSharedPreferences("mod",0);File file=new File(context.getFilesDir(),VPK_NAME);
   if(file.isFile()&&file.length()>0&&mPref.getInt("pakversion",0)==PAK_VERSION&&!force)return true;
   is=context.getAssets().open(VPK_NAME);os=new FileOutputStream(file,false);byte[] buffer=new byte[8192];int n;
   while((n=is.read(buffer))!=-1)if(n>0)os.write(buffer,0,n);os.flush();os.getFD().sync();
   if(!file.isFile()||file.length()<=0)throw new java.io.IOException("Extracted VPK is empty");
   SharedPreferences.Editor e=mPref.edit();e.putInt("pakversion",PAK_VERSION);if(!e.commit())Log.w(TAG,"Could not persist VPK version; will recheck next launch.");
   chmod(context.getApplicationInfo().dataDir,0777);chmod(context.getFilesDir().getPath(),0777);chmod(file.getAbsolutePath(),0777);return true;
  }catch(Exception e){Log.e(TAG,"Failed to extract VPK asset "+VPK_NAME,e);return false;}
  finally{if(is!=null)try{is.close();}catch(Exception e){Log.w(TAG,"Unable to close VPK input",e);}if(os!=null)try{os.close();}catch(Exception e){Log.w(TAG,"Unable to close VPK output",e);}}
 }
}
