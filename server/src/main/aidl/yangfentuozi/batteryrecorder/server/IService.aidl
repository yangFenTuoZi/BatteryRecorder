package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.IRecordListener;
import yangfentuozi.batteryrecorder.server.Config;

interface IService {
    void stopService();
    void writeToDatabaseImmediately();
    void registerRecordListener(IRecordListener listener);
    void unregisterRecordListener(IRecordListener listener);
    void updateConfig(in Config config);
    void sync();
}