package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.tom_roush.pdfbox.pdmodel.PDDocument

/**
 * PDF 文件文本提取器
 */
class PdfExtractor(private val context: Context) : TextExtractor {
    
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val numberOfPages = document.numberOfPages
                    
                    val pdfTextStripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    pdfTextStripper.startPage = 1
                    
                    for (i in 1..numberOfPages) {
                        pdfTextStripper.startPage = i
                        pdfTextStripper.endPage = i
                        val text = pdfTextStripper.getText(document)
                        // 按段落发出文本
                        text.lines().forEach { line ->
                            if (line.isNotEmpty()) {
                                emit(line)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
