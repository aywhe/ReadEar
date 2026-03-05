package com.example.readear

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文本分页处理器
 * 负责将连续的文本流按照屏幕显示能力分割成页面
 */
class TextPaginationProcessor {
    
    /**
     * 将连续的文本流分割成适合显示的页面
     * @param textFlow 原始文本流
     * @param avgCharsPerLine 每行平均字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    fun paginateText(
        textFlow: Flow<String>,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        var chapterIndex = 0
        var currentContent = StringBuilder()
        var currentLines = 0
        
        textFlow.collect { text ->
            // 按行处理
            text.lines().forEach { line ->
                val lineContent = line + "\n"  // 准备带换行符的内容
                
                // 计算这一行需要多少行来显示
                val linesNeeded = if (line.isEmpty()) {
                    1  // 空行也占一行
                } else {
                    kotlin.math.ceil(line.length.toDouble() / avgCharsPerLine).toInt()
                }
                
                // 情况 1：这一行本身就超过一页
                if (linesNeeded > maxLinesPerPage) {
                    // 如果当前页有内容，先发送
                    if (currentContent.isNotEmpty()) {
                        emit(
                            TextChunk(
                                content = currentContent.toString(),
                                chapterTitle = (chapterIndex + 1).toString(),
                                isComplete = false,
                                chapterIndex = chapterIndex + 1
                            )
                        )
                        currentContent.clear()
                        chapterIndex++
                        currentLines = 0
                    }
                                    
                    // 将超长文本分割成多页（除了最后一页）
                    var remainingText = line
                    while (remainingText.length > avgCharsPerLine * maxLinesPerPage) {
                        val charsForFullPage = maxLinesPerPage * avgCharsPerLine
                        val pageText = remainingText.substring(0, charsForFullPage)
                                        
                        emit(
                            TextChunk(
                                content = pageText + "\n",
                                chapterTitle = (chapterIndex + 1).toString(),
                                isComplete = false,
                                chapterIndex = chapterIndex + 1
                            )
                        )
                        chapterIndex++
                        remainingText = remainingText.substring(charsForFullPage)
                    }
                                    
                    // 剩余不足一页的部分，保留到 currentContent 中，让下一个 line 补充
                    if (remainingText.isNotEmpty()) {
                        currentContent.append(remainingText).append("\n")
                        currentLines = kotlin.math.ceil(remainingText.length.toDouble() / avgCharsPerLine).toInt()
                    }
                } 
                // 情况 2：这一行加上之前的内容会达到或超过一页
                else if (currentLines + linesNeeded >= maxLinesPerPage && currentContent.isNotEmpty()) {
                    // 先发送当前页（已有的内容）
                    emit(
                        TextChunk(
                            content = currentContent.toString(),
                            chapterTitle = (chapterIndex + 1).toString(),
                            isComplete = false,
                            chapterIndex = chapterIndex + 1
                        )
                    )
                    currentContent.clear()
                    chapterIndex++
                    currentLines = 0
                                    
                    // 现在处理这一行，检查是否能完整放入新的一页
                    if (linesNeeded <= maxLinesPerPage) {
                        // 可以完整放入，添加到新的一页
                        currentContent.append(lineContent)
                        currentLines += linesNeeded
                    } else {
                        // 这一行本身就超过一页，需要分割
                        var remainingText = line
                        while (remainingText.length > avgCharsPerLine * maxLinesPerPage) {
                            val charsForFullPage = maxLinesPerPage * avgCharsPerLine
                            val pageText = remainingText.substring(0, charsForFullPage)
                                            
                            emit(
                                TextChunk(
                                    content = pageText + "\n",
                                    chapterTitle = (chapterIndex + 1).toString(),
                                    isComplete = false,
                                    chapterIndex = chapterIndex + 1
                                )
                            )
                            chapterIndex++
                            remainingText = remainingText.substring(charsForFullPage)
                        }
                                        
                        // 剩余不足一页的部分，保留到 currentContent 中
                        if (remainingText.isNotEmpty()) {
                            currentContent.append(remainingText).append("\n")
                            currentLines = kotlin.math.ceil(remainingText.length.toDouble() / avgCharsPerLine).toInt()
                        }
                    }
                }
                // 情况 3：这一行可以放入当前页
                else {
                    // 即使可以放入，也要检查这一行是否需要分割成多页
                    if (linesNeeded > 1 && currentLines + linesNeeded > maxLinesPerPage) {
                        // 这一行需要跨页：先添加能放入当前页的部分
                        val charsFitInCurrentPage = (maxLinesPerPage - currentLines) * avgCharsPerLine
                        val firstPart = line.take(charsFitInCurrentPage)
                                                        
                        currentContent.append(firstPart).append("\n")
                        currentLines += kotlin.math.ceil(firstPart.length.toDouble() / avgCharsPerLine).toInt()
                                                        
                        // 发送当前页
                        emit(
                            TextChunk(
                                content = currentContent.toString(),
                                chapterTitle = (chapterIndex + 1).toString(),
                                isComplete = false,
                                chapterIndex = chapterIndex + 1
                            )
                        )
                        currentContent.clear()
                        chapterIndex++
                        currentLines = 0
                                                        
                        // 处理剩余部分
                        val remainingText = line.drop(charsFitInCurrentPage)
                        if (remainingText.isNotEmpty()) {
                            // 检查剩余部分是否超过一页
                            val remainingLinesNeeded = kotlin.math.ceil(remainingText.length.toDouble() / avgCharsPerLine).toInt()
                            if (remainingLinesNeeded > maxLinesPerPage) {
                                // 剩余部分也超过一页，继续分割
                                var textToSplit = remainingText
                                while (textToSplit.length > avgCharsPerLine * maxLinesPerPage) {
                                    val charsForFullPage = maxLinesPerPage * avgCharsPerLine
                                    val pageText = textToSplit.substring(0, charsForFullPage)
                                                                    
                                    emit(
                                        TextChunk(
                                            content = pageText + "\n",
                                            chapterTitle = (chapterIndex + 1).toString(),
                                            isComplete = false,
                                            chapterIndex = chapterIndex + 1
                                        )
                                    )
                                    chapterIndex++
                                    textToSplit = textToSplit.substring(charsForFullPage)
                                }
                                                                
                                // 最后剩余不足一页的部分
                                if (textToSplit.isNotEmpty()) {
                                    currentContent.append(textToSplit).append("\n")
                                    currentLines = kotlin.math.ceil(textToSplit.length.toDouble() / avgCharsPerLine).toInt()
                                }
                            } else {
                                // 剩余部分不超过一页，直接添加
                                currentContent.append(remainingText).append("\n")
                                currentLines = remainingLinesNeeded
                            }
                        }
                    } else {
                        // 简单情况：整行都可以放入当前页
                        currentContent.append(lineContent)
                        currentLines += linesNeeded
                    }
                }
            }
        }
        
        // 发送剩余的内容（最后一页）
        if (currentContent.isNotEmpty()) {
            emit(
                TextChunk(
                    content = currentContent.toString(),
                    chapterTitle = if (chapterIndex > 0) (chapterIndex + 1).toString() else "1",
                    isComplete = true,
                    chapterIndex = chapterIndex + 1
                )
            )
        }
    }
}
