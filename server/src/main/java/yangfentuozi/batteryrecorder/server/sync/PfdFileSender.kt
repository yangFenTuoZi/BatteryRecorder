package yangfentuozi.batteryrecorder.server.sync

import android.os.ParcelFileDescriptor
import yangfentuozi.batteryrecorder.server.sync.SyncConstants.BUF_SIZE
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Path

object PfdFileSender {

    fun sendFile(
        writePfd: ParcelFileDescriptor,
        file: File,
        callback: ((File) -> Unit)? = null
    ) {
        val basePath = file.toPath()
        ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { raw ->
            BufferedOutputStream(raw, BUF_SIZE).use { out ->
                sendFileInner(out, file, basePath, callback)
                // 结束码
                out.write(SyncConstants.CODE_FINISHED)
                out.flush()
            }
        }
    }

    private fun sendFileInner(out: OutputStream, file: File, basePath: Path, callback: ((File) -> Unit)?) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                sendFileInner(out, it, basePath, callback)
            }
        } else {
            val size = file.length()
            if (size < 0) throw IOException("Invalid file size: $size")

            // 文件识别码
            out.write(SyncConstants.CODE_FILE)

            // 基于 basePath 的文件路径 (UTF-8)
            out.write(basePath.relativize(file.toPath()).toString().toByteArray(Charsets.UTF_8))

            // 00位
            out.write(SyncConstants.CODE_DELIM)

            // 文件大小 (ASCII 十进制)
            out.write(size.toString().toByteArray(Charsets.US_ASCII))

            // 00位
            out.write(SyncConstants.CODE_DELIM)

            // 文件内容：size: Long 字节
            BufferedInputStream(FileInputStream(file), BUF_SIZE).use { fis ->
                val buf = ByteArray(BUF_SIZE)
                var remaining = size
                while (remaining > 0) {
                    val toRead = minOf(remaining, buf.size.toLong()).toInt()
                    val n = fis.read(buf, 0, toRead)
                    if (n < 0) throw EOFException("Unexpected EOF reading file: ${file.absolutePath}")
                    out.write(buf, 0, n)
                    remaining -= n.toLong()
                }
            }
            out.flush()

            callback?.invoke(file)
        }
    }
}
