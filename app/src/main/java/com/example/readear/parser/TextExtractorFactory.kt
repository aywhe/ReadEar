package com.example.readear.parser

import android.content.Context
import android.net.Uri
import com.example.readear.FileType

/**
 * 文本提取器工厂
 * 根据文件类型创建对应的提取器
 * 
 * @param context Context
 */
class TextExtractorFactory(private val context: Context) {

    /**
     * 根据文件类型获取对应的文本提取器
     */
    fun getExtractor(fileType: FileType): TextExtractor {
        return when (fileType) {
            FileType.TXT -> TxtExtractor(context)
            FileType.PDF -> PdfExtractor(context)
            FileType.DOCX -> WordExtractor(context)  // 新增
            FileType.EPUB -> EpubExtractor(context)
            else -> throw IllegalArgumentException("Unsupported file type: $fileType")
        }
    }
    /**
     * 根据文件 URI 获取对应的文本提取器
     */
    fun getExtractor(uri: Uri): TextExtractor {
        val fileType = FileTypeUtils.fromUri(context, uri)
        return getExtractor(fileType)
    }
}
