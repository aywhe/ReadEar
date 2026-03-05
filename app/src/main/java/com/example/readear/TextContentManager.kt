package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 文本内容加载管理器
 * 负责统一管理文本的提取和缓存，自动判断是从源文件提取还是从缓存加载
 */
class TextContentManager(private val context: Context) {
    
    private val cacheManager = TextCacheManager(context)
    
    /**
     * 加载文本内容（自动判断是否使用缓存）
     * @param uri 文件 URI
     * @param fileType 文件类型
     * @param avgCharsPerLine 每行字符数
     * @param maxLinesPerPage 每页最大行数
     * @return 返回分页后的文本块 Flow
     */
    fun loadTextContent(
        uri: Uri,
        fileType: FileType,
        avgCharsPerLine: Int,
        maxLinesPerPage: Int
    ): Flow<TextChunk> = flow {
        val uriString = uri.toString()
        
        // 优先尝试从缓存加载
        if (cacheManager.hasCache(uriString)) {
            // 有缓存，直接从缓存读取
            cacheManager.readFromCache(uriString, avgCharsPerLine, maxLinesPerPage)
                .collect { chunk ->
                    emit(chunk)
                }
        } else {
            // 没有缓存，从源文件提取并保存缓存
            val extractor = TextExtractorFactory.getExtractor(context, fileType)
            val rawTextFlow = extractor.extractTextRaw(uri)
            
            // 异步保存到缓存（不阻塞当前流程）
            GlobalScope.launch(Dispatchers.IO) {
                cacheManager.saveToCache(uriString, rawTextFlow)
            }
            
            // 同时，直接分页显示
            val paginationProcessor = TextPaginationProcessor()
            paginationProcessor.paginateText(rawTextFlow, avgCharsPerLine, maxLinesPerPage)
                .collect { chunk ->
                    emit(chunk)
                }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 清除指定文件的缓存
     */
    fun clearCache(uri: String) {
        cacheManager.clearCache(uri)
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAllCache() {
        cacheManager.clearAllCache()
    }
}
