package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
                    val mimeType = resource.mediaType?.defaultExtension ?: ""
                    mimeType == ".xhtml" || mimeType == ".html" || mimeType == ".htm"
                }

                val totalResources = resources.size
                
                // 从指定位置开始遍历章节
                for (index in startPosition until totalResources) {
                    val resource = resources[index]
                    val isLastResource = index == totalResources - 1
                    
                    try {
                        // 将资源内容转换为字符串
                        val htmlContent = String(resource.data, Charsets.UTF_8)
                        
                        // 使用 Jsoup 解析 HTML，提取每个 <p> 标签的文本并根据 class 添加换行
                        val document = Jsoup.parse(htmlContent)
                        val paragraphs = document.select("p")
                        val text = paragraphs.joinToString("\n") { p ->
                            val text = p.text().prependIndent("    ")
                            
                            val className = p.className().lowercase()
                            val prefixNewlines = when {
                                className.contains("h1") || className.contains("title-1") -> "\n\n"
                                className.contains("h2") || className.contains("title-2") -> "\n"
                                className.contains("h3") || className.contains("title-3") -> "\n"
                                else -> ""
                            }
                            val suffixNewlines = when {
                                className.contains("h1") || className.contains("title-1") -> "\n"
                                className.contains("h2") || className.contains("title-2") -> "\n"
                                className.contains("h3") || className.contains("title-3") -> "\n"
                                else -> ""
                            }
                            
                            "$prefixNewlines$text$suffixNewlines"
                        }

                        if (text.isNotBlank()) {
                            emit(
                                TextExtractionResult(
                                    content = text,
                                    isCompleted = isLastResource,
                                    position = index
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // 跳过解析失败的章节
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
