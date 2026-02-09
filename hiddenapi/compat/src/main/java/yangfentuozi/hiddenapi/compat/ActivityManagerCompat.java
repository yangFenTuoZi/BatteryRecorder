package yangfentuozi.hiddenapi.compat;

import android.app.IActivityManager;
import android.content.AttributionSource;
import android.content.IContentProvider;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Nullable;

public class ActivityManagerCompat {

    private static final String TAG = "ActivityManagerCompat";
    private static IActivityManager service;

    private static void init() {
        if (service == null) {
            service = IActivityManager.Stub.asInterface(ServiceManager.getService("activity"));
        }
    }

    public static Bundle contentProviderCall(String authority, String method, @Nullable String arg, @Nullable Bundle extras)
            throws RemoteException {
        init();

        IContentProvider provider = null;
        try {
            var contentProviderHolder =
                    service.getContentProviderExternal(authority, 0, null, authority);
            provider = contentProviderHolder.provider;

            if (provider == null) {
                Log.e(TAG, "Provider is null");
                return null;
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "Provider is dead");
            }

            final var result = provider.call(
                    (new AttributionSource.Builder(Os.getuid())).setPackageName(null).build(),
                    authority,
                    method,
                    arg,
                    extras
            );
            Log.i(TAG, "Did ContentProvider.call");
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to do ContentProvider.call", e);
            throw e;
        } finally {
            if (provider != null) {
                try {
                    service.removeContentProviderExternal(authority, null);
                } catch (Throwable tr) {
                    Log.w(TAG, "RemoveContentProviderExternal", tr);
                }
            }
        }
    }
}
