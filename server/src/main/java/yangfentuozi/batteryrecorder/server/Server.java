package yangfentuozi.batteryrecorder.server;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.IActivityTaskManager;
import android.app.ITaskStackListener;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Scanner;

public class Server extends IService.Stub {
    public static final String TAG = "BatteryRecorderServer";
    public static final String APP_PACKAGE = "yangfentuozi.batteryrecorder";
    @SuppressLint("SdCardPath")
    public static final String APP_DATA = "/data/user/0/" + APP_PACKAGE;
    public static final String CONFIG = APP_DATA + "/config.prop";
    private static final String SEND_BINDER_ACTION = "yangfentuozi.batteryrecorder.intent.action.SEND_BINDER";

    private final Context mContext;
    private final IActivityTaskManager iActivityTaskManager;
    private final Handler mMainHandler;
    private final ITaskStackListener taskStackListener = new TaskStackListener() {
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
            onFocusedAppChanged(taskInfo);
        }
    };

    private HandlerThread mMonitorThread;
    private Handler mMonitorHandler;
    private long mIntervalMillis = 900;

    private PowerDataStorage storage;
    private String currForegroundApp = null;

    Server() {
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServiceImmediately));
        mContext = FakeContext.get();
        mMainHandler = new Handler(Looper.getMainLooper());
        iActivityTaskManager = IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"));

        startService();
        Looper.loop();
    }

    private void startService() {
        try {
            IPackageManager iPackageManager = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
            ApplicationInfo appInfo;
            if (Build.VERSION.SDK_INT >= 33) {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0L, Os.getuid());
            } else {
                appInfo = iPackageManager.getApplicationInfo(APP_PACKAGE, 0, Os.getuid());
            }
            if (appInfo == null) {
                throw new RuntimeException("Failed to get application info for package: " + APP_PACKAGE);
            }
            app_uid = appInfo.uid;
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to get application info for package: " + APP_PACKAGE, e);
        }

        try {
            iActivityTaskManager.registerTaskStackListener(taskStackListener);
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to register task stack listener", e);
        }

        try {
            onFocusedAppChanged(iActivityTaskManager.getFocusedRootTaskInfo());
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get focused root task info", e);
            throw new RuntimeException(e);
        }

        try {
            storage = new PowerDataStorage();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        refreshConfig();

        mMonitorThread = new HandlerThread("MonitorThread");
        mMonitorThread.start();
        mMonitorHandler = new Handler(mMonitorThread.getLooper());

        startMonitoring();

        new Thread(() -> {
            try {
                Scanner scanner = new Scanner(System.in);
                String line;
                while ((line = scanner.nextLine()) != null) {
                    if (line.trim().equals("exit")) {
                        stopService();
                    }
                }
                scanner.close();
            } catch (Throwable ignored) {
            }
        }, "InputHandler").start();
    }

    private void startMonitoring() {
        mMonitorHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    long timestamp = System.currentTimeMillis();
                    storage.insertRecord(new PowerDataStorage.PowerRecord(timestamp, PowerUtil.getCurrent(), PowerUtil.getVoltage(), currForegroundApp, PowerUtil.getCapacity()));
                } catch (IOException e) {
                    Log.e(TAG, "Error reading power data", e);
                } finally {
                    mMonitorHandler.postDelayed(this, mIntervalMillis);
                }
            }
        }, mIntervalMillis);
    }

    public void onFocusedAppChanged(TaskInfo taskInfo) {
        ComponentName componentName = taskInfo.topActivity;
        if (componentName == null) return;
        String packageName = componentName.getPackageName();
        if (packageName.equals(APP_PACKAGE)) {
            sendBinder();
        }
        currForegroundApp = packageName;
    }

    @Override
    public void refreshConfig() {
        Properties prop = new Properties();
        try (InputStream in = new FileInputStream(CONFIG)) {
            prop.load(in);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Config file not found", e);
            return;
        } catch (IOException e) {
            Log.e(TAG, "Cannot load config", e);
            return;
        }

        mIntervalMillis = parseInt(prop.getProperty("interval"), 900);
        if (mIntervalMillis < 0) mIntervalMillis = 0;
        int batchSize = parseInt(prop.getProperty("batchSize"), 20);
        if (batchSize < 0) batchSize = 0;
        else if (batchSize > 1000) batchSize = 1000;
        storage.setBatchSize(batchSize);
    }

    public static boolean parseBool(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        return switch (value.toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                Log.e(TAG, "Invalid boolean value: " + value);
                yield defaultValue;
            }
        };
    }

    public static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid integer value: " + value, e);
            return defaultValue;
        }
    }

    public static long parseLong(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid integer value: " + value, e);
            return defaultValue;
        }
    }

    @Override
    public void stopService() {
        mMainHandler.postDelayed(() -> System.exit(0), 100);
    }

    @Override
    public void writeToDatabaseImmediately() throws RemoteException {
        try {
            storage.flushBuffer();
        } catch (IOException e) {
            throw new RemoteException(Log.getStackTraceString(e));
        }
    }

    private void stopServiceImmediately() {
        if (mMonitorThread != null) {
            mMonitorThread.quitSafely();
            mMonitorThread.interrupt();
        }

        try {
            storage.flushBuffer();
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        storage.close();

        try {
            iActivityTaskManager.unregisterTaskStackListener(taskStackListener);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unregister task stack listener", e);
        }
    }

    private void sendBinder() {
        Bundle data = new Bundle();
        data.putBinder("binder", this);

        Intent intent = new Intent(SEND_BINDER_ACTION);
        intent.putExtra("data", data);

        mContext.sendBroadcast(intent);
    }

    public static int app_uid;

    public static void chown(File file) {
        try {
            Os.chown(file.getAbsolutePath(), app_uid, app_uid);
        } catch (ErrnoException e) {
            throw new RuntimeException("Failed to set file owner or group", e);
        }
    }

    public static void chownR(File file) {
        chown(file);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    chownR(child);
                }
            }
        }
    }
}
