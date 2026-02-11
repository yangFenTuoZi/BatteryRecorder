package yangfentuozi.batteryrecorder.shared.sync

import android.os.ParcelFileDescriptor
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.io.path.Path

object PfdFileReceiver {

    fun receiveToDir(
        readPfd: ParcelFileDescriptor,
        outputDir: File,
        callback: ((savedFile: File, size: Long) -> Unit)? = null
    ) {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Failed to create dir: ${outputDir.absolutePath}")
        }

        val basePath = outputDir.toPath()

        ParcelFileDescriptor.AutoCloseInputStream(readPfd).use { raw ->
            BufferedInputStream(raw, SyncConstants.BUF_SIZE).use { input ->
                while (true) {
                    val code = input.read()
                    if (code < 0) throw EOFException("EOF while reading control code")

                    when (code) {
                        SyncConstants.CODE_FINISHED -> {
                            return
                        }

                        SyncConstants.CODE_FILE -> {
                        }

                        else -> {
                            throw IOException("Invalid control code: ${code.hexString}}")
                        }
                    }

                    // 读文件名、size
                    val relativizePath = Path(readUntilDelim(input).toString(Charsets.UTF_8))
                    val sizeStr = readUntilDelim(input).toString(Charsets.US_ASCII)

                    val size = sizeStr.trim().toLongOrNull()
                        ?: throw IOException("Invalid size: $sizeStr")
                    if (size < 0) throw IOException("Negative size: $size")

                    val outFile = basePath.resolve(relativizePath).toFile()

                    // 严格读 size 字节作为内容
                    BufferedOutputStream(FileOutputStream(outFile),
                        SyncConstants.BUF_SIZE
                    ).use { out ->
                        var remaining = size
                        val buf = ByteArray(SyncConstants.BUF_SIZE)
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buf.size.toLong()).toInt()
                            val r = input.read(buf, 0, toRead)
                            if (r < 0) throw EOFException("Unexpected EOF, remaining=$remaining")
                            out.write(buf, 0, r)
                            remaining -= r.toLong()
                        }
                        out.flush()
                    }

                    callback?.invoke(outFile, size)
                }
            }
        }
    }

    private fun readUntilDelim(input: InputStream): ByteArray {
        val baos = ByteArrayOutputStream(128)
        while (true) {
            val cur = input.read()
            if (cur < 0) throw EOFException("EOF before delimiter 00 00")

            if (cur == SyncConstants.CODE_DELIM) {
                return baos.toByteArray()
            }
            baos.write(cur)
        }
    }

    private val Int.hexString: String
        get() = "%02X".format(this and 0xFF)
}
