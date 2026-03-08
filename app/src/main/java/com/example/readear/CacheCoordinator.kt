package com.example.readear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 缓存协调器
 * 
 * 职责：
 * - 协调内存缓存和数据库缓存
 * - 批量加载数据到内存
 * - 确保缓存一致性
 * 
 * @param context Context
 * @param booksCache 内存缓存管理器
 */
class CacheCoordinator(
    private val context: Context,
    private val booksCache: BooksCache
) {
    
    private val cacheManager: CacheManager by lazy {
        CacheManager(context)
    }
    
    /**
     * 检查书籍是否有缓存（任意一级）
     */
    suspend fun hasCache(uri: String): Boolean {
        return booksCache.hasCache(uri) || cacheManager.hasPagesCache(uri)
    }
    
    /**
     * 获取总页数（优先从内存缓存获取）
     */
    suspend fun getPagesCount(uri: String): Int? {
        return booksCache.getPagesCount(uri) ?: cacheManager.getTotalPagesCount(uri)
    }
    
    /**
     * 获取上次阅读页码（优先从内存缓存获取）
     */
    suspend fun getLastReadPageNumber(uri: String): Int? {
        return withContext(Dispatchers.Default) {
            val lastPage = booksCache.getCache(uri)?.getLastReadingPageNumber()
            if (lastPage != null) {
                return@withContext lastPage
            }
            
            val dbProgress = cacheManager.loadReadingProgress(uri)
            if (dbProgress != null) {
                booksCache.getCache(uri)?.setLastReadingPageNumber(dbProgress)
                return@withContext dbProgress
            }
            
            null
        }
    }
    
    /**
     * 从数据库批量加载页面到内存缓存
     * 
     * @param uri 文件 URI
     * @param startPage 起始页码
     * @param endPage 结束页码
     * @return 返回成功加载的页面数量
     */
    suspend fun loadPagesToMemory(uri: String, startPage: Int, endPage: Int): Int {
        return withContext(Dispatchers.Default) {
            val memoryCache = ensureMemoryCacheExists(uri)
            
            val dbPages = try {
                cacheManager.getPagesRange(uri, startPage, endPage)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            
            var loadedCount = 0
            dbPages.forEach { page ->
                if (!memoryCache.hasPage(page.pageNumber)) {
                    memoryCache.addPage(
                        TextChunk(page.content, page.isCompleted, page.pageNumber)
                    )
                    loadedCount++
                }
            }
            
            loadedCount
        }
    }
    
    /**
     * 从数据库加载所有页面到内存（用于完整缓存）
     */
    suspend fun loadAllPagesToMemory(uri: String): Int {
        return withContext(Dispatchers.Default) {
            val memoryCache = ensureMemoryCacheExists(uri)
            
            val allPages = try {
                cacheManager.getAllPages(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            
            allPages.forEach { page ->
                memoryCache.addPage(
                    TextChunk(page.content, page.isCompleted, page.pageNumber)
                )
            }
            
            val book = try {
                cacheManager.getBook(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            
            book?.let {
                memoryCache.setCompleted(it.isCompleted)
                val progress = cacheManager.loadReadingProgress(uri)
                progress?.let { lastPage ->
                    memoryCache.setLastReadingPageNumber(lastPage)
                }
            }
            
            allPages.size
        }
    }
    
    /**
     * 保存页面到两级缓存
     */
    suspend fun savePage(uri: String, page: Page) {
        withContext(Dispatchers.Default) {
            val memoryCache = ensureMemoryCacheExists(uri)
            memoryCache.addPage(TextChunk(page.content, page.isCompleted, page.pageNumber))
            
            try {
                cacheManager.savePage(uri, page)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 保存阅读进度到两级缓存
     */
    suspend fun saveReadingProgress(uri: String, currentPage: Int) {
        withContext(Dispatchers.Default) {
            booksCache.getCache(uri)?.setLastReadingPageNumber(currentPage)
            
            try {
                cacheManager.saveReadingProgress(uri, currentPage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 标记缓存完成状态
     */
    suspend fun markCacheAsCompleted(uri: String, totalWords: Int, totalPages: Int) {
        withContext(Dispatchers.Default) {
            booksCache.getCache(uri)?.setCompleted(true)
            
            try {
                val book = cacheManager.getBook(uri)
                if (book != null) {
                    cacheManager.saveBook(
                        book.copy(
                            totalWords = totalWords,
                            totalPages = totalPages,
                            isCompleted = true,
                            lastReadTime = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 清除指定书籍的缓存
     */
    suspend fun clearCache(uri: String) {
        booksCache.clearCache(uri)
        try {
            cacheManager.deleteBook(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun ensureMemoryCacheExists(uri: String): PagesCache {
        return booksCache.getCache(uri) ?: run {
            val newCache = PagesCache(uri)
            booksCache.setCache(uri, newCache)
            newCache
        }
    }
}
