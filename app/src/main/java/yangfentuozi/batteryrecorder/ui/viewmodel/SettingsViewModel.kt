package yangfentuozi.batteryrecorder.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yangfentuozi.batteryrecorder.config.Config
import yangfentuozi.batteryrecorder.config.Constants
import yangfentuozi.batteryrecorder.ipc.Service

class SettingsViewModel : ViewModel() {
    private lateinit var prefs: SharedPreferences

    // 双电芯设置
    private val _dualCellEnabled = MutableStateFlow(Constants.DEF_DUAL_CELL_ENABLED)
    val dualCellEnabled: StateFlow<Boolean> = _dualCellEnabled.asStateFlow()

    // 放电电流显示为正值
    private val _dischargeDisplayPositive =
        MutableStateFlow(Constants.DEF_DISCHARGE_DISPLAY_POSITIVE)
    val dischargeDisplayPositive: StateFlow<Boolean> = _dischargeDisplayPositive.asStateFlow()

    // 校准值
    private val _calibrationValue = MutableStateFlow(Constants.DEF_CALIBRATION_VALUE)
    val calibrationValue: StateFlow<Int> = _calibrationValue.asStateFlow()

    // 采样间隔 (ms)
    private val _recordIntervalMs = MutableStateFlow(Constants.DEF_RECORD_INTERVAL_MS)
    val recordIntervalMs: StateFlow<Long> = _recordIntervalMs.asStateFlow()

    // 写入延迟 (ms)
    private val _writeLatencyMs = MutableStateFlow(Constants.DEF_WRITE_LATENCY_MS)
    val writeLatencyMs: StateFlow<Long> = _writeLatencyMs.asStateFlow()

    // 批次大小
    private val _batchSize = MutableStateFlow(Constants.DEF_BATCH_SIZE)
    val batchSize: StateFlow<Int> = _batchSize.asStateFlow()

    // 息屏时继续记录
    private val _screenOffRecord = MutableStateFlow(Constants.DEF_SCREEN_OFF_RECORD_ENABLED)
    val screenOffRecord: StateFlow<Boolean> = _screenOffRecord.asStateFlow()

    // 分段时间 (分钟)
    private val _segmentDurationMin = MutableStateFlow(Constants.DEF_SEGMENT_DURATION_MIN)
    val segmentDurationMin: StateFlow<Long> = _segmentDurationMin.asStateFlow()

    private lateinit var serverConfig: Config

