package yangfentuozi.batteryrecorder.server;

interface IRecordListener {
    void onRecord(long timestamp, long power, int status);
}