package android.app;

import android.content.ComponentName;
import android.os.RemoteException;

public abstract class TaskStackListener extends ITaskStackListener.Stub {
    public void setIsLocal() {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskStackChanged() throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityPinned(String packageName, int userId, int taskId, int rootTaskId)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityUnpinned() throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task, boolean homeTaskVisible,
                                         boolean clearedTask, boolean wasVisible) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityForcedResizable(String packageName, int taskId, int reason)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityDismissingDockedTask() throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityLaunchOnSecondaryDisplayFailed(ActivityManager.RunningTaskInfo taskInfo,
                                                         int requestedDisplayId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityLaunchOnSecondaryDisplayFailed() throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityLaunchOnSecondaryDisplayRerouted(ActivityManager.RunningTaskInfo taskInfo,
                                                           int requestedDisplayId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskCreated(int taskId, ComponentName componentName) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskRemoved(int taskId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }


    @Deprecated
    public void onTaskMovedToFront(int taskId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskRemovalStarted(ActivityManager.RunningTaskInfo taskInfo)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }


    @Deprecated
    public void onTaskRemovalStarted(int taskId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskDescriptionChanged(ActivityManager.RunningTaskInfo taskInfo)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }


    @Deprecated
    public void onTaskDescriptionChanged(int taskId, ActivityManager.TaskDescription td)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskProfileLocked(ActivityManager.RunningTaskInfo taskInfo) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

//    @Override
//    public void onTaskSnapshotChanged(int taskId, TaskSnapshot snapshot) throws RemoteException {
//        throw new UnsupportedOperationException("STUB");
//    }

    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo)
            throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskDisplayChanged(int taskId, int newDisplayId) throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onRecentTaskListUpdated() throws RemoteException {
        throw new UnsupportedOperationException("STUB");
    }

    public void onRecentTaskListFrozenChanged(boolean frozen) {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskFocusChanged(int taskId, boolean focused) {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskRequestedOrientationChanged(int taskId, int requestedOrientation) {
        throw new UnsupportedOperationException("STUB");
    }

    public void onActivityRotation(int displayId) {
        throw new UnsupportedOperationException("STUB");
    }

    public void onTaskMovedToBack(ActivityManager.RunningTaskInfo taskInfo) {
        throw new UnsupportedOperationException("STUB");
    }

    public void onLockTaskModeChanged(int mode) {
        throw new UnsupportedOperationException("STUB");
    }

}