    /**
     * 初始化 SharedPreferences
     */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(
                "yangfentuozi.batteryrecorder_preferences",
                Context.MODE_PRIVATE
            )
            loadSettings()
        }
    }

    /**
     * 从 SharedPreferences 加载设置
     */
    private fun loadSettings() {
        _dualCellEnabled.value =
            prefs.getBoolean(
                Constants.KEY_DUAL_CELL_ENABLED,
                Constants.DEF_DUAL_CELL_ENABLED
            )

        _dischargeDisplayPositive.value =
            prefs.getBoolean(
                Constants.KEY_DISCHARGE_DISPLAY_POSITIVE,
                Constants.DEF_DISCHARGE_DISPLAY_POSITIVE
            )

        _calibrationValue.value =
            prefs.getInt(
                Constants.KEY_CALIBRATION_VALUE,
                Constants.DEF_CALIBRATION_VALUE
            ).coerceIn(
                Constants.MIN_CALIBRATION_VALUE,
                Constants.MAX_CALIBRATION_VALUE
            )

        _recordIntervalMs.value =
            prefs.getLong(
                Constants.KEY_RECORD_INTERVAL_MS,
                Constants.DEF_RECORD_INTERVAL_MS
            ).coerceIn(
                Constants.MIN_RECORD_INTERVAL_MS,
                Constants.MAX_RECORD_INTERVAL_MS
            )

        _writeLatencyMs.value =
            prefs.getLong(
                Constants.KEY_WRITE_LATENCY_MS,
                Constants.DEF_WRITE_LATENCY_MS
            ).coerceIn(
                Constants.MIN_WRITE_LATENCY_MS,
                Constants.MAX_WRITE_LATENCY_MS
            )

        _batchSize.value =
            prefs.getInt(
                Constants.KEY_BATCH_SIZE,
                Constants.DEF_BATCH_SIZE
            ).coerceIn(
                Constants.MIN_BATCH_SIZE,
                Constants.MAX_BATCH_SIZE
            )

        _screenOffRecord.value =
            prefs.getBoolean(
                Constants.KEY_SCREEN_OFF_RECORD_ENABLED,
                Constants.DEF_SCREEN_OFF_RECORD_ENABLED
            )

        _segmentDurationMin.value =
            prefs.getLong(
                Constants.KEY_SEGMENT_DURATION_MIN,
                Constants.DEF_SEGMENT_DURATION_MIN
            ).coerceIn(
                Constants.MIN_SEGMENT_DURATION_MIN,
                Constants.MAX_SEGMENT_DURATION_MIN
            )
        serverConfig = Config(
            recordIntervalMs = _recordIntervalMs.value,
            writeLatencyMs = _writeLatencyMs.value,
            batchSize = _batchSize.value,
            screenOffRecordEnabled = _screenOffRecord.value,
            segmentDurationMin = _segmentDurationMin.value
        )
    }

    /**
     * 更新双电芯设置
     */
    fun setDualCellEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(Constants.KEY_DUAL_CELL_ENABLED, enabled) }
            _dualCellEnabled.value = enabled
        }
    }

    /**
     * 更新校准值
     */
    fun setDischargeDisplayPositiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(Constants.KEY_DISCHARGE_DISPLAY_POSITIVE, enabled) }
            _dischargeDisplayPositive.value = enabled
        }
    }

    fun setCalibrationValue(value: Int) {
        val finalValue =
            value.coerceIn(Constants.MIN_CALIBRATION_VALUE, Constants.MAX_CALIBRATION_VALUE)
        viewModelScope.launch {
            prefs.edit { putInt(Constants.KEY_CALIBRATION_VALUE, finalValue) }
            _calibrationValue.value = finalValue
        }
    }

    /**
     * 更新采样间隔
     */
    fun setRecordIntervalMs(value: Long) {
        val finalValue =
            value.coerceIn(Constants.MIN_RECORD_INTERVAL_MS, Constants.MAX_RECORD_INTERVAL_MS)
        viewModelScope.launch {
            prefs.edit { putLong(Constants.KEY_RECORD_INTERVAL_MS, finalValue) }
            _recordIntervalMs.value = finalValue
            serverConfig = serverConfig.copy(recordIntervalMs = finalValue)
            Service.service?.updateConfig(serverConfig)
        }
    }

    /**
     * 更新写入延迟
     */
    fun setWriteLatencyMs(value: Long) {
        val finalValue =
            value.coerceIn(Constants.MIN_WRITE_LATENCY_MS, Constants.MAX_WRITE_LATENCY_MS)
        viewModelScope.launch {
            prefs.edit { putLong(Constants.KEY_WRITE_LATENCY_MS, finalValue) }
            _writeLatencyMs.value = finalValue
            serverConfig = serverConfig.copy(writeLatencyMs = finalValue)
            Service.service?.updateConfig(serverConfig)
        }
    }

    /**
     * 更新批次大小
     */
    fun setBatchSize(value: Int) {
        val finalValue = value.coerceIn(Constants.MIN_BATCH_SIZE, Constants.MAX_BATCH_SIZE)
        viewModelScope.launch {
            prefs.edit { putInt(Constants.KEY_BATCH_SIZE, finalValue) }
            _batchSize.value = finalValue
            serverConfig = serverConfig.copy(batchSize = finalValue)
            Service.service?.updateConfig(serverConfig)
        }
    }

    fun setScreenOffRecordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit { putBoolean(Constants.KEY_SCREEN_OFF_RECORD_ENABLED, enabled) }
            _screenOffRecord.value = enabled
            serverConfig = serverConfig.copy(screenOffRecordEnabled = enabled)
            Service.service?.updateConfig(serverConfig)
        }
    }

    fun setSegmentDurationMin(value: Long) {
        val finalValue =
            value.coerceIn(Constants.MIN_SEGMENT_DURATION_MIN, Constants.MAX_SEGMENT_DURATION_MIN)
        viewModelScope.launch {
            prefs.edit { putLong(Constants.KEY_SEGMENT_DURATION_MIN, finalValue) }
            _segmentDurationMin.value = finalValue
            serverConfig = serverConfig.copy(segmentDurationMin = finalValue)
            Service.service?.updateConfig(serverConfig)
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
