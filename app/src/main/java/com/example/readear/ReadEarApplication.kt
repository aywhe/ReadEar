package com.example.readear

import android.app.Application
import android.util.Log
import com.example.readear.data.BooksCache
import com.example.readear.speech.TTSEngineType
import com.example.readear.speech.UserTextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 全局 Application 类
 * 
 * 生命周期：App 启动时创建，进程死亡时销毁
 * 用于管理全局资源和状态
 */
class ReadEarApplication : Application() {
    val booksCache = BooksCache()
    var userTextToSpeech: UserTextToSpeech? = null
    
    // 应用级别的 CoroutineScope，用于后台异步任务
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val context = this
        // 在后台异步初始化 TTS，避免阻塞主线程
        applicationScope.launch {
            try {
                userTextToSpeech = UserTextToSpeech(context, TTSEngineType.DEFAULT)
                userTextToSpeech?.reinitialize()
            } catch (e: Exception) {
                // TTS 初始化失败不影响应用启动，记录日志即可
                Log.e("ReadEarApplication", "TTS 初始化失败：${e.message}", e)
            }
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // 清理 TTS 资源
        userTextToSpeech?.release()
        // 注意：BooksCache 会在进程终止时自动释放，无需手动清理
        booksCache.clearAllCache()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        // 内存不足时可以清理缓存
        // booksCache.clearAllCache()  // 可选：根据需求决定是否清理
    }
}
