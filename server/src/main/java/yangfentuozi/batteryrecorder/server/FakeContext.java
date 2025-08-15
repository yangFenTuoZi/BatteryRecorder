package yangfentuozi.batteryrecorder.server;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.lang.reflect.Field;

public final class FakeContext extends ContextWrapper {

    public static final int UID = Os.getuid();
    public static final String PACKAGE_NAME = UID == 0 ? "root" : "com.android.shell";

    private static final FakeContext INSTANCE;

    static {
        try {
            var activityThreadConstructor = ActivityThread.class.getDeclaredConstructor();
            activityThreadConstructor.setAccessible(true);
            ActivityThread activityThread = activityThreadConstructor.newInstance();

            Field sCurrentActivityThreadField = ActivityThread.class.getDeclaredField("sCurrentActivityThread");
            sCurrentActivityThreadField.setAccessible(true);
            sCurrentActivityThreadField.set(null, activityThread);

            @SuppressLint("SoonBlockedPrivateApi") Field mSystemThreadField = ActivityThread.class.getDeclaredField("mSystemThread");
            mSystemThreadField.setAccessible(true);
            mSystemThreadField.setBoolean(activityThread, true);

            INSTANCE = new FakeContext(activityThread.getSystemContext());
        } catch (Exception e){
            throw new AssertionError(e);
        }
    }

    public static FakeContext get() {
        return INSTANCE;
    }

    private FakeContext(Context context) {
        super(context);
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }

    @NonNull
    @Override
    public String getOpPackageName() {
        return PACKAGE_NAME;
    }

    @NonNull
    @Override
    @RequiresApi(api = Build.VERSION_CODES.S)
    public AttributionSource getAttributionSource() {
        AttributionSource.Builder builder = new AttributionSource.Builder(UID);
        builder.setPackageName(PACKAGE_NAME);
        return builder.build();
    }

    // @Override to be added on SDK upgrade for Android 14
    @SuppressWarnings("unused")
    public int getDeviceId() {
        return 0;
    }
}