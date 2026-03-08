package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * 文本管理器（重构版本）
 * 
 * 职责：
 * - 协调 TextLoader、CacheCoordinator 完成文本加载和缓存管理
 * - 提供简洁的外部接口
 * 
 * 架构改进：
 * 1. 职责分离：TextLoader 负责提取和分页，CacheCoordinator 负责缓存管理
 * 2. 依赖注入：通过构造函数注入依赖，便于测试
 * 3. 错误处理：关键操作添加异常捕获和日志记录
 * 4. 性能优化：批量查询替代循环查询
 * 
 * @param context Context
 */
class TextManager(private val context: Context) {
    
    private val booksCache: BooksCache = BooksCache
    private val textExtractorFactory: TextExtractorFactory = TextExtractorFactory(context)
    private val textLoader: TextLoader = TextLoader(textExtractorFactory)
    private val cacheCoordinator: CacheCoordinator = CacheCoordinator(context, booksCache)
    
    companion object {
        private const val TAG = "TextManager"
    }
    
    /**
     * 检查指定 URI 的书籍是否有缓存
     */
    suspend fun hasBook(uri: String): Boolean {
        return cacheCoordinator.hasCache(uri)
    }
    
    /**
     * 获取缓存的页面数量
     */
    suspend fun getPagesCount(uri: String): Int? {
        return cacheCoordinator.getPagesCount(uri)
    }
    
    /**
     * 获取上次阅读的页码（仅从内存缓存）
     */
    suspend fun getLastReadPageNumber(uri: String): Int? {
        return try {
            cacheCoordinator.getLastReadPageNumber(uri)
        } catch (e: Exception) {
            Log.e(TAG, "获取阅读进度失败：${e.message}", e)
            null
        }
    }

    /**
     * 获取缓存的页面内容
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回 null
     */
    suspend fun getPage(uri: String, pageNumber: Int): TextChunk? {
        return withContext(Dispatchers.Default) {
            try {
                booksCache.getCache(uri)?.getPage(pageNumber)
            } catch (e: Exception) {
                Log.e(TAG, "获取页面失败：$pageNumber, ${e.message}", e)
                null
            }
        }
    }

    /**
     * 预加载指定范围的页面到内存缓存（批量查询优化）
     * 
     * @param uri 文件 URI
     * @param startPage 起始页码
     * @param endPage 结束页码
     * @return 返回成功加载的页面数量
     */
    suspend fun preloadPagesRange(uri: String, startPage: Int, endPage: Int): Int {
        return withContext(Dispatchers.Default) {
            try {
                if (startPage > endPage) {
                    return@withContext 0
                }
                
                cacheCoordinator.loadPagesToMemory(uri, startPage, endPage)
            } catch (e: Exception) {
                Log.e(TAG, "预加载页面失败：$startPage-$endPage, ${e.message}", e)
                0
            }
        }
    }
    
    /**
     * 同步加载单个页面内容（用于 Compose UI）
     * @param uri 文件 URI
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果不存在返回 null
     */
    fun getPageFromMemory(uri: String, pageNumber: Int): TextChunk? {
        return try {
            booksCache.getCache(uri)?.getPage(pageNumber)
        } catch (e: Exception) {
            Log.e(TAG, "同步获取页面失败：$pageNumber, ${e.message}", e)
            null
        }
    }

    
    /**
     * 保存阅读进度（两级缓存）
     */
    suspend fun saveReadingProgress(uri: String, currentPage: Int) {
        try {
            cacheCoordinator.saveReadingProgress(uri, currentPage)
        } catch (e: Exception) {
            Log.e(TAG, "保存阅读进度失败：$currentPage, ${e.message}", e)
        }
    }
    
    /**
     * 清除指定书籍的缓存
     */
    suspend fun delBook(uri: String) {
        try {
            cacheCoordinator.clearCache(uri)
        } catch (e: Exception) {
            Log.e(TAG, "删除书籍失败：${e.message}", e)
        }
    }
    
