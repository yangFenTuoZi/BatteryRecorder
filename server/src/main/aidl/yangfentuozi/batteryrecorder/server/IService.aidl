package yangfentuozi.batteryrecorder.server;

interface IService {
    void refreshConfig();
    void stopService();
    void writeToDatabaseImmediately();
}