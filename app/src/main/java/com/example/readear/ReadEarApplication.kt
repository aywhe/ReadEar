package com.example.readear

import android.app.Application
import com.example.readear.data.BooksCache
import com.example.readear.parser.TTSEngineType
import com.example.readear.parser.UserTextToSpeech

/**
 * 全局 Application 类
 * 
 * 生命周期：App 启动时创建，进程死亡时销毁
 * 用于管理全局资源和状态
 */
class ReadEarApplication : Application() {
    val booksCache = BooksCache()
    var userTextToSpeech: UserTextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        // 预加载资源（可选）
        userTextToSpeech = UserTextToSpeech(this, TTSEngineType.DEFAULT)

        userTextToSpeech?.reinitialize()
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
