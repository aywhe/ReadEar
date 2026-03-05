package com.example.readear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * 文本内容缓存管理器
 * 负责缓存已提取的文本内容，避免重复提取
 */
class TextCacheManager(private val context: Context) {
    
    companion object {
        private const val CACHE_DIR_NAME = "text_cache"
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 生成缓存文件的唯一标识
     */
    private fun getCacheFileName(uri: String): String {
        // 使用 URI 的哈希值作为文件名，避免特殊字符问题
        return "cache_${uri.hashCode()}.txt"
    }
    
    /**
     * 获取缓存文件
     */
    fun getCacheFile(uri: String): File {
        return File(cacheDir, getCacheFileName(uri))
    }
    
    /**
     * 检查是否有缓存
     */
    fun hasCache(uri: String): Boolean {
        return getCacheFile(uri).exists()
    }
    
    /**
     * 从缓存读取文本（按页分割）
     * @param uri 文件 URI
     * @param avgCharsPerLine 每行字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    fun readFromCache(
        uri: String,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        val cacheFile = getCacheFile(uri)
        if (!cacheFile.exists()) {
            return@flow
        }
        
        // 读取完整的缓存文本
        val fullText = cacheFile.readText()
        
        // 使用分页处理器进行分页
        val paginationProcessor = TextPaginationProcessor()
        paginationProcessor.paginateText(
            flow { emit(fullText) },
            avgCharsPerLine,
            maxLinesPerPage
        ).collect { chunk ->
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 保存文本到缓存（同时保存分页信息和进度）
     * @param uri 文件 URI
     * @param textFlow 原始文本流
     * @param paginationProcessor 分页处理器
     * @param avgCharsPerLine 每行字符数
     * @param maxLinesPerPage 每页最大行数
     */
    suspend fun saveToCache(uri: String, textFlow: Flow<String>, paginationProcessor: TextPaginationProcessor, avgCharsPerLine: Int, maxLinesPerPage: Int) {
        val cacheFile = getCacheFile(uri)
        val progressFile = File(cacheDir, "${getCacheFileName(uri)}.progress")
        
        // 使用临时文件，避免写入过程中被读取
        val tempFile = File(cacheDir, "${getCacheFileName(uri)}.tmp")
        
        try {
            // 先分页，然后保存为 JSON 格式：每行是一个页面对象
            var pageNumber = 0
            tempFile.bufferedWriter().use { writer ->
                paginationProcessor.paginateText(textFlow, avgCharsPerLine, maxLinesPerPage).collect { chunk ->
                    // 保存为 JSON 格式：{"page":0,"content":"..."}
                    val pageJson = "{\"page\":$pageNumber,\"content\":\"${escapeJson(chunk.content)}\"}"
                    writer.write(pageJson)
                    writer.newLine()
                    pageNumber++
                }
                
                // 同时保存总页数和最后一页的页码
                progressFile.writeText("{\"totalPages\":$pageNumber,\"lastPage\":${pageNumber - 1}}")
            }
            
            // 写入完成后，重命名为正式缓存文件
            tempFile.renameTo(cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
        }
    }
    
    /**
     * 转义 JSON 特殊字符
     */
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    /**
     * 保存阅读进度（页码）
     * @param uri 文件 URI
     * @param pageNumber 当前页码（从 0 开始）
     */
    fun saveReadingProgress(uri: String, pageNumber: Int) {
        val progressFile = File(cacheDir, "${getCacheFileName(uri)}.progress")
        progressFile.writeText(pageNumber.toString())
    }
    
    /**
     * 读取阅读进度（页码）
     * @param uri 文件 URI
     * @return 返回上次阅读的页码，如果没有则返回 0
     */
    fun readReadingProgress(uri: String): Int {
        val progressFile = File(cacheDir, "${getCacheFileName(uri)}.progress")
        if (!progressFile.exists()) return 0
        
        try {
            val json = progressFile.readText()
            val jsonObj = org.json.JSONObject(json)
            return jsonObj.optInt("lastPage", 0)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0
        }
    }
    
    /**
     * 从缓存快速加载指定页面及其后续页面（懒加载）
     * @param uri 文件 URI
     * @param startPage 起始页码
     * @param avgCharsPerLine 每行字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    fun loadPagesFromCache(
        uri: String,
        startPage: Int,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        val cacheFile = getCacheFile(uri)
        if (!cacheFile.exists()) return@flow
        
        // 使用缓冲读取器逐行读取
        cacheFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.trim().isEmpty()) return@forEach
                
                try {
                    val jsonObj = org.json.JSONObject(line)
                    val pageNumber = jsonObj.getInt("page")
                    val content = jsonObj.getString("content")
                    
                    // 只发射从 startPage 开始的页面
                    if (pageNumber >= startPage) {
                        emit(
                            TextChunk(
                                content = content,
                                chapterTitle = (pageNumber + 1).toString(),
                                isComplete = false,
                                chapterIndex = pageNumber + 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 跳过损坏的行
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 从缓存倒序加载指定页面之前的页面（前向加载）
     * @param uri 文件 URI
     * @param endPage 结束页码（不包含）
     * @param avgCharsPerLine 每行字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow（倒序发射）
     */
    fun loadPreviousPagesFromCache(
        uri: String,
        endPage: Int,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        val cacheFile = getCacheFile(uri)
        if (!cacheFile.exists()) return@flow
        
        // 先收集所有前面的页面
        val previousPages = mutableListOf<TextChunk>()
        
        // 使用缓冲读取器逐行读取
        cacheFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.trim().isEmpty()) return@forEach
                
                try {
                    val jsonObj = org.json.JSONObject(line)
                    val pageNumber = jsonObj.getInt("page")
                    val content = jsonObj.getString("content")
                    
                    // 收集 endPage 之前的页面
                    if (pageNumber < endPage) {
                        previousPages.add(
                            TextChunk(
                                content = content,
                                chapterTitle = (pageNumber + 1).toString(),
                                isComplete = false,
                                chapterIndex = pageNumber + 1
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 跳过损坏的行
                }
            }
        }
        
        // 倒序发射（从最近的往前）
        for (i in previousPages.lastIndex downTo 0) {
            emit(previousPages[i])
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 清除指定 URI 的缓存
     */
    fun clearCache(uri: String) {
        getCacheFile(uri).delete()
    }
    
    /**
     * 清除指定 URI 的阅读进度
     */
    fun clearReadingProgress(uri: String) {
        val progressFile = File(cacheDir, "${getCacheFileName(uri)}.progress")
        progressFile.delete()
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * 获取缓存大小（字节）
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