    /**
     * 清除所有缓存
     */
    suspend fun clearAllCache() {
        booksCache.clearAllCache()
    }
    
    /**
     * 同步加载页面内容（重构版本）
     * 
     * 检查内存缓存和数据库缓存的完整性，按需加载数据：
     * 1. 如果内存缓存已完整，直接返回
     * 2. 如果数据库缓存已完整，将数据加载到内存
     * 3. 如果都不完整，启动后台提取任务
     * 
     * @param uri 文件 URI
     * @param fileType 文件类型
     * @param avgCharsPerLine 每行平均字符数
     * @param maxLinesPerPage 每页最大行数
     */
    suspend fun syncLoadPages(uri: Uri, fileType: FileType, avgCharsPerLine: Int, maxLinesPerPage: Int) {
        val uriString = uri.toString()
        
        try {
            val memoryCache = booksCache.getCache(uriString)
            if (memoryCache?.isCompleted() == true) {
                Log.d(TAG, "内存缓存已完整，直接返回：$uriString")
                return
            }
            
            val book = try {
                CacheManager(context).getBook(uriString)
            } catch (e: Exception) {
                Log.e(TAG, "获取书籍信息失败：${e.message}", e)
                null
            }
            
            if (book?.isCompleted == true) {
                Log.d(TAG, "数据库缓存已完整，加载到内存：$uriString")
                cacheCoordinator.loadAllPagesToMemory(uriString)
            } else {
                Log.d(TAG, "启动后台提取任务：$uriString")
                extractTextFromFileAndSaveCache(uri, fileType, avgCharsPerLine, maxLinesPerPage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "同步加载页面失败：${e.message}", e)
        }
    }
    
    /**
     * 异步提取文本并保存缓存（增强错误处理）
     */
    private suspend fun extractTextFromFileAndSaveCache(
        uri: Uri, 
        fileType: FileType, 
        avgCharsPerLine: Int, 
        maxLinesPerPage: Int
    ) = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        
        try {
            if (!booksCache.hasCache(uriString)) {
                booksCache.setCache(uriString, PagesCache(uriString))
            }
            
            var pageCount = 0
            var lastPageIndex = 0
            var totalWords = 0
            
            textLoader.extractAndPaginate(uri, fileType, avgCharsPerLine, maxLinesPerPage).collect { textChunk ->
                try {
                    val page = Page(
                        bookId = uriString,
                        pageNumber = textChunk.index,
                        content = textChunk.content,
                        isCompleted = textChunk.isCompleted
                    )
                    
                    cacheCoordinator.savePage(uriString, page)
                    
                    pageCount++
                    lastPageIndex = textChunk.index
                    totalWords += textChunk.content.length
                    
                    if (pageCount % 100 == 0 || textChunk.isCompleted) {
                        saveBookInfo(uriString, totalWords, lastPageIndex + 1)
                    }
                    
                    if (textChunk.isCompleted) {
                        cacheCoordinator.markCacheAsCompleted(uriString, totalWords, lastPageIndex + 1)
                        Log.i(TAG, "文本提取完成：$uriString, 总页数：${lastPageIndex + 1}, 总字数：$totalWords")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "保存页面失败：${textChunk.index}, ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取文本失败：${e.message}", e)
        }
    }
    
    private suspend fun saveBookInfo(uriString: String, totalWords: Int, totalPages: Int) {
        try {
            val cacheManager = CacheManager(context)
            val existingBook = cacheManager.getBook(uriString)
            
            if (existingBook != null) {
                cacheManager.saveBook(
                    existingBook.copy(
                        totalWords = totalWords,
                        totalPages = totalPages,
                        lastReadTime = System.currentTimeMillis()
                    )
                )
            } else {
                cacheManager.saveBook(
                    Book(
                        bookId = uriString,
                        title = uriString.substringAfterLast("/"),
                        filePath = uriString,
                        totalWords = totalWords,
                        totalPages = totalPages,
                        lastReadTime = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存书籍信息失败：${e.message}", e)
        }
    }
}
