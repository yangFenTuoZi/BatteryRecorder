package yangfentuozi.batteryrecorder.server.recorder

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import yangfentuozi.batteryrecorder.server.Server
import yangfentuozi.batteryrecorder.server.data.BatteryStatus

class BatteryManagerDataSource(
    private val context: Context
) : BatteryDataSource {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val batteryIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private var lastEnergyNwh: Long? = null
    private var lastEnergyTimestampMs: Long? = null
    private var lastErrorLogTimeMs = 0L

    override fun readSample(nowMs: Long): BatterySample? {
        logDebug("开始 BatteryManager 采样，context=${context.javaClass.name}")
        val manager = batteryManager
        if (manager == null) {
            logError(nowMs, "BatteryManager 服务不可用")
            return null
        }

        val intent = getBatteryIntent(nowMs)
        val status = resolveStatus(manager, intent)

        val levelFromIntent = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val capacity = if (levelFromIntent in 0..100) {
            levelFromIntent
        } else {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }
        logDebug("电量解析：intentLevel=$levelFromIntent, finalCapacity=$capacity")
        if (capacity !in 0..100) {
            logError(nowMs, "电量无效：$capacity")
            return null
        }

        val voltageMv = resolveVoltageMv(intent)
        val currentUa = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        logDebug("电压电流读取：voltageMv=$voltageMv, currentUa=$currentUa")
        var powerSource = "未知"
        val powerNw = when {
            voltageMv > 0 && currentUa != Int.MIN_VALUE -> {
                powerSource = "电压*CURRENT_NOW"
                voltageMv.toLong() * currentUa.toLong()
            }

            else -> {
                val averageCurrentUa = resolveAverageCurrentUa(manager)
                if (voltageMv > 0 && averageCurrentUa != Int.MIN_VALUE) {
                    powerSource = "电压*CURRENT_AVERAGE"
                    voltageMv.toLong() * averageCurrentUa.toLong()
                } else {
                    powerSource = "ENERGY_COUNTER 差分"
                    resolvePowerFromEnergyCounter(manager, nowMs)
                }
            }
        }

        if (powerNw == null) {
            logError(nowMs, "功率解析失败：voltageMv=$voltageMv, currentNowUa=$currentUa")
            return null
        }
        logDebug("采样成功：powerNw=$powerNw, source=$powerSource, status=${status.name}, capacity=$capacity")

        return BatterySample(
            timestampMs = nowMs,
            powerNw = powerNw,
            capacity = capacity,
            status = status
        )
    }

    private fun getBatteryIntent(nowMs: Long): Intent? {
        var lastError: Throwable? = null
        val candidates = resolveCandidateContexts()
        logDebug("尝试获取 ACTION_BATTERY_CHANGED，上下文数量=${candidates.size}")
        for ((index, candidateContext) in candidates.withIndex()) {
            try {
                logDebug("注册广播尝试[$index]：${candidateContext.javaClass.name}")
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    candidateContext.registerReceiver(
                        null,
                        batteryIntentFilter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    candidateContext.registerReceiver(null, batteryIntentFilter)
                }
                if (intent != null) {
                    logDebug("注册广播成功[$index]：status=${intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)}, level=${intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)}, voltage=${intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE)}")
                    return intent
                }
                logDebug("注册广播返回 null[$index]")
            } catch (e: Throwable) {
                lastError = e
                logDebug("注册广播异常[$index]：${e.javaClass.simpleName} ${e.message}")
            }
        }
        if (lastError != null) {
            logError(
                nowMs,
                "获取 ACTION_BATTERY_CHANGED 失败：${lastError.javaClass.simpleName} ${lastError.message}"
            )
        }
        return null
    }

    private fun resolveCandidateContexts(): List<Context> {
        val result = ArrayList<Context>(4)
        result.add(context)

        val activityThreadClass = runCatching { Class.forName("android.app.ActivityThread") }.getOrNull()
            ?: return result
        val activityThread = runCatching {
            val current = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
            current ?: activityThreadClass.getDeclaredMethod("systemMain").invoke(null)
        }.getOrNull()

        val appContext = runCatching {
            val application = activityThreadClass.getDeclaredMethod("currentApplication").invoke(null) as? Application
            application?.applicationContext
        }.getOrNull()
        if (appContext != null && result.none { it === appContext }) {
            result.add(appContext)
        }

        val systemContext = runCatching {
            activityThreadClass.getDeclaredMethod("getSystemContext").invoke(activityThread) as? Context
        }.getOrNull()
        if (systemContext != null && result.none { it === systemContext }) {
            result.add(systemContext)
        }

        val systemUiContext = runCatching {
            activityThreadClass.getDeclaredMethod("getSystemUiContext").invoke(activityThread) as? Context
        }.getOrNull()
        if (systemUiContext != null && result.none { it === systemUiContext }) {
            result.add(systemUiContext)
        }

        return result
    }

    private fun resolveVoltageMv(intent: Intent?): Int {
        val voltageFromIntent =
            intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (voltageFromIntent > 0) {
            return voltageFromIntent
        }

        val voltagePropertyId = BATTERY_PROPERTY_VOLTAGE_ID ?: return Int.MIN_VALUE
        val voltageFromProperty = batteryManager?.getIntProperty(voltagePropertyId) ?: Int.MIN_VALUE
        return voltageFromProperty
    }

    private fun resolveAverageCurrentUa(manager: BatteryManager): Int {
        val averagePropertyId = BATTERY_PROPERTY_CURRENT_AVERAGE_ID ?: return Int.MIN_VALUE
        return manager.getIntProperty(averagePropertyId)
    }

    private fun resolvePowerFromEnergyCounter(manager: BatteryManager, nowMs: Long): Long? {
        val energyNwh = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
        if (energyNwh == Long.MIN_VALUE) {
            logDebug("ENERGY_COUNTER 不可用")
            return null
        }

        val previousEnergyNwh = lastEnergyNwh
        val previousTimestampMs = lastEnergyTimestampMs
        lastEnergyNwh = energyNwh
        lastEnergyTimestampMs = nowMs

        if (previousEnergyNwh == null || previousTimestampMs == null) {
            logDebug("ENERGY_COUNTER 首次采样，等待下一次差分")
            return null
        }

        val deltaMs = nowMs - previousTimestampMs
        if (deltaMs <= 0) {
            logDebug("ENERGY_COUNTER 时间差无效：deltaMs=$deltaMs")
            return null
        }

        val deltaEnergyNwh = energyNwh - previousEnergyNwh
        val powerNw = (deltaEnergyNwh * NANO_WATT_PER_NANO_WATT_HOUR) / deltaMs
        logDebug("ENERGY_COUNTER 差分：current=$energyNwh, previous=$previousEnergyNwh, delta=$deltaEnergyNwh, deltaMs=$deltaMs, powerNw=$powerNw")
        return powerNw
    }

    private fun resolveStatus(manager: BatteryManager, intent: Intent?): BatteryStatus {
        val statusFromIntent =
            intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        if (statusFromIntent != BatteryManager.BATTERY_STATUS_UNKNOWN) {
            logDebug("状态来自 intent：$statusFromIntent")
            return mapStatus(statusFromIntent)
        }

        val statusPropertyId = BATTERY_PROPERTY_STATUS_ID ?: return BatteryStatus.Full
        val statusFromProperty = manager.getIntProperty(statusPropertyId)
        if (statusFromProperty == Int.MIN_VALUE) {
            logDebug("状态属性不可用，回退 Full")
            return BatteryStatus.Full
        }
        logDebug("状态来自 property：$statusFromProperty")
        return mapStatus(statusFromProperty)
    }

    private fun mapStatus(statusValue: Int): BatteryStatus {
        return when (statusValue) {
            BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.Charging
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.Discharging

            else -> BatteryStatus.Full
        }
    }

    private fun logError(nowMs: Long, message: String) {
        if (nowMs - lastErrorLogTimeMs < ERROR_LOG_INTERVAL_MS) return
        lastErrorLogTimeMs = nowMs
        Log.e(Server.TAG, message)
    }

    private fun logDebug(message: String) {
        if (!DEBUG_LOG_ENABLED) return
        Log.d(Server.TAG, "[BM] $message")
    }

    companion object {
        private const val ERROR_LOG_INTERVAL_MS = 10_000L
        private const val DEBUG_LOG_ENABLED = true
        private const val NANO_WATT_PER_NANO_WATT_HOUR = 3_600_000L
        private val BATTERY_PROPERTY_STATUS_ID = readBatteryPropertyId("BATTERY_PROPERTY_STATUS")
        private val BATTERY_PROPERTY_VOLTAGE_ID = readBatteryPropertyId("BATTERY_PROPERTY_VOLTAGE")
        private val BATTERY_PROPERTY_CURRENT_AVERAGE_ID =
            readBatteryPropertyId("BATTERY_PROPERTY_CURRENT_AVERAGE")

        private fun readBatteryPropertyId(fieldName: String): Int? {
            return try {
                BatteryManager::class.java.getField(fieldName).getInt(null)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
