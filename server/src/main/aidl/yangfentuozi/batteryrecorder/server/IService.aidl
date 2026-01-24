package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.IRecordListener;

interface IService {
    void refreshConfig();
    void stopService();
    void writeToDatabaseImmediately();
    void registerRecordListener(IRecordListener listener);
    void unregisterRecordListener(IRecordListener listener);
}