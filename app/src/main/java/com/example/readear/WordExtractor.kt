package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Word 文件文本提取器
 * 注意：目前仅支持简单的文本读取，完整的 Word 格式支持需要 Apache POI 库
 */
class WordExtractor(private val context: Context) : TextExtractor {
    
    override fun extractText(uri: Uri, chunkSize: Int): Flow<TextChunk> = flow {
        var chapterIndex = 0
        var currentContent = StringBuilder()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    var totalChars = 0
                    
                    while (reader.readLine().also { line = it } != null) {
                        currentContent.append(line).append("\n")
                        totalChars += line!!.length + 1
                        
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
