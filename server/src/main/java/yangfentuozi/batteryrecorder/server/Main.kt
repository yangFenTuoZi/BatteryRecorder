package yangfentuozi.batteryrecorder.server

import android.ddm.DdmHandleAppName
import androidx.annotation.Keep
import yangfentuozi.batteryrecorder.shared.util.LoggerX
import java.io.File
import java.io.IOException

@Keep
object Main {

    @Keep
    @JvmStatic
    fun main(args: Array<String>) {
        DdmHandleAppName.setAppName("battery_recorder", 0)
        // 设置OOM保活
        setSelfOomScoreAdj()
        /* ColorOS 调度使得某些 Soc 的 1 核在息屏后被禁用，会导致某些机型息屏后无法正常记录，故不自行 taskset，全权交由系统调度
        try {
            Runtime.getRuntime().exec(new String[]{"taskset", "-ap", "1", String.valueOf(Os.getpid())});
        } catch (IOException e) {
            Log.e("Main", "Failed to set task affinity", e);
            throw new RuntimeException(e);
        }
        */
        Server()
    }

    private fun setSelfOomScoreAdj() {
        val oomScoreAdjFile = File("/proc/self/oom_score_adj")
        val oomScoreAdjValue = -1000
        try {
            oomScoreAdjFile.writeText("$oomScoreAdjValue\n")
            val actualValue: String = oomScoreAdjFile.readText().trim()
            if (oomScoreAdjValue.toString() != actualValue) {
                LoggerX.e<Main>("[启动] 设置 oom_score_adj 失败，期望 $oomScoreAdjValue，实际 $actualValue")
                return
            }
            LoggerX.i<Main>("[启动] 设置 oom_score_adj 成功，实际 $oomScoreAdjValue")
        } catch (e: IOException) {
            LoggerX.e<Main>("[启动] 设置 oom_score_adj 失败", e)
        } catch (e: RuntimeException) {
            LoggerX.e<Main>("[启动] 设置 oom_score_adj 失败", e)
        }
    }
}
