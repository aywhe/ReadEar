package com.example.readear

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * 文本提取器接口
 * 用于从不同类型的文件中异步提取文本内容
 */
interface TextExtractor {
    /**
     * 异步提取文本内容
     * @param uri 文件 URI
     * @param chunkSize 每次提取的字符数（章节大小）
     * @return 返回文本块的 Flow
     */
    fun extractText(uri: Uri, chunkSize: Int = 5000): Flow<TextChunk>
}

/**
 * 文本块数据类
 * @param content 文本内容
 * @param chapterTitle 章节标题
 * @param isComplete 是否是最后一个块
 * @param chapterIndex 章节索引
 */
data class TextChunk(
    val content: String,
    val chapterTitle: String,
    val isComplete: Boolean = false,
    val chapterIndex: Int = 0
)
