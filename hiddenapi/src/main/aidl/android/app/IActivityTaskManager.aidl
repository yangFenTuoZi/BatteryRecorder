package android.app;

import android.app.ITaskStackListener;
import android.app.ActivityTaskManager;

interface IActivityTaskManager {
    void registerTaskStackListener(in ITaskStackListener listener);
    void unregisterTaskStackListener(in ITaskStackListener listener);
    ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo();
}