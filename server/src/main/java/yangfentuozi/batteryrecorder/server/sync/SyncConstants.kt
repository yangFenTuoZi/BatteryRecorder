package yangfentuozi.batteryrecorder.server.sync

object SyncConstants {
    const val BUF_SIZE = 64 * 1024

    // 两字节控制码
    const val CODE_FILE = 0x13
    const val CODE_FINISHED = 0x78

    // 分隔
    const val CODE_DELIM = 0x00
}
