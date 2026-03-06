package com.example.readear

/**
 * 文本页面缓存数据类
 * 
 * 存储单个文件的所有页面信息，包括：
 * - URI 标识
 * - 所有页面的文本内容
 * - 上次阅读位置
 * - 加载状态等
 * 
 * @param uri 文件 URI 字符串
 */
class PagesCache(val uri: String) {

    /**
     * 所有页面的列表（索引即页码，从 0 开始）
     */
    private val pages = mutableMapOf<UInt, TextChunk>()
    
    /**
     * 上次阅读的页码
     */
    private var lastReadingPage: Int = 0
    
    /**
     * 是否已完整保存（整本书是否已全部缓存）
     */
    private var isCompleted: Boolean = false
    
    /**
     * 检查是否已完整保存
     * @return 如果已完整保存返回 true
     */
    fun isCompleted(): Boolean {
        return isCompleted
    }
    
    /**
     * 设置是否已完整保存
     * @param completed 完成状态
     */
    fun setCompleted(completed: Boolean) {
        this.isCompleted = completed
    }
    
    /**
     * 总页数
     */
    val totalPages: Int
        get() = pages.size
    
    /**
     * 添加页面到缓存
     * @param chunk 文本块
     */
    fun addPage(chunk: TextChunk) {
        val pageNumber = pages.size.toUInt()
        pages[pageNumber] = chunk
    }
    
    /**
     * 批量添加页面
     * @param chunks 文本块列表
     */
    fun addAllPages(chunks: List<TextChunk>) {
        var startIndex = pages.size.toUInt()
        chunks.forEach { chunk ->
            pages[startIndex] = chunk
            startIndex++
        }
    }
    
    /**
     * 获取指定页的内容
     * @param pageNumber 页码（从 0 开始）
     * @return 返回该页的文本块，如果页码无效返回 null
     */
    fun getPage(pageNumber: Int): TextChunk? {
        return pages[pageNumber.toUInt()]
    }
    
    /**
     * 获取所有页面
     * @return 返回所有页面的列表（只读）
     */
    fun getAllPages(): List<TextChunk> {
        return pages.toSortedMap().values.toList()
    }
    
    /**
     * 检查页码是否有效
     * @param pageNumber 页码
     * @return 如果页码在有效范围内返回 true
     */
    fun isValidPage(pageNumber: Int): Boolean {
        return pages.containsKey(pageNumber.toUInt())
    }
    
    /**
     * 检查是否是最后一页
     * @param pageNumber 页码
     * @return 如果是最后一页返回 true
     */
    fun isLastPage(pageNumber: Int): Boolean {
        if (pages.isEmpty()) return false
        return pageNumber.toUInt() == pages.keys.maxOrNull()!!
    }
    
    /**
     * 清除所有页面数据
     */
    fun clear() {
        pages.clear()
        lastReadingPage = 0
        isCompleted = false
    }
}