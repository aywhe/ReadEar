package com.example.readear.parser

import android.net.Uri
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.flow.Flow

/**
 * 文本提取器接口
 * 用于从不同类型的文件中异步提取文本内容
 */
interface TextExtractor {
    /**
     * 异步提取文本内容（原始文本流）
     * @param uri 文件 URI
     * @return 返回原始文本的 Flow（按行或按段）
     */
    fun extractTextRaw(uri: Uri, startPosition: Int = 0): Flow<TextExtractionResult>
}

/**
 * 文本块数据类
 * @param content 文本内容
 * @param isCompleted 是否是最后一个块
 * @param index 章节索引
 */
data class TextChunk(
    val content: String,
    val isCompleted: Boolean = false,
    val index: Int = 0
)

data class TextExtractionResult(
    val content: String,
    val isCompleted: Boolean = false,
    val position: Int
)
