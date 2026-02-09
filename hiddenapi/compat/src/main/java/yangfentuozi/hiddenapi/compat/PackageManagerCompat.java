package yangfentuozi.hiddenapi.compat;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;

public class PackageManagerCompat {

    private static IPackageManager service;

    private static void init() {
        if (service == null) {
            service = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        }
    }

    public static ApplicationInfo getApplicationInfo(String packageName, long flags, int userId)
            throws RemoteException {
        init();

        ApplicationInfo applicationInfo;
        if (Build.VERSION.SDK_INT >= 33) {
            applicationInfo = service.getApplicationInfo(packageName, flags, Os.getuid());
        } else {
            applicationInfo = service.getApplicationInfo(packageName, (int) flags, Os.getuid());
        }
        return applicationInfo;
    }
}
