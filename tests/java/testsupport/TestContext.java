package testsupport;

import android.content.Context;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import com.magd.source.engine.R;

public final class TestContext extends Context {
    private final InMemorySharedPreferences preferences = new InMemorySharedPreferences();
    private final ApplicationInfo applicationInfo = new ApplicationInfo();
    private ContentResolver contentResolver = new ContentResolver();

    public TestContext(String nativeLibraryDir) {
        applicationInfo.nativeLibraryDir = nativeLibraryDir;
    }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    @Override public ApplicationInfo getApplicationInfo() { return applicationInfo; }
    public InMemorySharedPreferences preferences() { return preferences; }
    @Override public ContentResolver getContentResolver() { return contentResolver; }
    public void setContentResolver(ContentResolver resolver) {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        contentResolver = resolver;
    }

    @Override public String getString(int id) {
        switch (id) {
            case R.string.srceng_library_status_missing_info: return "MISSING_INFO";
            case R.string.srceng_library_detail_missing_info: return "The metadata file is missing.";
            case R.string.srceng_library_status_invalid_info: return "INVALID_INFO";
            case R.string.srceng_library_detail_invalid_info: return "The metadata file is invalid.";
            case R.string.srceng_library_status_missing_assets: return "MISSING_ASSETS";
            case R.string.srceng_library_detail_missing_assets: return "Required game assets are missing.";
            case R.string.srceng_library_status_unsupported_abi: return "UNSUPPORTED_ABI";
            case R.string.srceng_library_detail_unsupported_abi: return "The device ABI is unsupported.";
            case R.string.srceng_library_status_native_missing: return "NATIVE_MISSING";
            case R.string.srceng_library_detail_native_missing: return "Required native libraries are missing.";
            case R.string.srceng_library_status_ready: return "READY";
            case R.string.srceng_library_detail_ready: return "Ready to try; compatibility is not guaranteed.";
            default: throw new IllegalArgumentException("Unexpected resource ID: " + id);
        }
    }
}
