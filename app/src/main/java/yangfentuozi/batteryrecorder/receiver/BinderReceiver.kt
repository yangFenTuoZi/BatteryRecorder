package yangfentuozi.batteryrecorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import yangfentuozi.batteryrecorder.Service

class BinderReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "yangfentuozi.batteryrecorder.intent.action.BINDER") {
            Service.binder = intent.getBundleExtra("data")?.getBinder("binder")
        }
    }
}