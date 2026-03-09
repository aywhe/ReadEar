package com.example.readear

import android.app.Application
import com.example.readear.data.BooksCache

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
    
    override fun onCreate() {
        super.onCreate()
        // 初始化缓存管理器（如果需要额外的初始化逻辑）
        // pagesCacheManager 会在首次访问时自动初始化
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // 注意：这个方法在真机上通常不会被调用
        // 清理所有缓存
        pagesCacheManager.clearAllCache()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        // 系统内存不足时会被调用
        // 可以选择清理缓存以释放内存
        pagesCacheManager.clearAllCache()
    }
}
