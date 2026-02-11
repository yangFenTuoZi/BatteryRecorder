package yangfentuozi.batteryrecorder.server;

import yangfentuozi.batteryrecorder.server.recorder.IRecordListener;
import yangfentuozi.batteryrecorder.shared.config.Config;

interface IService {
    void stopService();
    void registerRecordListener(IRecordListener listener);
    void unregisterRecordListener(IRecordListener listener);
    void updateConfig(in Config config);
    ParcelFileDescriptor sync();
}