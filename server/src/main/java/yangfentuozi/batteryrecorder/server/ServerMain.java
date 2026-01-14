package yangfentuozi.batteryrecorder.server;

import android.ddm.DdmHandleAppName;
import android.system.Os;

import java.io.IOException;

public class ServerMain {
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("battery_recorder", Os.getuid());
        try {
            Runtime.getRuntime().exec(new String[]{"taskset", "-ap", "1", String.valueOf(Os.getpid())});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        new Server();
    }
}
