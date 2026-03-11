package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import org.mozilla.universalchardet.UniversalDetector

/**
 * TXT 文件文本提取器
 */
class TxtExtractor(private val context: Context) : TextExtractor {
    
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 检测文件编码
                val detectedCharset = detectFileEncoding(inputStream)
                
                // 重置输入流以便重新读取
                if (inputStream.markSupported()) {
                    inputStream.reset()
                }
                
                BufferedReader(InputStreamReader(inputStream, detectedCharset)).use { reader ->
                    var line: String?
                    
                    while (reader.readLine().also { line = it } != null) {
                        // 直接发出原始文本行
                        emit(line!!)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 检测文件编码
     * 支持 UTF-8、GBK、GB2312、Big5 等常见中文编码
     */
    private fun detectFileEncoding(inputStream: java.io.InputStream): Charset {
        val detector = UniversalDetector(null)
        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        
        try {
            // 标记输入流以便重置
            if (inputStream.markSupported()) {
                inputStream.mark(4096)
            }
            
            // 读取部分字节进行编码检测
            var bytesRead = inputStream.read(buffer)
            while (bytesRead > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, bytesRead)
                if (!detector.isDone()) {
                    bytesRead = inputStream.read(buffer)
                }
            }
            
            detector.dataEnd()
            
            // 获取检测到的编码
            val charsetName = detector.detectedCharset ?: "UTF-8"
            
            // 重置输入流
            if (inputStream.markSupported()) {
                inputStream.reset()
            }
            
            return Charset.forName(charsetName)
        } catch (e: Exception) {
            // 检测失败时返回 UTF-8
            return Charsets.UTF_8
        } finally {
            detector.reset()
        }
    }
}
