package com.example.readear

import java.util.concurrent.ConcurrentHashMap

/**
 * 全局页面缓存管理器（单例）
 * 
 * 职责：
 * - 管理所有文件的页面缓存
 * - 提供全局访问点
 * - 协调内存缓存和磁盘缓存
 */
object BooksCache {

    private val cacheMap = ConcurrentHashMap<String, PagesCache>()

    /**
     * 获取缓存
     * @param uri 文件 URI
     * @return 返回缓存对象，如果不存在返回 null
     */
    fun getCache(uri: String): PagesCache? {
        return cacheMap[uri]
    }

    /**
     * 检查是否有缓存
     * @param uri 文件 URI
     * @return 如果有缓存返回 true
     */
    fun hasCache(uri: String): Boolean {
        return cacheMap.containsKey(uri)
    }
    
    /**
     * 获取缓存的页面数量
     * @param uri 文件 URI
     * @return 返回页面数量，如果不存在返回 null
     */
    fun getPagesCount(uri: String): Int? {
        return cacheMap[uri]?.totalPages
    }
    
    /**
     * 设置缓存
     * @param uri 文件 URI
     * @param pagesCache 页面缓存对象
     */
    fun setCache(uri: String, pagesCache: PagesCache) {
        cacheMap[uri] = pagesCache
    }

    /**
     * 清除指定 URI 的缓存
     */
    fun clearCache(uri: String) {
        cacheMap.remove(uri)
    }

    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        cacheMap.clear()
    }

}
