package com.example.readear

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放进度数据类
 */
data class PlaybackProgress(
    val bookId: String,          // 书籍 URI
    val pageNumber: Int,         // 当前页码（从 0 开始）
    val timestamp: Long,         // 时间戳
    val isPlaying: Boolean = false // 是否在播放中
)

/**
 * 播放进度管理器
 * 
 * 负责管理音频播放的进度信息，包括：
 * - 保存和恢复播放进度
 * - 记录当前播放的书籍 URI 和页码
 * - 跨 Activity 共享播放状态
 * 
 * @param context Context
 */
class PlaybackProgressManager(private val context: Context) {
    
    private val database by lazy { ReadEarDatabase.getDatabase(context) }
    private val dao by lazy { database.bookDao() }
    
    companion object {
        private const val TAG = "PlaybackProgressMgr"
        
        @Volatile
        private var INSTANCE: PlaybackProgressManager? = null
        
        fun getInstance(context: Context): PlaybackProgressManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PlaybackProgressManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * 保存播放进度
     * 
     * @param bookId 书籍 URI
     * @param pageNumber 当前页码
     * @param isPlaying 是否在播放
     */
    suspend fun savePlaybackProgress(bookId: String, pageNumber: Int, isPlaying: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                val progress = ReadingProgress(
                    bookId = bookId,
                    currentPage = pageNumber,
                    timestamp = System.currentTimeMillis()
                )
                dao.insertReadingProgress(progress)
                
                Log.d(TAG, "💾 保存播放进度：书籍=$bookId, 页码=${pageNumber + 1}, 播放中=$isPlaying")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 保存播放进度失败：${e.message}", e)
            }
        }
    }
    
    /**
     * 获取播放进度
     * 
     * @param bookId 书籍 URI
     * @return 播放进度，如果不存在返回 null
     */
    suspend fun getPlaybackProgress(bookId: String): PlaybackProgress? {
        return withContext(Dispatchers.IO) {
            try {
                val progress = dao.getReadingProgress(bookId)
                progress?.let {
                    PlaybackProgress(
                        bookId = it.bookId,
                        pageNumber = it.currentPage,
                        timestamp = it.timestamp,
                        isPlaying = false // 默认不播放，由用户点击控制
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 获取播放进度失败：${e.message}", e)
                null
            }
        }
    }
    
    /**
     * 获取所有播放进度
     * 
     * @return 所有书籍的播放进度列表
     */
    suspend fun getAllPlaybackProgress(): List<PlaybackProgress> {
        return withContext(Dispatchers.IO) {
            try {
                val allBooks = dao.getAllBooks()
                allBooks.mapNotNull { book ->
                    val progress = dao.getReadingProgress(book.bookId)
                    progress?.let {
                        PlaybackProgress(
                            bookId = book.bookId,
                            pageNumber = it.currentPage,
                            timestamp = it.timestamp,
                            isPlaying = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 获取所有播放进度失败：${e.message}", e)
                emptyList()
            }
        }
    }
    
    /**
     * 删除播放进度
     * 
     * @param bookId 书籍 URI
     */
    suspend fun deletePlaybackProgress(bookId: String) {
        withContext(Dispatchers.IO) {
            try {
                dao.deleteReadingProgress(bookId)
                Log.d(TAG, "🗑️ 删除播放进度：书籍=$bookId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 删除播放进度失败：${e.message}", e)
            }
        }
    }
    
    /**
     * 更新播放状态
     * 
     * @param bookId 书籍 URI
     * @param isPlaying 是否正在播放
     */
    suspend fun updatePlayingState(bookId: String, isPlaying: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                val progress = dao.getReadingProgress(bookId)
                if (progress != null) {
                    val updatedProgress = progress.copy(
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insertReadingProgress(updatedProgress)
                    Log.d(TAG, "🎵 更新播放状态：书籍=$bookId, 播放中=$isPlaying")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 更新播放状态失败：${e.message}", e)
            }
        }
    }
}
