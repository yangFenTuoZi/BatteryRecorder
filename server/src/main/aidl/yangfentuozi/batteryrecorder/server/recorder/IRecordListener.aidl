package yangfentuozi.batteryrecorder.server.recorder;

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus;

interface IRecordListener {
    void onRecord(long timestamp, long power, in BatteryStatus status, int temp);
}