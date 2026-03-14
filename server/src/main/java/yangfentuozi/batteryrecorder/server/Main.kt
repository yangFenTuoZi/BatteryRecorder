package yangfentuozi.batteryrecorder.server;

import android.ddm.DdmHandleAppName;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import yangfentuozi.batteryrecorder.shared.util.LoggerX;

@Keep
public class Main {
    private static final String TAG = "ServerMain";
    private static final File OOM_SCORE_ADJ_FILE = new File("/proc/self/oom_score_adj");
    private static final String OOM_SCORE_ADJ_VALUE = "-1000\n";

    @Keep
    public static void main(String[] args) {
        DdmHandleAppName.setAppName("battery_recorder", 0);
        // 设置OOM保活
        setSelfOomScoreAdj();
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

    private static void setSelfOomScoreAdj() {
        try {
            try (FileOutputStream outputStream = new FileOutputStream(OOM_SCORE_ADJ_FILE)) {
                outputStream.write(OOM_SCORE_ADJ_VALUE.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            String actualValue;
            try (BufferedReader reader = new BufferedReader(new FileReader(OOM_SCORE_ADJ_FILE))) {
                actualValue = reader.readLine();
            }
            if (actualValue == null) {
                actualValue = "";
            }
            actualValue = actualValue.trim();
            if (!"-1000".equals(actualValue)) {
                LoggerX.e(TAG, "[启动] 设置 oom_score_adj 失败，期望=-1000，实际=" + actualValue);
                return;
            }
            LoggerX.i(TAG, "[启动] 设置 oom_score_adj 成功，实际=-1000");
        } catch (IOException | RuntimeException e) {
            LoggerX.e(TAG, "[启动] 设置 oom_score_adj 失败", e);
        }
    }
}
