package android.hardware.display;

import android.hardware.display.IDisplayManagerCallback;

interface IDisplayManager {
    void registerCallback(in IDisplayManagerCallback callback);
}