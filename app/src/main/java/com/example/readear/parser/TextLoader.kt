package com.example.readear.parser

import android.content.Context
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
 */
class TextLoader(
    private val context: Context
) {
    /**
     * 提取文本并分页
     * 
     * @param uri 文件 URI
     * @param avgCharsPerLine 每行平均字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    suspend fun extractAndPaginate(
        uri: Uri,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int,
        positionStatus: PositionStatus? = null,
        positionCallBack: (PositionStatus) -> Unit = {},
    ): Flow<TextChunk> = withContext(Dispatchers.IO) {
        val textExtractorFactory = TextExtractorFactory(context)
        val extractor = textExtractorFactory.getExtractor(uri)
        val rawTextFlow = extractor.extractTextRaw(uri,positionStatus?.position?:0)
        paginateText(rawTextFlow, avgCharsPerLine, maxLinesPerPage, positionStatus, positionCallBack)
    }
    
    /**
     * 将连续的文本流分割成适合显示的页面
     */
    private suspend fun paginateText(
        textFlow: Flow<TextExtractionResult>,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int,
        positionStatus: PositionStatus? = null,
        positionCallBack: (PositionStatus) -> Unit =  {},
    ): Flow<TextChunk> = flow {
        val paginator = TextPaginator(avgCharsPerLine, maxLinesPerPage, positionStatus){ chunk ->
            emit(chunk)
        }
        
        textFlow.collect { textExtractionResult ->
            // 原书一个position
            positionCallBack(
                PositionStatus(
                    chunkIndex = paginator.getCurrentIndex(),
                    position = textExtractionResult.position,
                    remainContent = paginator.getRemainContent()
                )
            )
            textExtractionResult. content.lines().forEach { line ->
                paginator.processLine(line)
            }
        }
        
        paginator.flushRemaining()
    }
}

data class PositionStatus (
    var chunkIndex: Int = 0,
    var position: Int = 0,
    var remainContent: String = ""
)
/**
 * 文本分页器
 * 负责将文本行分割成适合显示的页面
 */
private class TextPaginator(
    private val avgCharsPerLine: Int,
    private val maxLinesPerPage: Int,
    private val positionStatus: PositionStatus? = null,
    private val emitCallback: suspend (TextChunk) -> Unit
) {
    private var index = 0
    private var currentContent = StringBuilder()
    private var currentLines = 0

    init {
        if (positionStatus != null) {
            index = positionStatus.chunkIndex
            currentContent = StringBuilder(positionStatus.remainContent)
            currentLines = calculateLinesNeeded(positionStatus.remainContent)
        }
    }
    
    suspend fun processLine(line: String) {
        val linesNeeded = calculateLinesNeeded(line)
        
        when {
            // 超长行处理：单行内容超过一页容量
            linesNeeded > maxLinesPerPage -> handleExtraLongLine(line)
            
            // 当前行+新行会超出页面容量，且当前有内容
            currentLines + linesNeeded >= maxLinesPerPage && currentContent.isNotEmpty() -> {
                flushCurrentPage()
                addLineToNewPage(line)
            }
            
            // 新行跨页（需要拆分到两页）
            linesNeeded > 1 && currentLines + linesNeeded > maxLinesPerPage -> {
                splitLineAcrossPages(line)
            }
            
            // 正常情况：直接添加到当前页面
            else -> appendLineToCurrentPage(line)
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
            incrementIndex()
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
        
        val chunkSize = avgCharsPerLine * maxLinesPerPage
        val lineLength = line.length
        var startIndex = 0
        
        // 处理完整的页块
        while (startIndex + chunkSize <= lineLength) {
            val endIndex = startIndex + chunkSize
            val pageTextBuilder = StringBuilder(chunkSize + 1)
            pageTextBuilder.append(line, startIndex, endIndex).append('\n')
            
            emitCallback(
                TextChunk(
                    content = pageTextBuilder.toString(),
                    isCompleted = false,
                    index = getCurrentIndex()
                )
            )
            incrementIndex()
            startIndex = endIndex
        }
        
        // 处理剩余部分（如果还有未处理的字符）
        if (startIndex < lineLength) {
            val remainingText = line.substring(startIndex)
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
            val chunkSize = avgCharsPerLine * maxLinesPerPage
            val lineLength = line.length
            var startIndex = 0
            
            // 处理完整的页块
            while (startIndex + chunkSize <= lineLength) {
                val endIndex = startIndex + chunkSize
                val pageTextBuilder = StringBuilder(chunkSize + 1)
                pageTextBuilder.append(line, startIndex, endIndex).append('\n')
                
                emitCallback(
                    TextChunk(
                        content = pageTextBuilder.toString(),
                        isCompleted = false,
                        index = getCurrentIndex()
                    )
                )
                incrementIndex()
                startIndex = endIndex
            }
            
            // 处理剩余部分
            if (startIndex < lineLength) {
                val remainingText = line.substring(startIndex)
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
                val chunkSize = avgCharsPerLine * maxLinesPerPage
                val textLength = remainingText.length
                var startIndex = 0
                
                // 处理完整的页块
                while (startIndex + chunkSize <= textLength) {
                    val endIndex = startIndex + chunkSize
                    val pageTextBuilder = StringBuilder(chunkSize + 1)
                    pageTextBuilder.append(remainingText, startIndex, endIndex).append('\n')
                    
                    emitCallback(
                        TextChunk(
                            content = pageTextBuilder.toString(),
                            isCompleted = false,
                            index = getCurrentIndex()
                        )
                    )
                    incrementIndex()
                    startIndex = endIndex
                }
                
                // 处理剩余部分
                if (startIndex < textLength) {
                    val finalText = remainingText.substring(startIndex)
                    appendLineToCurrentPage(finalText)
                }
            } else {
                appendLineToCurrentPage(remainingText)
            }
        }
    }
    
    private fun appendLineToCurrentPage(line: String) {
        currentContent.append(line).append('\n')
        currentLines += calculateLinesNeeded(line)
    }
    
    fun getCurrentIndex(): Int {
        //return if (index > 0) index + 1 else 0
        return index
    }
    
    private fun incrementIndex() {
        index++
    }

    fun getRemainContent(): String {
        return currentContent.toString()
    }
}
