package android.app;

public class ActivityThread {
    private static volatile ActivityThread sCurrentActivityThread;
    boolean mSystemThread = false;

    public ActivityThread() {
        throw new UnsupportedOperationException("STUB");
    }

    public ContextImpl getSystemContext() {
        throw new UnsupportedOperationException("STUB");
    }

    public ContextImpl getSystemUiContext() {
        throw new UnsupportedOperationException("STUB");
    }
}
