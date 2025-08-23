package yangfentuozi.hiddenapi.compat;

import android.os.ServiceManager;
import android.util.Log;

public class ServiceManagerCompat {
    public static final String TAG = "ServiceManagerCompat";

    public static void waitService(String name) {
        while (ServiceManager.getService(name) == null) {
            try {
                Log.i(TAG, "service " + name + " is not started, wait 1s.");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, e.getMessage(), e);
            }
        }
    }
}
