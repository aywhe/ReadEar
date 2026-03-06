package com.example.readear

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
    fun getCachedPages(uri: String): List<TextChunk>? {
        return pagesCacheManager.getCache(uri)?.getAllPages()
    }
    
    /**
     * 获取上次阅读的页码
     */
    suspend fun getLastReadPage(uri: String): Int {
        return withContext(Dispatchers.IO) {
            pagesCacheManager.getCache(uri)?.let { cache ->
                // TODO: 从缓存或数据库获取上次阅读页码
                0
            } ?: 0
        }
    }
    
    /**
     * 加载书籍的所有页面内容
     * 优先从缓存获取，如果没有则从数据库加载
     * 如果数据库也没有，留给后续处理（TODO）
     */
    suspend fun loadBookContent(uri: String, fileName: String, fileType: FileType): LoadResult {
        return withContext(Dispatchers.IO) {
            // 1. 检查缓存
            val cachedPages = getCachedPages(uri)
            if (cachedPages != null && cachedPages.isNotEmpty()) {
                return@withContext LoadResult.Success(cachedPages)
            }
            
            // 2. 从数据库加载
            val cacheManager = CacheManager(context)
            val bookId = uri // 使用 URI 作为 bookId
            
            // TODO: 检查数据库中是否有这本书
            // val book = cacheManager.getBook(bookId)
            // if (book != null) {
            //     val pages = cacheManager.getAllPages(bookId)
            //     // 同步到缓存
            //     val textChunks = pages.map { page ->
            //         TextChunk(page.content, page.chapterTitle, false, page.chapterIndex)
            //     }
            //     // 添加到缓存
            //     val pagesCache = PagesCache(uri)
            //     pagesCache.addAllPages(textChunks)
            //     pagesCacheManager.cacheMap[uri] = pagesCache
            //     return@withContext LoadResult.Success(textChunks)
            // }
            
            // 3. 数据库也没有，需要提取文本
            // TODO: 调用 TextExtractor 提取文本，同时保存到数据库和缓存
            LoadResult.NotExist
        }
    }
    
    /**
     * 保存阅读进度
     */
    suspend fun saveReadingProgress(uri: String, currentPage: Int) {
        withContext(Dispatchers.IO) {
            // TODO: 保存到数据库和缓存
            // val cacheManager = CacheManager(context)
            // cacheManager.saveReadingProgress(uri, currentPage)
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
        data class Success(val pages: List<TextChunk>) : LoadResult()
        object NotExist : LoadResult()
        data class Error(val message: String) : LoadResult()
    }
}
