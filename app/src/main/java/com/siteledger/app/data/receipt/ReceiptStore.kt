package com.siteledger.app.data.receipt

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Receipt files live in app-private storage until uploaded. Local copies are retained
 * after upload (explicit V1 retention policy) and removed when a deleted expense has been
 * acknowledged by the cloud.
 */
class ReceiptStore(private val context: Context) {

    private val captureDir: File
        get() = File(context.cacheDir, "receipt_captures").apply { mkdirs() }

    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun newCaptureFile(): File = File(captureDir, "capture_${UUID.randomUUID()}.jpg")

    /** Moves a freshly captured/picked file into permanent app storage for the expense. */
    fun persistReceipt(sourcePath: String, expenseId: String): String? {
        val source = File(sourcePath)
        if (!source.exists()) return null
        val target = File(receiptsDir, "$expenseId.jpg")
        source.copyTo(target, overwrite = true)
        if (source.parentFile == captureDir) source.delete()
        return target.absolutePath
    }

    fun readBytes(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists()) file.readBytes() else null
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    /** Removes every stored receipt; only used by the explicit "clear local data" flow. */
    fun clearAll() {
        receiptsDir.listFiles()?.forEach { it.delete() }
        captureDir.listFiles()?.forEach { it.delete() }
    }
}
