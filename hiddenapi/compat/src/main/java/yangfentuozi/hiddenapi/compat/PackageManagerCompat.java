package yangfentuozi.hiddenapi.compat;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.Os;

import androidx.annotation.NonNull;

public class PackageManagerCompat {

    private static IPackageManager service;

    private static void init() {
        if (service == null) {
            service = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        }
    }

    @NonNull
    public static ApplicationInfo getApplicationInfo(String packageName, long flags, int userId)
            throws RemoteException, PackageManager.NameNotFoundException {
        init();

        ApplicationInfo applicationInfo;
        if (Build.VERSION.SDK_INT >= 33) {
            applicationInfo = service.getApplicationInfo(packageName, flags, Os.getuid());
        } else {
            applicationInfo = service.getApplicationInfo(packageName, (int) flags, Os.getuid());
        }
        if (applicationInfo == null) throw new PackageManager.NameNotFoundException();
        return applicationInfo;
    }
}
