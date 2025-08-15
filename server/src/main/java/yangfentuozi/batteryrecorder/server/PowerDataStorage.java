package yangfentuozi.batteryrecorder.server;

import static yangfentuozi.batteryrecorder.server.Server.APP_DATA;
import static yangfentuozi.batteryrecorder.server.Server.TAG;
import static yangfentuozi.batteryrecorder.server.Server.chown;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PowerDataStorage {
    public static final File POWER_DIR = new File(APP_DATA + "/power_data");

    private OutputStream outputStream;
    private boolean lastCurrentPositive = true;
    private boolean firstRecord = true;

    private final StringBuilder buffer = new StringBuilder(4096);
    private int batchCount = 0;
    private int batchSize = 20;

    public PowerDataStorage() throws IOException {
        if (!POWER_DIR.exists()) {
            if (!POWER_DIR.mkdirs()) {
                throw new IOException("Failed to create power data directory");
            }
            chown(POWER_DIR);
        } else if (!POWER_DIR.isDirectory()) {
            throw new IOException("Power data path is not a directory: " + POWER_DIR.getAbsolutePath());
        }
    }

    private void startNewSegment(long timestamp, boolean currentPositive) throws IOException {
        flushBuffer();
        closeCurrentSegment();

        String fileName = timestamp + (currentPositive ? "+" : "-") + ".txt";
        File segmentFile = new File(POWER_DIR, fileName);
        if (!segmentFile.exists() && !segmentFile.createNewFile()) {
            throw new IOException("Failed to create segment file: " + segmentFile.getAbsolutePath());
        }
        chown(segmentFile);
        outputStream = new FileOutputStream(segmentFile, true);
        lastCurrentPositive = currentPositive;
    }

    public void insertRecord(PowerRecord record) throws IOException {
        boolean currentPositive = record.current >= 0;
        if (firstRecord) {
            startNewSegment(record.timestamp, currentPositive);
            firstRecord = false;
        } else if (currentPositive != lastCurrentPositive) {
            // 电流符号变化 -> 新段
            startNewSegment(record.timestamp, currentPositive);
        }

        buffer.append(record).append("\n");
        batchCount++;

        if (batchCount >= batchSize) {
            flushBuffer();
        }
    }

    void flushBuffer() throws IOException {
        if (batchCount == 0 || outputStream == null) return;
        outputStream.write(buffer.toString().getBytes());
        outputStream.flush();
        buffer.setLength(0); // 清空 StringBuilder
        batchCount = 0;
    }

    private void closeCurrentSegment() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close segment file", e);
            }
            outputStream = null;
        }
    }

    public void close() {
        try {
            flushBuffer();
        } catch (IOException e) {
            Log.e(TAG, "Error flushing buffer before close", e);
        }
        closeCurrentSegment();
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
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
