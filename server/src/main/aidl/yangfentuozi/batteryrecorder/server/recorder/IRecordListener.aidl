package yangfentuozi.batteryrecorder.server.recorder;

interface IRecordListener {
    void onRecord(long timestamp, long power, int status, int temp);
}