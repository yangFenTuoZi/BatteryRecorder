package yangfentuozi.batteryrecorder.ui.compose.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.Service
import yangfentuozi.batteryrecorder.util.PowerStats
import yangfentuozi.batteryrecorder.util.StatisticsUtil
import java.io.File

class MainViewModel : ViewModel() {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _showStopDialog = MutableStateFlow(false)
    val showStopDialog: StateFlow<Boolean> = _showStopDialog.asStateFlow()

    private val _showAboutDialog = MutableStateFlow(false)
    val showAboutDialog: StateFlow<Boolean> = _showAboutDialog.asStateFlow()

    private val _chargeStats = MutableStateFlow<PowerStats?>(null)
    val chargeStats: StateFlow<PowerStats?> = _chargeStats.asStateFlow()

    private val _dischargeStats = MutableStateFlow<PowerStats?>(null)
    val dischargeStats: StateFlow<PowerStats?> = _dischargeStats.asStateFlow()

    private val _isLoadingStats = MutableStateFlow(false)
    val isLoadingStats: StateFlow<Boolean> = _isLoadingStats.asStateFlow()

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

    fun loadStatistics(context: Context) {
        if (_isLoadingStats.value) return

        viewModelScope.launch {
            _isLoadingStats.value = true
            try {
                val powerDir = File(context.dataDir, "power_data")
                val chargeDir = File(powerDir, "charge")
                val dischargeDir = File(powerDir, "discharge")
                val cacheDir = File(context.cacheDir, "power_stats")

                // 获取最新文件
                val latestChargeFile = chargeDir.listFiles()?.filter { it.isFile }
                    ?.maxByOrNull { it.lastModified() }?.name
                val latestDischargeFile = dischargeDir.listFiles()?.filter { it.isFile }
                    ?.maxByOrNull { it.lastModified() }?.name

                // 加载充电统计
                if (chargeDir.exists() && chargeDir.isDirectory) {
                    chargeDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }?.let { file ->
                        try {
                            val stats = StatisticsUtil.getCachedPowerStats(
                                cacheDir = cacheDir,
                                dataDir = chargeDir,
                                name = file.name,
                                lastestFileName = latestChargeFile
                            )
                            _chargeStats.value = stats
                        } catch (_: Exception) {
                        }
                    }
                }

                // 加载放电统计
                if (dischargeDir.exists() && dischargeDir.isDirectory) {
                    dischargeDir.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }?.let { file ->
                        try {
                            val stats = StatisticsUtil.getCachedPowerStats(
                                cacheDir = cacheDir,
                                dataDir = dischargeDir,
                                name = file.name,
                                lastestFileName = latestDischargeFile
                            )
                            _dischargeStats.value = stats
                        } catch (_: Exception) {
                        }
                    }
                }
            } finally {
                _isLoadingStats.value = false
            }
        }
    }

    fun refreshStatistics(context: Context) {
        _chargeStats.value = null
        _dischargeStats.value = null
        loadStatistics(context)
    }
}
