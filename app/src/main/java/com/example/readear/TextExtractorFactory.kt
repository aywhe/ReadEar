package com.example.readear

import android.content.Context
import android.net.Uri

/**
 * 文本提取器工厂
 * 根据文件类型创建对应的提取器
 */
object TextExtractorFactory {
    
    /**
     * 根据文件类型获取对应的文本提取器
     */
    fun getExtractor(context: Context, fileType: FileType): TextExtractor {
        return when (fileType) {
            FileType.TXT -> TxtExtractor(context)
            FileType.WORD -> WordExtractor(context)
            FileType.PDF -> PdfExtractor(context)
            FileType.OTHER -> throw UnsupportedOperationException("不支持的文件类型")
        }
    }
}
