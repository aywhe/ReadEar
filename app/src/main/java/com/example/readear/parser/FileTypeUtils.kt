package com.example.readear.parser

import android.content.Context
import android.net.Uri
import com.example.readear.ContentActivity.Companion.EXTRA_FILE_TYPE
import com.example.readear.FileType

object FileTypeUtils {
    fun fromUri(context: Context, uri: Uri): FileType {
        val mimeType = context.contentResolver.getType(uri)
        return when (mimeType) {
            "application/pdf" -> FileType.PDF
            "text/plain" -> FileType.TXT
            else -> {
                val fileName = uri.lastPathSegment ?: ""
                val extension = fileName.substringAfterLast('.', "").lowercase()
                when (extension) {
                    "pdf" -> FileType.PDF
                    "txt" -> FileType.TXT
                    "doc", "docx" -> FileType.DOCX
                    else -> FileType.OTHER
                }
            }
        }
    }
}