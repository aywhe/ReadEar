package com.example.readear

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局 Application 类
 * 
 * 生命周期：App 启动时创建，进程死亡时销毁
 * 用于管理全局资源和状态
 */
class ReadEarApplication : Application() {
    
    /**
     * 全局页面缓存管理器
     * 通过 Application 持有单例引用，提供全局访问点
     */
    val pagesCacheManager: BooksCache by lazy {
        BooksCache
    }
    
    // 全局播放按钮状态管理器
    private val stateManager by lazy {
        GlobalPlayButtonStateManager.getInstance()
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ReadEarApplication", "✅ Application onCreate")
        
        // 初始化缓存管理器（如果需要额外的初始化逻辑）
        // pagesCacheManager 会在首次访问时自动初始化
        
        // 初始化并显示全局播放按钮
        initPlayButton()
    }
    
    /**
     * 初始化播放按钮
     */
    private fun initPlayButton() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 恢复上次的播放状态
                restorePlaybackState()
                
                Log.d("ReadEarApplication", "✅ 播放按钮状态已初始化")
            } catch (e: Exception) {
                Log.e("ReadEarApplication", "❌ 初始化播放按钮失败：${e.message}", e)
            }
        }
    }
    
    /**
     * 恢复上次的播放状态
     */
    private suspend fun restorePlaybackState() {
        try {
            val playbackProgressManager = PlaybackProgressManager.getInstance(this@ReadEarApplication)
            val allProgress = playbackProgressManager.getAllPlaybackProgress()
            
            if (allProgress.isNotEmpty()) {
                // 取最近播放的一个
                val lastProgress = allProgress.maxByOrNull { it.timestamp }
                lastProgress?.let { progress ->
                    Log.d("ReadEarApplication", "📚 恢复播放进度：书籍=${progress.bookId}, 页码=${progress.pageNumber + 1}")
                    
                    // 更新播放按钮的状态（暂停状态，等待用户点击）
                    withContext(Dispatchers.Main) {
                        stateManager.updatePlaybackState(
                            bookId = progress.bookId,
                            pageNumber = progress.pageNumber,
                            playing = false // 初始化为暂停状态
                        )
                    }
                }
            } else {
                Log.d("ReadEarApplication", "ℹ️ 没有播放历史记录")
            }
        } catch (e: Exception) {
            Log.e("ReadEarApplication", "❌ 恢复播放状态失败：${e.message}", e)
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.d("ReadEarApplication", "❌ Application onTerminate")
        
        // 注意：这个方法在真机上通常不会被调用
        // 清理所有缓存
        pagesCacheManager.clearAllCache()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.d("ReadEarApplication", "⚠️ 内存不足")
        
        // 系统内存不足时会被调用
        // 可以选择清理缓存以释放内存
        pagesCacheManager.clearAllCache()
    }
}
