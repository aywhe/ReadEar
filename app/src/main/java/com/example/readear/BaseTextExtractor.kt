package com.example.readear

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 文本提取器基类
 * 提供通用的文件读取逻辑，子类只需处理特定的解析逻辑
 */
abstract class BaseTextExtractor(private val context: Context) : TextExtractor {
    
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // 调用子类的方法处理每一行（允许子类自定义）
                        processLine(this, line!!)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 处理单行文本
     * 子类可以重写此方法来自定义处理逻辑
     * @param flowCollector Flow 收集器
     * @param line 原始文本行
     */
    protected open suspend fun processLine(flowCollector: FlowCollector<String>, line: String) {
        // 默认直接发出原始行
        flowCollector.emit(line)
    }
}
