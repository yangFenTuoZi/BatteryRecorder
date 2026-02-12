package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.recorder.IRecordListener;
import yangfentuozi.batteryrecorder.shared.config.Config;

interface IService {
    void stopService() = 1;

    void registerRecordListener(IRecordListener listener) = 100;
    void unregisterRecordListener(IRecordListener listener) = 101;

    void updateConfig(in Config config) = 200;

    ParcelFileDescriptor sync() = 300;
}