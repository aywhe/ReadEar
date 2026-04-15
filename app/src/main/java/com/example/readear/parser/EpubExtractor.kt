package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import nl.siegmann.epublib.domain.MediaType
import nl.siegmann.epublib.epub.EpubReader
import org.jsoup.Jsoup

/**
 * EPUB 文件文本提取器
 * 
 * 支持格式：
 * - .epub (标准 EPUB 2.0/3.0)
 */
class EpubExtractor(private val context: Context) : TextExtractor {

    override fun extractTextRaw(uri: Uri, startPosition: Int): Flow<TextExtractionResult> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 读取 EPUB 文件
                val book = EpubReader().readEpub(inputStream)
                
                // 获取所有 XHTML/HTML 资源（按阅读顺序）
                val resources = book.contents.filter { resource ->
                    resource.mediaType == MediaType.XHTML || 
                    resource.mediaType == MediaType.HTML
                }
                
                var position = 0
                val totalResources = resources.size
                
                // 从指定位置开始遍历章节
                for (index in startPosition until totalResources) {
                    val resource = resources[index]
                    val isLastResource = index == totalResources - 1
                    
                    try {
                        // 将资源内容转换为字符串
                        val htmlContent = String(resource.data, Charsets.UTF_8)
                        
                        // 使用 Jsoup 清理 HTML 标签，提取纯文本
                        val text = Jsoup.parse(htmlContent).text()
                        
                        if (text.isNotBlank()) {
                            emit(
                                TextExtractionResult(
                                    content = text,
                                    isCompleted = isLastResource,
                                    position = position
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // 跳过解析失败的章节
                        e.printStackTrace()
                    }
                    
                    position++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
