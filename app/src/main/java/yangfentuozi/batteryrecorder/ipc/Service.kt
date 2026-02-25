package yangfentuozi.batteryrecorder.ipc

import android.os.IBinder
import android.os.RemoteException
import yangfentuozi.batteryrecorder.server.IService
import java.util.concurrent.CopyOnWriteArrayList

object Service {
    @Volatile
    private var mBinder: IBinder? = null
    @Volatile
    private var mService: IService? = null
    private val mDeathRecipient = IBinder.DeathRecipient {
        mBinder = null
        mService = null
        scheduleListeners()
    }
    private val mListener = CopyOnWriteArrayList<ServiceConnection>()

    var binder: IBinder?
        get() = mBinder
        set(value) {
            mBinder = value
            mService = null
            try {
                mBinder?.linkToDeath(mDeathRecipient, 0)
                mService = if (value != null) IService.Stub.asInterface(value) else null
            } catch (_: RemoteException) {
            }
            scheduleListeners()
        }

    var service: IService?
        get() = mService
        set(value) {
            mBinder = null
            mService = null
            try {
                value?.asBinder()?.linkToDeath(mDeathRecipient, 0)
                mBinder = value?.asBinder()
                mService = value
            } catch (_: RemoteException) {
            }
            scheduleListeners()
        }

    fun addListener(listener: ServiceConnection) {
        if (listener !in mListener) mListener += listener
        if (mService != null) {
            listener.onServiceConnected()
        } else {
            listener.onServiceDisconnected()
        }
    }

    fun removeListener(listener: ServiceConnection) {
        if (listener in mListener) mListener -= listener
    }

    private fun scheduleListeners() {
        val connected = mService != null
        for (listener in mListener) {
            if (connected) {
                listener.onServiceConnected()
            } else {
                listener.onServiceDisconnected()
            }
        }
    }

    interface ServiceConnection {
        fun onServiceConnected()
        fun onServiceDisconnected()
    }
}
