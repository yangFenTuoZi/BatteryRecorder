package yangfentuozi.batteryrecorder.server;

import android.ddm.DdmHandleAppName;
import android.system.Os;

public class ServerMain {
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("battery_recorder", Os.getuid());
        new Server();
    }
}
