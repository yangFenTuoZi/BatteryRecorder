package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.recorder.IRecordListener;
import yangfentuozi.batteryrecorder.config.Config;

interface IService {
    void stopService();
    void writeToDatabaseImmediately();
    void registerRecordListener(IRecordListener listener);
    void unregisterRecordListener(IRecordListener listener);
    void updateConfig(in Config config);
    void sync();
}