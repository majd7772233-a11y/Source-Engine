package android.content;

import android.content.pm.ApplicationInfo;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract ApplicationInfo getApplicationInfo();
    public abstract String getString(int resourceId);
    public abstract ContentResolver getContentResolver();
}
