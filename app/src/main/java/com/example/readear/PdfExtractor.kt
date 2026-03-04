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
    
    override fun extractText(uri: Uri, chunkSize: Int): Flow<TextChunk> = flow {
        var chapterIndex = 0
        var currentContent = StringBuilder()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    var totalChars = 0
                    val numberOfPages = document.numberOfPages
                    
                    val pdfTextStripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    pdfTextStripper.startPage = 1
                    
                    for (i in 1..numberOfPages) {
                        pdfTextStripper.startPage = i
                        pdfTextStripper.endPage = i
                        val text = pdfTextStripper.getText(document) + "\n\n"
                        currentContent.append(text)
                        totalChars += text.length
                        
                        // 当达到指定字符数时，发出一个文本块
                        if (totalChars >= chunkSize) {
                            emit(
                                TextChunk(
                                    content = currentContent.toString(),
                                    chapterTitle = "章节 ${chapterIndex + 1}",
                                    isComplete = false,
                                    chapterIndex = chapterIndex + 1
                                )
                            )
                            currentContent.clear()
                            chapterIndex++
                            totalChars = 0
                        }
                    }
                    
                    // 发送剩余的内容
                    if (currentContent.isNotEmpty()) {
                        emit(
                            TextChunk(
                                content = currentContent.toString(),
                                chapterTitle = if (chapterIndex > 0) "章节 ${chapterIndex + 1}" else "第 1 章",
                                isComplete = true,
                                chapterIndex = chapterIndex + 1
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
