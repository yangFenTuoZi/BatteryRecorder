package yangfentuozi.batteryrecorder.server;

import static yangfentuozi.batteryrecorder.server.Server.APP_DATA;
import static yangfentuozi.batteryrecorder.server.Server.APP_PACKAGE;
import static yangfentuozi.batteryrecorder.server.Server.TAG;

import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class PowerDbHelper {
    public static final File POWER_FILE = new File(APP_DATA + "/power.txt");

    private final OutputStream outputStream;

    public PowerDbHelper() throws IOException {
        if (!POWER_FILE.exists()) {
            if (!POWER_FILE.createNewFile()) {
                throw new IOException("Failed to create power data file");
            }
        }

        IPackageManager iPackageManager = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        ApplicationInfo appInfo;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0L, Os.getuid());
            } else {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0, Os.getuid());
            }
            if (appInfo == null) {
                throw new IOException("Failed to get application info for package: " + APP_PACKAGE);
            }
        } catch (RemoteException e) {
            throw new IOException("Failed to get application info for package: " + APP_PACKAGE, e);
        }
        try {
            Os.chown(POWER_FILE.getPath(), appInfo.uid, appInfo.uid);
        } catch (ErrnoException e) {
            throw new IOException("Failed to set file owner or group", e);
        }
        outputStream = new FileOutputStream(POWER_FILE, true);
    }

    public void insertRecords(List<PowerRecord> records) throws IOException {
        for (PowerRecord record : records) {
            outputStream.write((record.toString() + "\n").getBytes());
        }
        outputStream.flush();
    }

    public void close() {
        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close power data file", e);
            throw new RuntimeException(e);
        }
    }

    public record PowerRecord(long timestamp, long current, long voltage, String packageName,
                              int capacity) {
        @NonNull
        @Override
        public String toString() {
            return timestamp + "," + current + "," + voltage + "," + packageName + "," + capacity;
        }
    }
}
