package com.example.readear

import android.content.Context

/**
 * Word 文件文本提取器
 * 注意：目前仅支持简单的文本读取，完整的 Word 格式支持需要 Apache POI 库
 */
class WordExtractor(context: Context) : BaseTextExtractor(context)
