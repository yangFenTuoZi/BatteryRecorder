package yangfentuozi.batteryrecorder.server;

import android.ddm.DdmHandleAppName;

import androidx.annotation.Keep;

@Keep
public class Main {
    @Keep
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("battery_recorder", 0);
        /* ColorOS 调度使得某些 Soc 的 1 核在息屏后被禁用，会导致某些机型息屏后无法正常记录，故不自行 taskset，全权交由系统调度
        try {
            Runtime.getRuntime().exec(new String[]{"taskset", "-ap", "1", String.valueOf(Os.getpid())});
        } catch (IOException e) {
            Log.e("Main", "Failed to set task affinity", e);
            throw new RuntimeException(e);
        }
        */
        new Server();
    }
}
