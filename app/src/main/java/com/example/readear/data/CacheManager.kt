package com.example.readear.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 文本缓存管理器（基于 Room Database）
 *
 * 职责：
 * 1. 管理 App 内部生成的数据（缓存文件、阅读进度）
 * 2. 处理用户提供的文件（读取、提取、缓存）
 *
 * 数据存储：
 * - 使用 Room Database 存储书籍、页面和进度
 * - 使用 Application Context，避免内存泄漏
 *
 * @param context Context（会自动转换为 applicationContext）
 */
class CacheManager(private val context: Context) {

    private val database: ReadEarDatabase by lazy {
        ReadEarDatabase.getDatabase(context)
    }
    
    private val dao: BookDao by lazy {
        database.bookDao()
    }
    
    // bookUri -> bookId 映射缓存（线程安全，对外透明）
    private val bookIdCache = ConcurrentHashMap<String, Int>()

    /**
     * 获取或缓存 bookId（内部使用，对外透明）
     * @param bookUri 书籍 URI
     * @return 返回 bookId，如果不存在返回 null
     */
    private suspend fun getOrCacheBookId(bookUri: String): Int? = withContext(Dispatchers.IO) {
        // 优先从缓存获取
        bookIdCache[bookUri]?.let { cachedId ->
            return@withContext cachedId
        }

        // 缓存未命中，查询数据库
        val book = dao.getBook(bookUri)
        val bookId = book?.id

        // 存入缓存
        if (bookId != null) {
            bookIdCache[bookUri] = bookId
        }

        bookId
    }

    /**
     * 清除 bookId 缓存（删除书籍时调用）
     */
    private fun clearBookIdCache(bookUri: String) {
        bookIdCache.remove(bookUri)
    }

    // ==================== 书籍操作 ====================
    
    /**
     * 保存书籍信息（异步）
     * @param book 书籍对象
     */
    suspend fun saveBook(book: Book) = withContext(Dispatchers.IO) {
        dao.insertBook(book)
    }
    
    /**
     * 获取书籍信息
     * @param bookUri 书籍 URI
     * @return 返回书籍对象，如果不存在返回 null
     */
    suspend fun getBook(bookUri: String): Book? = withContext(Dispatchers.IO) {
        dao.getBook(bookUri)
    }

