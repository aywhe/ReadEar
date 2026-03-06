package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 文本管理器
 * 
 * 职责：
 * - 管理书籍页面内容的加载和缓存
 * - 优先从 PagesCache 获取
 * - 提供异步加载接口，避免 UI 卡顿
 */
class TextManager(private val context: Context) {
    
    private val pagesCacheManager: BooksCache = BooksCache
    
    /**
     * 检查指定 URI 的书籍是否有缓存
     */
    fun hasBook(uri: String): Boolean {
        return pagesCacheManager.hasCache(uri)
    }
    
    /**
     * 获取缓存的页面内容
     */
    private fun getAllPages(uri: String): Map<Int, TextChunk>?{
        return pagesCacheManager.getCache(uri)?.getAllPages()
    }
    
    /**
     * 获取缓存的页面数量
     */
    suspend fun getPagesCount(uri: String): Int? {
        return withContext(Dispatchers.Default) {
            pagesCacheManager.getPagesCount(uri)
        }
    }
    
    /**
     * 获取上次阅读的页码（仅从内存缓存）
     */
    suspend fun getLastReadPageNumber(uri: String): Int? {
        return withContext(Dispatchers.Default) {
            pagesCacheManager.getCache(uri)?.getLastReadingPageNumber()
        }
    }

    /**
     * 加载单个页面内容（仅从内存缓存）
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回 null
     */
    suspend fun getPage(uri: String, pageNumber: Int): TextChunk? {
        return withContext(Dispatchers.Default) {
            pagesCacheManager.getCache(uri)?.getPage(pageNumber)
        }
    }

    /**
     * 预加载指定范围的页面到内存缓存
     * @param uri 文件 URI
     * @param startPage 起始页码
     * @param endPage 结束页码
     * @return 返回成功从数据库加载到内存的页面数量
     */
    suspend fun preloadPagesRange(uri: String, startPage: Int, endPage: Int): Int {
        return withContext(Dispatchers.Default) {
            var loadedCount = 0
            val cacheManager = CacheManager(context)  // ← 移到循环外
            
            for (pageNumber in startPage..endPage) {
                // 检查是否已在内存缓存中
                if (pagesCacheManager.getCache(uri)?.getPage(pageNumber) == null) {
                    // 不在内存缓存中，从数据库加载
                    val page = cacheManager.getPage(uri, pageNumber)
                    
                    if (page != null) {
                        val textChunk = TextChunk(page.content, false, page.index)
                        // 加载到内存缓存
                        if(!pagesCacheManager.hasCache(uri)) {
                            // 如果内存缓存不存在，先创建一个新的缓存对象
                            pagesCacheManager.setCache(uri, PagesCache(uri))
                        }
                        pagesCacheManager.getCache(uri)?.addPage(textChunk)
                        loadedCount++
                    }
                }
            }
            
            loadedCount
        }
    }
    
    /**
     * 同步加载单个页面内容（用于 Compose UI）
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回空文本块
     */
    fun getPageSync(uri: String, pageNumber: Int): TextChunk? {
        return pagesCacheManager.getCache(uri)?.getPage(pageNumber)
    }

    
    /**
     * 保存阅读进度（仅内存）
     */
    suspend fun saveReadingProgress(uri: String, currentPage: Int) {
        withContext(Dispatchers.Default) {
            pagesCacheManager.getCache(uri)?.setLastReadingPageNumber(currentPage)
        }
        // 保存到缓存
        val cacheManager = CacheManager(context)
        cacheManager.saveReadingProgress(uri, currentPage)
    }
    
    /**
     * 清除指定书籍的缓存
     */
    suspend fun delBook(uri: String) {
        pagesCacheManager.clearCache(uri)
        val cacheManager = CacheManager(context)
        cacheManager.deleteBook(uri)
    }
    
    /**
     * 清除所有缓存
     */
    suspend fun clearAllCache() {
        pagesCacheManager.clearAllCache()
    }
    
    /**
     * 加载结果密封类
     */
    sealed class LoadResult {
        data class Success(val pages: Map<Int, TextChunk>) : LoadResult()
        object NotExist : LoadResult()
        data class Error(val message: String) : LoadResult()
    }

    /**
     * 异步提取原始文本并分页
     * 
     * 从指定文件中提取文本内容，自动根据布局参数进行分页处理。
     * 返回一个 Flow，可以异步收集每个页面内容。
     * 
     * @param uri 文件 URI
     * @param fileType 文件类型（TXT、PDF、EPUB 等）
     * @param avgCharsPerLine 每行平均字符数（用于分页计算）
     * @param maxLinesPerPage 每页最大行数（用于分页计算）
     * @return Flow<TextChunk> 文本块流，每个 TextChunk 代表一页内容
     * 
     * @see TextExtractor.extractTextRaw 底层文本提取
     * @see paginateText 文本分页处理
     */
    suspend fun extractRawText(uri: Uri, fileType: FileType, avgCharsPerLine: Int, maxLinesPerPage: Int): Flow<TextChunk> {
        // 没有缓存，从源文件提取并保存缓存
        val extractor = TextExtractorFactory.getExtractor(context, fileType)
        val rawTextFlow = extractor.extractTextRaw(uri)
        return paginateText(rawTextFlow, avgCharsPerLine, maxLinesPerPage)
    }
    /**
     * 将连续的文本流分割成适合显示的页面
     * @param textFlow 原始文本流
     * @param avgCharsPerLine 每行平均字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    suspend fun paginateText(
        textFlow: Flow<String>,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        var index = 0
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
                                isComplete = false,
                                index = index + 1
                            )
                        )
                        currentContent.clear()
                        index++
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
                                isComplete = false,
                                index = index + 1
                            )
                        )
                        index++
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
                            isComplete = false,
                            index = index + 1
                        )
                    )
                    currentContent.clear()
                    index++
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
                                    isComplete = false,
                                    index = index + 1
                                )
                            )
                            index++
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
                                isComplete = false,
                                index = index + 1
                            )
                        )
                        currentContent.clear()
                        index++
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
                                            isComplete = false,
                                            index = index + 1
                                        )
                                    )
                                    index++
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
                    isComplete = true,
                    index = if (index > 0) index + 1 else 1
                )
            )
        }
    }
}
