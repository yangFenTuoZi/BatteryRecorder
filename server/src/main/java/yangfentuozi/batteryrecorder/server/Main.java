package yangfentuozi.batteryrecorder.server;

import android.ddm.DdmHandleAppName;
import android.system.Os;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.IOException;

@Keep
public class Main {
    @Keep
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("battery_recorder", 0);
        try {
            Runtime.getRuntime().exec(new String[]{"taskset", "-ap", "1", String.valueOf(Os.getpid())});
        } catch (IOException e) {
            Log.e("Main", "Failed to set task affinity", e);
            throw new RuntimeException(e);
        }
        new Server();
    }
}
