package yangfentuozi.batteryrecorder.ui.compose.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.Service

class SettingsViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences

    // 双电芯设置
    private val _dualCellEnabled = MutableStateFlow(false)
    val dualCellEnabled: StateFlow<Boolean> = _dualCellEnabled.asStateFlow()

    // 电流校准值
    private val _calibrationValue = MutableStateFlow(-1)
    val calibrationValue: StateFlow<Int> = _calibrationValue.asStateFlow()

    // 采样间隔 (ms)
    private val _intervalMs = MutableStateFlow(1000L)
    val intervalMs: StateFlow<Long> = _intervalMs.asStateFlow()

    // 写入延迟 (ms)
    private val _writeLatencyMs = MutableStateFlow(30000L)
    val writeLatencyMs: StateFlow<Long> = _writeLatencyMs.asStateFlow()

    // 批次大小
    private val _batchSize = MutableStateFlow(200)
    val batchSize: StateFlow<Int> = _batchSize.asStateFlow()

    // 息屏时继续记录
    private val _recordScreenOff = MutableStateFlow(false)
    val recordScreenOff: StateFlow<Boolean> = _recordScreenOff.asStateFlow()

    /**
     * 初始化 SharedPreferences
     */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences("yangfentuozi.batteryrecorder_preferences", Context.MODE_PRIVATE)
            loadSettings()
        }
    }

    /**
     * 从 SharedPreferences 加载设置
     */
    private fun loadSettings() {
        _dualCellEnabled.value = prefs.getBoolean("dual_cell", false)
        _calibrationValue.value = prefs.getInt("current_unit_calibration", -1)
        _intervalMs.value = prefs.getLong("interval", 900)
        _writeLatencyMs.value = prefs.getLong("flush_interval", 30000)
        _batchSize.value = prefs.getInt("batch_size", 200)
        _recordScreenOff.value = prefs.getBoolean("record_screen_off", false)
    }

    /**
     * 更新双电芯设置
     */
    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean("dual_cell", enabled) }
            _dualCellEnabled.value = enabled
        }
    }

    /**
     * 更新校准值
     */
    fun setCalibrationValue(value: Int) {
        viewModelScope.launch {
            prefs.edit { putInt("current_unit_calibration", value) }
            _calibrationValue.value = value
        }
    }

    /**
     * 更新采样间隔
     */
    fun setIntervalMs(value: Long) {
        viewModelScope.launch {
            prefs.edit { putLong("interval", value) }
            _intervalMs.value = value
            Service.service?.refreshConfig()
        }
    }

    /**
     * 更新写入延迟
     */
    fun setWriteLatencyMs(value: Long) {
        viewModelScope.launch {
            prefs.edit { putLong("flush_interval", value) }
            _writeLatencyMs.value = value
            Service.service?.refreshConfig()
        }
    }

    /**
     * 更新批次大小
     */
    fun setBatchSize(value: Int) {
        viewModelScope.launch {
            prefs.edit { putInt("batch_size", value) }
            _batchSize.value = value
            Service.service?.refreshConfig()
        }
    }

    fun setRecordScreenOffEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean("record_screen_off", enabled) }
            _recordScreenOff.value = enabled
            Service.service?.refreshConfig()
        }
    }

    /**
     * 重新加载设置（从 SharedPreferences）
     */
    fun reloadSettings() {
        if (::prefs.isInitialized) {
            loadSettings()
        }
    }
}
