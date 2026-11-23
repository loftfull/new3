package com.knowledgehub.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class AttachmentStorage(context: Context) {
    private val resolver = context.contentResolver
    private val directory = File(context.filesDir, "attachments")

    fun find(key: String): File? = key.takeIf { FILE_KEY.matches(it) }
        ?.let { File(directory, it) }
        ?.takeIf(File::isFile)

    suspend fun copy(input: CaptureInput.File): Material {
        require(input.uri.scheme == ContentResolver.SCHEME_CONTENT) { "Unsupported attachment URI" }
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create attachment directory")
        }
        val name = displayName(input.uri) ?: input.uri.lastPathSegment ?: "attachment"
        val mime = optionalMetadata { resolver.getType(input.uri) } ?: input.mimeType
        val key = UUID.randomUUID().toString()
        val temporary = File(directory, "$key.partial")
        val saved = File(directory, key)
        try {
            val source = resolver.openInputStream(input.uri) ?: throw IOException("Attachment cannot be opened")
            source.use { stream ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(saved)) throw IOException("Cannot finalize attachment")
        } finally {
            temporary.delete()
        }
        return Material(
            kind = "FILE",
            title = displayTitle(name).ifEmpty { "attachment" },
            fileName = name,
            filePath = key,
            mimeType = mime,
        )
    }

    private fun displayName(uri: Uri): String? = optionalMetadata {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }?.takeIf(String::isNotBlank)
    }

    private fun <T> optionalMetadata(read: () -> T?): T? = try {
        read()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: UnsupportedOperationException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private companion object {
        val FILE_KEY = Regex("[a-f0-9-]{36}")
    }
}
