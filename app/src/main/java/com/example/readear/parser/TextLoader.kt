package com.example.readear.parser

import android.net.Uri
import com.example.readear.FileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlin.math.ceil

/**
 * 文本加载器
 * 
 * 职责：
 * - 从文件中提取原始文本
 * - 将文本流分页为适合显示的文本块
 * 
 * @param textExtractorFactory 文本提取器工厂
 */
class TextLoader(
    private val textExtractorFactory: TextExtractorFactory
) {
    
    /**
     * 提取文本并分页
     * 
     * @param uri 文件 URI
     * @param fileType 文件类型
     * @param avgCharsPerLine 每行平均字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    suspend fun extractAndPaginate(
        uri: Uri,
        fileType: FileType,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = withContext(Dispatchers.IO) {
        val extractor = textExtractorFactory.getExtractor(fileType)
        val rawTextFlow = extractor.extractTextRaw(uri)
        paginateText(rawTextFlow, avgCharsPerLine, maxLinesPerPage)
    }
    
    /**
     * 将连续的文本流分割成适合显示的页面
     */
    private suspend fun paginateText(
        textFlow: Flow<String>,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        val paginator = TextPaginator(avgCharsPerLine, maxLinesPerPage) { chunk ->
            emit(chunk)
        }
        
        textFlow.collect { text ->
            text.lines().forEach { line ->
                paginator.processLine(line)
            }
        }
        
        paginator.flushRemaining()
    }
}

/**
 * 文本分页器
 * 负责将文本行分割成适合显示的页面
 */
private class TextPaginator(
    private val avgCharsPerLine: Int,
    private val maxLinesPerPage: Int,
    private val emitCallback: suspend (TextChunk) -> Unit
) {
    private var index = 0
    private var currentContent = StringBuilder()
    private var currentLines = 0
    
    suspend fun processLine(line: String) {
        val linesNeeded = calculateLinesNeeded(line)
        
        if (linesNeeded > maxLinesPerPage) {
            handleExtraLongLine(line)
        } else if (currentLines + linesNeeded >= maxLinesPerPage && currentContent.isNotEmpty()) {
            flushCurrentPage()
            addLineToNewPage(line)
        } else {
            if (linesNeeded > 1 && currentLines + linesNeeded > maxLinesPerPage) {
                splitLineAcrossPages(line)
            } else {
                appendLineToCurrentPage(line)
            }
        }
    }
    
    suspend fun flushRemaining() {
        if (currentContent.isNotEmpty()) {
            emitCallback(
                TextChunk(
                    content = currentContent.toString(),
                    isCompleted = true,
                    index = getCurrentIndex()
                )
            )
        }
    }
    
    private fun calculateLinesNeeded(line: String): Int {
        return if (line.isEmpty()) {
            1
        } else {
            ceil(line.length.toDouble() / avgCharsPerLine).toInt()
        }
    }
    
    private suspend fun handleExtraLongLine(line: String) {
        if (currentContent.isNotEmpty()) {
            flushCurrentPage()
        }
        
        var remainingText = line
        while (remainingText.length > avgCharsPerLine * maxLinesPerPage) {
            val pageText = remainingText.substring(0, avgCharsPerLine * maxLinesPerPage)
            emitCallback(
                TextChunk(
                    content = pageText + "\n",
                    isCompleted = false,
                    index = getCurrentIndex()
                )
            )
            incrementIndex()
            remainingText = remainingText.substring(avgCharsPerLine * maxLinesPerPage)
        }
        
        if (remainingText.isNotEmpty()) {
            appendLineToCurrentPage(remainingText)
        }
    }
    
    private suspend fun flushCurrentPage() {
        if (currentContent.isNotEmpty()) {
            emitCallback(
                TextChunk(
                    content = currentContent.toString(),
                    isCompleted = false,
                    index = getCurrentIndex()
                )
            )
            incrementIndex()
            currentContent.clear()
            currentLines = 0
        }
    }
    
    private suspend fun addLineToNewPage(line: String) {
        val linesNeeded = calculateLinesNeeded(line)
        
        if (linesNeeded <= maxLinesPerPage) {
            appendLineToCurrentPage(line)
        } else {
            var remainingText = line
            while (remainingText.length > avgCharsPerLine * maxLinesPerPage) {
                val pageText = remainingText.substring(0, avgCharsPerLine * maxLinesPerPage)
                emitCallback(
                    TextChunk(
                        content = pageText + "\n",
                        isCompleted = false,
                        index = getCurrentIndex()
                    )
                )
                incrementIndex()
                remainingText = remainingText.substring(avgCharsPerLine * maxLinesPerPage)
            }
            if (remainingText.isNotEmpty()) {
                appendLineToCurrentPage(remainingText)
            }
        }
    }
    
    private suspend fun splitLineAcrossPages(line: String) {
        val charsFitInCurrentPage = (maxLinesPerPage - currentLines) * avgCharsPerLine
        val firstPart = line.take(charsFitInCurrentPage)
        
        appendLineToCurrentPage(firstPart)
        flushCurrentPage()
        
        val remainingText = line.drop(charsFitInCurrentPage)
        if (remainingText.isNotEmpty()) {
            val remainingLinesNeeded = calculateLinesNeeded(remainingText)
            if (remainingLinesNeeded > maxLinesPerPage) {
                var textToSplit = remainingText
                while (textToSplit.length > avgCharsPerLine * maxLinesPerPage) {
                    val pageText = textToSplit.substring(0, avgCharsPerLine * maxLinesPerPage)
                    emitCallback(
                        TextChunk(
                            content = pageText + "\n",
                            isCompleted = false,
                            index = getCurrentIndex()
                        )
                    )
                    incrementIndex()
                    textToSplit = textToSplit.substring(avgCharsPerLine * maxLinesPerPage)
                }
                if (textToSplit.isNotEmpty()) {
                    appendLineToCurrentPage(textToSplit)
                }
            } else {
                appendLineToCurrentPage(remainingText)
            }
        }
    }
    
    private fun appendLineToCurrentPage(line: String) {
        currentContent.append(line).append("\n")
        currentLines += calculateLinesNeeded(line)
    }
    
    private fun getCurrentIndex(): Int {
        //return if (index > 0) index + 1 else 0
        return index
    }
    
    private fun incrementIndex() {
        index++
    }
}
