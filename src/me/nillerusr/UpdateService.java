package me.nillerusr;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Deliberately disabled. The legacy updater did not verify APK signatures or checksums,
 * so it must not turn an intent extra into an arbitrary browser destination.
 */
public final class UpdateService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w("SRCAPK.Update", "Legacy update notification ignored: verified release installation is not implemented.");
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
