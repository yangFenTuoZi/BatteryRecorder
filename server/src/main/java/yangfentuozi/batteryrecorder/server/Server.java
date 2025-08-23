package yangfentuozi.batteryrecorder.server;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.ITaskStackListener;
import android.app.TaskInfo;
import android.app.TaskStackListener;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.IContentProvider;
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
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import yangfentuozi.hiddenapi.compat.ServiceManagerCompat;

public class Server extends IService.Stub {
    public static final String TAG = "BatteryRecorderServer";
    public static final String APP_PACKAGE = "yangfentuozi.batteryrecorder";
    @SuppressLint("SdCardPath")
    public static final String APP_DATA = "/data/user/0/" + APP_PACKAGE;
    public static final String CONFIG = APP_DATA + "/shared_prefs/" + APP_PACKAGE + "_preferences.xml";

    private final IActivityTaskManager iActivityTaskManager;
    private final IActivityManager iActivityManager;
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
        mMainHandler = new Handler(Looper.getMainLooper());
        ServiceManagerCompat.waitService("activity");
        ServiceManagerCompat.waitService("activity_task");
        iActivityTaskManager = IActivityTaskManager.Stub.asInterface(ServiceManager.getService("activity_task"));
        iActivityManager = IActivityManager.Stub.asInterface(ServiceManager.getService("activity"));

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
        var config = new File(CONFIG);
        if (!config.exists()) {
            Log.e(TAG, "Config file not found");
            return;
        }

        try (FileInputStream fis = new FileInputStream(config)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(fis, "UTF-8");

            int eventType = parser.getEventType();
            mIntervalMillis = 900;
            int batchSize = 20;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if ("int".equals(tagName)) {
                        String nameAttr = parser.getAttributeValue(null, "name");
                        String valueAttr = parser.getAttributeValue(null, "value");

                        if ("interval".equals(nameAttr)) {
                            mIntervalMillis = parseInt(valueAttr, 900);
                        } else if ("batch_size".equals(nameAttr)) {
                            batchSize = parseInt(valueAttr, 20);
                        }
                    }
                }
                eventType = parser.next();
            }

            if (mIntervalMillis < 0) mIntervalMillis = 0;
            if (batchSize < 0) batchSize = 0;
            else if (batchSize > 1000) batchSize = 1000;
            storage.setBatchSize(batchSize);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Config file not found", e);
        } catch (IOException e) {
            Log.e(TAG, "Error reading config file", e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Error parsing config file", e);
        }
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
        Log.i(TAG, "stopService called");
        mMainHandler.post(() -> {
            try {
                stopServiceImmediately();
                Log.i(TAG, "stopService cleanup complete, exiting");
            } catch (Throwable t) {
                Log.e(TAG, "Error during stopService cleanup", t);
            } finally {
                System.exit(0);
            }
        });
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
        Log.i(TAG, "stopServiceImmediately begin");
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
        Log.i(TAG, "stopServiceImmediately done");
    }

    private void sendBinder() {
        IContentProvider provider = null;
        String name = "yangfentuozi.batteryrecorder.binderProvider";
        try {
            ContentProviderHolder contentProviderHolder=
                    iActivityManager.getContentProviderExternal(name, 0, null, name);
            provider = contentProviderHolder != null ? contentProviderHolder.provider : null;

            if (provider == null) {
                Log.e(TAG, "Provider is null");
                return;
            }
            if (!provider.asBinder().pingBinder()) {
                Log.e(TAG, "Provider is dead");
            }

            Bundle extras = new Bundle();
            extras.putBinder("binder", this);

            provider.call((new AttributionSource.Builder(Os.getuid())).setPackageName(null).build(), name, "setBinder", null, extras);
            Log.i(TAG, "Send binder");
        } catch (RemoteException e) {
            Log.e(TAG, "Failed send binder", e);
        } finally {
            if (provider != null) {
                try {
                    iActivityManager.removeContentProviderExternal(name, null);
                } catch (Throwable tr) {
                    Log.w(TAG, "RemoveContentProviderExternal", tr);
                }
            }
        }
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
