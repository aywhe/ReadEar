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
     * 保存文本到缓存
     * @param uri 文件 URI
     * @param textFlow 原始文本流
     */
    suspend fun saveToCache(uri: String, textFlow: Flow<String>) {
        val cacheFile = getCacheFile(uri)
        
        // 使用临时文件，避免写入过程中被读取
        val tempFile = File(cacheDir, "${getCacheFileName(uri)}.tmp")
        
        try {
            tempFile.bufferedWriter().use { writer ->
                textFlow.collect { text ->
                    writer.write(text)
                    writer.newLine()
                }
            }
            
            // 写入完成后，重命名为正式缓存文件
            tempFile.renameTo(cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
        }
    }
    
    /**
     * 清除指定 URI 的缓存
     */
    fun clearCache(uri: String) {
        getCacheFile(uri).delete()
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
