package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文本管理器
 * 
 * 职责：
 * - 管理书籍页面内容的加载和缓存
 * - 优先从 PagesCache 获取，其次从数据库加载
 * - 提供异步加载接口，避免 UI 卡顿
 */
class TextManager(private val context: Context) {
    
    private val pagesCacheManager: BooksCache = BooksCache
    
    /**
     * 检查指定 URI 的书籍是否有缓存
     */
    fun hasCache(uri: String): Boolean {
        return pagesCacheManager.hasCache(uri)
    }
    
    /**
     * 获取缓存的页面内容
     */
    private fun getCachedPages(uri: String): Map<Int, TextChunk>?{
        return pagesCacheManager.getCache(uri)?.getAllPages()
    }
    
    /**
     * 获取缓存的页面数量
     */
    suspend fun getCachedPagesCount(uri: String): Int? {
        return withContext(Dispatchers.IO) {
            pagesCacheManager.getCache(uri)?.getAllPages()?.size
        }
    }
    
    /**
     * 获取上次阅读的页码
     */
    suspend fun getLastReadPage(uri: String): Int {
        return withContext(Dispatchers.IO) {
            // 1. 先从内存缓存获取（最快）
            val cachedProgress = pagesCacheManager.getCache(uri)?.getLastReadingPage()
            if (cachedProgress != null && cachedProgress > 0) {
                return@withContext cachedProgress
            }
            
            // 2. 从数据库获取（如果缓存中没有或为 0）
            val cacheManager = CacheManager(context)
            val dbProgress = cacheManager.loadReadingProgress(uri)
            
            // 3. 同步到缓存（方便下次快速访问）
            dbProgress?.let { progress ->
                pagesCacheManager.getCache(uri)?.setLastReadingPage(progress)
            }
            
            dbProgress ?: 0
        }
    }
    
    /**
     * 加载书籍的所有页面内容
     * 优先从缓存获取，如果没有则从数据库加载
     * 如果数据库也没有，留给后续处理（TODO）
     */
    suspend fun loadBookContent(uri: String): LoadResult {
        return withContext(Dispatchers.IO) {
            // 1. 检查缓存
            val cachedPages = getCachedPages(uri)
            if (cachedPages != null && cachedPages.isNotEmpty()) {
                return@withContext LoadResult.Success(cachedPages)
            }
            
            // 2. 从数据库加载
            val cacheManager = CacheManager(context)
            val bookId = uri // 使用 URI 作为 bookId
            
            // 检查数据库中是否有这本书
             val book = cacheManager.getBook(bookId)
             if (book != null) {
                 val pages = cacheManager.getAllPages(bookId)
                 // 同步到缓存
                 val textChunksMap = pages.associate { page ->
                     page.pageNumber to TextChunk(page.content, false, page.index)
                 }
                 // 添加到缓存
                 val pagesCache = PagesCache(uri)
                 pagesCache.addAllPages(textChunksMap)
                 pagesCacheManager.setCache(uri, pagesCache)
                 return@withContext LoadResult.Success(textChunksMap)
             }
            
            // 3. 数据库也没有，需要提取文本
            // TODO: 调用 TextExtractor 提取文本，同时保存到数据库和缓存
            LoadResult.NotExist
        }
    }
    
    /**
     * 加载单个页面内容
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回 null
     */
    suspend fun loadPage(uri: String, pageNumber: Int): TextChunk? {
        return withContext(Dispatchers.IO) {
            // 1. 先尝试从内存缓存获取
            val cachedPage = pagesCacheManager.getCache(uri)?.getPage(pageNumber)
            if (cachedPage != null) {
                return@withContext cachedPage
            }
            
            // 2. 从数据库加载
            val cacheManager = CacheManager(context)
            val page = cacheManager.getPage(uri, pageNumber)
            
            if (page != null) {
                val textChunk = TextChunk(page.content, false, page.index)
                
                var pagesCache = pagesCacheManager.getCache(uri)
                if (pagesCache == null) {
                    pagesCache = PagesCache(uri)
                    pagesCacheManager.setCache(uri, pagesCache)
                }
                pagesCache.addPage(textChunk)
                
                return@withContext textChunk
            }
            
            // 3. 数据库也没有，返回 null（需要后续提取）
            null
        }
    }
    
    /**
     * 同步加载单个页面内容（用于 Compose UI）
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回空文本块
     */
    fun loadPageSync(uri: String, pageNumber: Int): TextChunk? {
        val cachedPage = pagesCacheManager.getCache(uri)?.getPage(pageNumber)
        if (cachedPage != null) {
            return cachedPage
        }
        
        // 如果缓存中没有，返回 null（会在后台异步加载）
        return null
    }
    
    /**
     * 预加载指定范围的页面
     * @param uri 文件 URI
     * @param startPage 起始页码
     * @param endPage 结束页码
     * @return 返回已加载的页面数量
     */
    suspend fun preloadPagesRange(uri: String, startPage: Int, endPage: Int): Int {
        return withContext(Dispatchers.IO) {
            var loadedCount = 0
            
            for (pageNumber in startPage..endPage) {
                // 检查是否已在缓存中
                if (pagesCacheManager.getCache(uri)?.getPage(pageNumber) == null) {
                    // 不在缓存中，从数据库加载
                    val cacheManager = CacheManager(context)
                    val page = cacheManager.getPage(uri, pageNumber)
                    
                    if (page != null) {
                        val textChunk = TextChunk(page.content, false,page.index)
                        
                        var pagesCache = pagesCacheManager.getCache(uri)
                        if (pagesCache == null) {
                            pagesCache = PagesCache(uri)
                            pagesCacheManager.setCache(uri, pagesCache)
                        }
                        pagesCache.addPage(textChunk)
                        loadedCount++
                    }
                }
            }
            
            loadedCount
        }
    }

    /**
     * 保存阅读进度
     */
    suspend fun saveReadingProgress(uri: String, currentPage: Int) {
        withContext(Dispatchers.IO) {
            // 1. 保存到数据库
            val cacheManager = CacheManager(context)
            cacheManager.saveReadingProgress(uri, currentPage)
            
            // 2. 同步到内存缓存
            pagesCacheManager.getCache(uri)?.setLastReadingPage(currentPage)
        }
    }
    
    /**
     * 预加载后续页面
     */
    suspend fun preloadNextPages(uri: String, currentPage: Int, pageCount: Int = 5) {
        withContext(Dispatchers.IO) {
            // TODO: 预加载逻辑
            // 检查后续页面是否已在缓存中
            // 如果不在，提前加载
        }
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
