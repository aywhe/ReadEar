package com.example.readear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
     * @param bookId 书籍 ID
     * @return 返回书籍对象，如果不存在返回 null
     */
    suspend fun getBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        dao.getBook(bookId)
    }

    suspend fun isCompleted(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val book = dao.getBook(bookId)
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
     * @param bookId 书籍 ID
     */
    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        dao.deleteReadingProgress(bookId)
        dao.deletePages(bookId)
        dao.deleteBook(bookId)
    }
    
    // ==================== 页面操作 ====================
    
    /**
     * 保存单个页面到数据库（异步）
     * 
     * 在后台线程中执行数据库插入操作，避免阻塞主线程。
     * 使用 Room DAO 将页面对象持久化存储。
     * 
     * @param bookId 书籍 ID，用于标识所属的书籍
     * @param page 页面对象，包含页面内容和元数据
     */
    suspend fun savePage(bookId: String, page: Page) = withContext(Dispatchers.IO) {
        dao.insertPage(page)
    }
    
    /**
     * 获取指定页面
     * @param bookId 书籍 ID
     * @param pageNumber 页码
     * @return 返回页面对象，如果不存在返回 null
     */
    suspend fun getPage(bookId: String, pageNumber: Int): Page? = withContext(Dispatchers.IO) {
        dao.getPage(bookId, pageNumber)
    }

    suspend fun hasPage(bookId: String, pageNumber: Int): Boolean = withContext(Dispatchers.IO) {
        val page = dao.getPage(bookId, pageNumber)
        page != null
    }
    
    /**
     * 获取页面范围（用于预加载）
     * @param bookId 书籍 ID
     * @param startPage 起始页
     * @param endPage 结束页
     * @return 返回页面列表
     */
    suspend fun getPagesRange(bookId: String, startPage: Int, endPage: Int): List<Page> = withContext(Dispatchers.IO) {
        dao.getPagesRange(bookId, startPage, endPage)
    }
    
    /**
     * 获取所有页面
     * @param bookId 书籍 ID
     * @return 返回所有页面列表
     */
    suspend fun getAllPages(bookId: String): List<Page> = withContext(Dispatchers.IO) {
        dao.getAllPages(bookId)
    }
    
    /**
     * 检查是否有页面缓存
     * @param bookId 书籍 ID
     * @return 如果有缓存返回 true
     */
    suspend fun hasPagesCache(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val pages = dao.getAllPages(bookId)
        pages.isNotEmpty()
    }
    
    /**
     * 获取总页数
     * @param bookId 书籍 ID
     * @return 返回总页数
     */
    suspend fun getTotalPagesCount(bookId: String): Int = withContext(Dispatchers.IO) {
        dao.getTotalPagesCount(bookId)
    }
    suspend fun getMaxPageIndex(bookId: String): Int = withContext(Dispatchers.IO) {
        dao.getMaxPageIndex(bookId)
    }
    
    // ==================== 阅读进度操作 ====================
    
    /**
     * 保存阅读进度（异步）
     * @param bookId 书籍 ID
     * @param currentPage 当前页码
     */
    suspend fun saveReadingProgress(bookId: String, currentPage: Int) = withContext(Dispatchers.IO) {
        val progress = ReadingProgress(
            bookId = bookId,
            currentPage = currentPage,
            timestamp = System.currentTimeMillis()
        )
        dao.insertReadingProgress(progress)
        
        // 同时更新书籍的最后阅读时间
        try {
            val book = dao.getBook(bookId)
            if (book != null) {
                dao.insertBook(book.copy(lastReadTime = System.currentTimeMillis()))
            }
        } catch (e: Exception) {
            android.util.Log.e("CacheManager", "更新书籍最后阅读时间失败：${e.message}", e)
        }
    }
    
    /**
     * 加载阅读进度
     * @param bookId 书籍 ID
     * @return 返回当前页码，如果未加载返回 null
     */
    suspend fun loadReadingProgress(bookId: String): Int? = withContext(Dispatchers.IO) {
        val progress = dao.getReadingProgress(bookId)
        progress?.currentPage
    }
}
