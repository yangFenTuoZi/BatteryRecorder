package yangfentuozi.batteryrecorder.server.recorder;

import yangfentuozi.batteryrecorder.shared.data.BatteryStatus;
import yangfentuozi.batteryrecorder.shared.data.RecordsFile;

interface IRecordListener {
    void onRecord(long timestamp, long power, in BatteryStatus status, int temp);
    void onChangedCurrRecordsFile();
}