    suspend fun isCompleted(bookUri: String): Boolean = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUri)
        book?.isCompleted ?: false
    }
    
    /**
     * 获取所有书籍列表
     * @return 返回书籍列表
     */
    suspend fun getAllBooks(): List<Book> = withContext(Dispatchers.IO) {
        dao.getAllBooks()
    }
    
    /**
     * 删除书籍（包括所有页面和进度）
     * @param bookUri 书籍 ID
     */
    suspend fun deleteBook(bookUri: String) = withContext(Dispatchers.IO) {
        clearBookIdCache(bookUri)
        dao.deleteReadingProgress(bookUri)
        dao.deletePages(bookUri)
        dao.deleteBook(bookUri)
    }
    
    // ==================== 页面操作 ====================
    
    /**
     * 保存单个页面到数据库（异步）
     * 
     * 在后台线程中执行数据库插入操作，避免阻塞主线程。
     * 使用 Room DAO 将页面对象持久化存储。
     * 
     * @param bookUri 书籍 ID，用于标识所属的书籍
     * @param page 页面对象，包含页面内容和元数据
     */
    suspend fun savePage(bookUri: String, page: Page) = withContext(Dispatchers.IO) {
        val bookId = getOrCacheBookId(bookUri) ?: return@withContext
        val page = page.copy(bookId = bookId)
        dao.insertPage(page)
    }
    
    /**
     * 获取指定页面
     * @param bookUri 书籍 ID
     * @param pageNumber 页码
     * @return 返回页面对象，如果不存在返回 null
     */
    suspend fun getPage(bookUri: String, pageNumber: Int): Page? = withContext(Dispatchers.IO) {
        dao.getPage(bookUri, pageNumber)
    }

    suspend fun hasPage(bookUri: String, pageNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val page = dao.getPage(bookUri, pageNumber)
        page != null
    }
    
    /**
     * 获取页面范围（用于预加载）
     * @param bookUri 书籍 ID
     * @param startPage 起始页
     * @param endPage 结束页
     * @return 返回页面列表
     */
    suspend fun getPagesRange(bookUri: String, startPage: Int, endPage: Int): List<Page> = withContext(Dispatchers.IO) {
        dao.getPagesRange(bookUri, startPage, endPage)
    }
    
    /**
     * 获取所有页面
     * @param bookUri 书籍 ID
     * @return 返回所有页面列表
     */
    suspend fun getAllPages(bookUri: String): List<Page> = withContext(Dispatchers.IO) {
        dao.getAllPages(bookUri)
    }
    
    /**
     * 检查是否有页面缓存
     * @param bookUri 书籍 ID
     * @return 如果有缓存返回 true
     */
    suspend fun hasPagesCache(bookUri: String): Boolean = withContext(Dispatchers.IO) {
        dao.hasAnyPages(bookUri)
    }
    
    /**
     * 获取总页数
     * @param bookUri 书籍 ID
     * @return 返回总页数
     */
    suspend fun getTotalPagesCount(bookUri: String): Int = withContext(Dispatchers.IO) {
        dao.getTotalPagesCount(bookUri)
    }
    suspend fun getMaxPageIndex(bookUri: String): Int = withContext(Dispatchers.IO) {
        dao.getMaxPageIndex(bookUri)
    }
    
    // ==================== 阅读进度操作 ====================
    
    /**
     * 保存阅读进度（异步）
     * @param bookUri 书籍 ID
     * @param currentPage 当前页码
     */
    suspend fun saveReadingProgress(bookUri: String, currentPage: Int) = withContext(Dispatchers.IO) {
        val progress = ReadingProgress(
            bookUri = bookUri,
            currentPage = currentPage,
            timestamp = System.currentTimeMillis()
        )
        dao.insertReadingProgress(progress)
        
        // 同时更新书籍的最后阅读时间
        try {
            val book = dao.getBook(bookUri)
            if (book != null) {
                dao.insertBook(book.copy(lastReadTime = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            Log.e("CacheManager", "更新书籍最后阅读时间失败：${e.message}", e)
        }
    }
    
    /**
     * 加载阅读进度
     * @param bookUri 书籍 ID
     * @return 返回当前页码，如果未加载返回 null
     */
    suspend fun loadReadingProgress(bookUri: String): Int? = withContext(Dispatchers.IO) {
        val progress = dao.getReadingProgress(bookUri)
        progress?.currentPage
    }
    
    /**
     * 保存书籍断点信息（异步）
     * @param bookUri 书籍 ID
     * @param breakpoint 断点位置（字符索引）
     * @param breakpointPage 断点所在页码
     * @param breakRemainContent 断点剩余内容
     */
    suspend fun saveBreakpoint(
        bookUri: String,
        breakpoint: Int,
        breakpointPage: Int,
        breakRemainContent: String
    ) = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUri)
        if (book != null) {
            dao.insertBook(
                book.copy(
                    breakpoint = breakpoint,
                    breakpointPage = breakpointPage,
                    breakRemainContent = breakRemainContent,
                    lastReadTime = System.currentTimeMillis()
                )
            )
        }
    }
    
    /**
     * 获取书籍断点信息
     * @param bookUri 书籍 ID
     * @return 返回包含断点信息的 Book 对象，如果不存在返回 null
     */
    suspend fun getBreakpoint(bookUri: String): Triple<Int, Int, String>? = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookUri)
        if (book != null && book.breakpoint > 0) {
            Triple(book.breakpoint, book.breakpointPage, book.breakRemainContent)
        } else {
            null
        }
    }
}
