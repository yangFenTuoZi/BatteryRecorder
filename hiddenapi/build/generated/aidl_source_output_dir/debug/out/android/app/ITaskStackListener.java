/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: D:\Android\Android\ SDK\build-tools\35.0.0\aidl.exe -pD:\Android\Android\ SDK\platforms\android-36\framework.aidl -oD:\Android\ Project\BatteryRecorder\hiddenapi\build\generated\aidl_source_output_dir\debug\out -ID:\Android\ Project\BatteryRecorder\hiddenapi\src\main\aidl -ID:\Android\ Project\BatteryRecorder\hiddenapi\src\debug\aidl -dC:\Users\22655\AppData\Local\Temp\aidl11731564404240595910.d D:\Android\ Project\BatteryRecorder\hiddenapi\src\main\aidl\android\app\ITaskStackListener.aidl
 */
package android.app;
public interface ITaskStackListener extends android.os.IInterface
{
  /** Default implementation for ITaskStackListener. */
  public static class Default implements android.app.ITaskStackListener
  {
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.app.ITaskStackListener
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.app.ITaskStackListener interface,
     * generating a proxy if needed.
     */
    public static android.app.ITaskStackListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.app.ITaskStackListener))) {
        return ((android.app.ITaskStackListener)iin);
      }
      return new android.app.ITaskStackListener.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements android.app.ITaskStackListener
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
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.app.ITaskStackListener";
  }
}
