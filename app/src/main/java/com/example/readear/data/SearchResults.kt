package com.example.readear.data

import java.util.concurrent.ConcurrentHashMap

/**
 * 全局搜索结果缓存管理器（单例）
 * 
 * 职责：
 * - 管理所有文件的搜索结果缓存
 * - 支持同一文件缓存多个不同的搜索关键词
 * - 使用 Boolean 列表快速定位匹配的页面
 */
object SearchResults {

    /**
     * 搜索结果缓存结构：
     * URI -> (搜索词 -> List<Boolean>)
     * 
     * List<Boolean> 说明：
     * - 索引 = 页码
     * - true = 该页包含搜索词
     * - false = 该页不包含搜索词
     * 
     * 例如：URI="file1", query="hello", [true, false, true] 
     * 表示第 0、2 页包含 "hello"
     */
    private val searchCacheMap = ConcurrentHashMap<String, ConcurrentHashMap<String, List<Boolean?>>>()

    /**
     * 获取某个文件的所有搜索词缓存
     * @param uri 文件 URI
     * @return 返回 Map<搜索词，Boolean 列表>，如果不存在返回 null
     */
    fun getAllSearchResults(uri: String): Map<String, List<Boolean?>>? {
        return searchCacheMap[uri]
    }

    /**
     * 获取指定搜索词的匹配结果
     * @param uri 文件 URI
     * @param query 搜索关键词
     * @return 返回 Boolean 列表（索引=页码，true=匹配），如果不存在返回 null
     */
    fun getSearchResult(uri: String, query: String): List<Boolean?>? {
        return searchCacheMap[uri]?.get(query)
    }

    /**
     * 检查是否有搜索结果缓存
     * @param uri 文件 URI
     * @return 如果有缓存返回 true
     */
    fun hasSearchResult(uri: String): Boolean {
        return searchCacheMap.containsKey(uri)
    }

    /**
     * 检查是否有指定搜索词的缓存
     * @param uri 文件 URI
     * @param query 搜索关键词
     * @return 如果有缓存返回 true
     */
    fun hasSearchQuery(uri: String, query: String): Boolean {
        return searchCacheMap[uri]?.containsKey(query) ?: false
    }

    /**
     * 设置搜索结果缓存
     * @param uri 文件 URI
     * @param query 搜索关键词
     * @param matchedPages Boolean 列表（索引=页码，true=匹配）
     */
    fun setSearchResult(uri: String, query: String, matchedPages: List<Boolean?>) {
        val fileCache = searchCacheMap.getOrPut(uri) { ConcurrentHashMap() }
        fileCache[query] = matchedPages
    }

    /**
     * 更新搜索结果（添加或替换）
     * @param uri 文件 URI
     * @param query 搜索关键词
     * @param cache 布尔列表
     */
    fun updateSearchResult(uri: String, query: String, cache: List<Boolean?>) {
        setSearchResult(uri, query, cache)
    }

    /**
     * 清除指定 URI 的所有搜索结果缓存
     * @param uri 文件 URI
     */
    fun clearSearchResult(uri: String) {
        searchCacheMap.remove(uri)
    }

    /**
     * 清除指定 URI 的某个搜索词缓存
     * @param uri 文件 URI
     * @param query 搜索关键词
     */
    fun clearSearchQuery(uri: String, query: String) {
        searchCacheMap[uri]?.remove(query)
    }

    /**
     * 清除所有搜索结果缓存
     */
    fun clearAllSearchResults() {
        searchCacheMap.clear()
    }

    /**
     * 获取所有缓存的 URI 列表
     * @return URI 列表
     */
    fun getCachedUris(): List<String> {
        return searchCacheMap.keys.toList()
    }

    /**
     * 获取缓存数量（文件数量）
     * @return 缓存的文件数量
     */
    fun getCacheCount(): Int {
        return searchCacheMap.size
    }

    /**
     * 获取某个文件中缓存的搜索词数量
     * @param uri 文件 URI
     * @return 搜索词数量
     */
    fun getSearchQueryCount(uri: String): Int {
        return searchCacheMap[uri]?.size ?: 0
    }
}
