package yangfentuozi.batteryrecorder.ui.compose.viewmodel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import yangfentuozi.batteryrecorder.Service

class MainViewModel : ViewModel() {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _showStopDialog = MutableStateFlow(false)
    val showStopDialog: StateFlow<Boolean> = _showStopDialog.asStateFlow()

    private val _showAboutDialog = MutableStateFlow(false)
    val showAboutDialog: StateFlow<Boolean> = _showAboutDialog.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceListener = object : Service.ServiceConnection {
        override fun onServiceConnected() {
            mainHandler.post {
                _serviceConnected.value = true
            }
        }

        override fun onServiceDisconnected() {
            mainHandler.post {
                _serviceConnected.value = false
            }
        }
    }

    init {
        Service.addListener(serviceListener)
        _serviceConnected.value = Service.service != null
    }

    override fun onCleared() {
        Service.removeListener(serviceListener)
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun showStopDialog() {
        _showStopDialog.value = true
    }

    fun dismissStopDialog() {
        _showStopDialog.value = false
    }

    fun stopService() {
        Thread {
            Service.service?.stopService()
        }.start()
    }

    fun showAboutDialog() {
        _showAboutDialog.value = true
    }

    fun dismissAboutDialog() {
        _showAboutDialog.value = false
    }
}
