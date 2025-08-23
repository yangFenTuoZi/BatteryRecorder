/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: D:\Android\Android\ SDK\build-tools\35.0.0\aidl.exe -pD:\Android\Android\ SDK\platforms\android-36\framework.aidl -oD:\Android\ Project\BatteryRecorder\hiddenapi\build\generated\aidl_source_output_dir\debug\out -ID:\Android\ Project\BatteryRecorder\hiddenapi\src\main\aidl -ID:\Android\ Project\BatteryRecorder\hiddenapi\src\debug\aidl -dC:\Users\22655\AppData\Local\Temp\aidl12165654731046436516.d D:\Android\ Project\BatteryRecorder\hiddenapi\src\main\aidl\android\app\IActivityTaskManager.aidl
 */
package android.app;
public interface IActivityTaskManager extends android.os.IInterface
{
  /** Default implementation for IActivityTaskManager. */
  public static class Default implements android.app.IActivityTaskManager
  {
    @Override public void registerTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException
    {
    }
    @Override public void unregisterTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException
    {
    }
    @Override public android.app.ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.app.IActivityTaskManager
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.app.IActivityTaskManager interface,
     * generating a proxy if needed.
     */
    public static android.app.IActivityTaskManager asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.app.IActivityTaskManager))) {
        return ((android.app.IActivityTaskManager)iin);
      }
      return new android.app.IActivityTaskManager.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_registerTaskStackListener:
        {
          android.app.ITaskStackListener _arg0;
          _arg0 = android.app.ITaskStackListener.Stub.asInterface(data.readStrongBinder());
          this.registerTaskStackListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterTaskStackListener:
        {
          android.app.ITaskStackListener _arg0;
          _arg0 = android.app.ITaskStackListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterTaskStackListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getFocusedRootTaskInfo:
        {
          android.app.ActivityTaskManager.RootTaskInfo _result = this.getFocusedRootTaskInfo();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.app.IActivityTaskManager
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void registerTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerTaskStackListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unregisterTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterTaskStackListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public android.app.ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.app.ActivityTaskManager.RootTaskInfo _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getFocusedRootTaskInfo, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, android.app.ActivityTaskManager.RootTaskInfo.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_registerTaskStackListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_unregisterTaskStackListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_getFocusedRootTaskInfo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.app.IActivityTaskManager";
  public void registerTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException;
  public void unregisterTaskStackListener(android.app.ITaskStackListener listener) throws android.os.RemoteException;
  public android.app.ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo() throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
