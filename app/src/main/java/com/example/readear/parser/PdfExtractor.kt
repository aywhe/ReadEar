package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * PDF 文件文本提取器
 */
class PdfExtractor(private val context: Context) : TextExtractor {
    
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw Exception("无法打开文件输入流")
            }
            
            val document = PDDocument.load(inputStream)
            try {
                val numberOfPages = document.numberOfPages
                
                for (i in 1..numberOfPages) {
                    val pdfTextStripper = PDFTextStripper()
                    pdfTextStripper.startPage = i
                    pdfTextStripper.endPage = i
                    val text = pdfTextStripper.getText(document)
                    
                    text.lines().forEach { line ->
                        if (line.isNotEmpty()) {
                            emit(line)
                        }
                    }
                }
            } finally {
                document.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            inputStream?.close()
        }
    }.flowOn(Dispatchers.IO)
}
