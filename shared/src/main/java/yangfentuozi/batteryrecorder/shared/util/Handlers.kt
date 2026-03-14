package yangfentuozi.batteryrecorder.shared.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

object Handlers {

    private val threadsMap = mutableMapOf<String, HandlerThread>()
    private val handlersMap = mutableMapOf<String, Handler>()

    fun initMainThread() {
        handlersMap["main"] = Handler(Looper.getMainLooper())
    }

    val main: Handler
        get() = handlersMap["main"] ?: throw IllegalStateException("not initMainThread()")
    val common: Handler = getHandler("common")

    fun getHandler(name: String): Handler {
        synchronized(this) {
            if (threadsMap[name] == null)
                threadsMap[name] = HandlerThread(name)
            if (handlersMap[name] == null)
                handlersMap[name] = Handler(threadsMap[name]!!.apply { start() }.looper)
            return handlersMap[name]!!
        }
    }

    fun interruptAll() {
        synchronized(this) {
            threadsMap.forEach { (name, thread) ->
                handlersMap.remove(name)
                thread.interrupt()
            }
        }
    }

    fun interrupt(name: String) {
        synchronized(this) {
            handlersMap.remove(name)
            threadsMap[name]?.interrupt()
        }
    }
}