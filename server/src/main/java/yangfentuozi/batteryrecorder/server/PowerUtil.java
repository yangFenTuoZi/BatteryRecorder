package yangfentuozi.batteryrecorder.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class PowerUtil {
    private static final String VOLTAGE_PATH = "/sys/class/power_supply/battery/voltage_now";
    private static final String CURRENT_PATH = "/sys/class/power_supply/battery/current_now";
    private static final String CAPACITY_PATH = "/sys/class/power_supply/battery/capacity";

    private static String readLine(String path) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine().trim();
        }
    }

    public static long getVoltage() throws IOException {
        return Server.parseLong(readLine(VOLTAGE_PATH), 0);
    }

    public static long getCurrent() throws IOException {
        return Server.parseLong(readLine(CURRENT_PATH), 0);
    }

    public static int getCapacity() throws IOException {
        return Server.parseInt(readLine(CAPACITY_PATH), 0);
    }
}
