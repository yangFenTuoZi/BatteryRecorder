package android.hardware.display;

interface IDisplayManagerCallback {
    oneway void onDisplayEvent(int displayId, int event);
}