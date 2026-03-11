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
import java.io.ByteArrayInputStream
import java.io.SequenceInputStream

/**
 * TXT 文件文本提取器
 */
class TxtExtractor(private val context: Context) : TextExtractor {
    
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 检测文件编码
                val detectedCharset = detectFileEncoding(inputStream)
                
                val combinedStream = SequenceInputStream(
                    ByteArrayInputStream(detectedData),
                    inputStream
                )
                
                BufferedReader(InputStreamReader(combinedStream, detectedCharset)).use { reader ->
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
    
    private var detectedData = ByteArray(0)
    
    /**
     * 检测文件编码
     * 支持 UTF-8、GBK、GB2312、Big5 等常见中文编码
     */
    private fun detectFileEncoding(inputStream: java.io.InputStream): Charset {
        val detector = UniversalDetector(null)
        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        
        try {
            var bytesRead = inputStream.read(buffer)
            var totalBytesRead = 0
            
            while (bytesRead > 0 && !detector.isDone()) {
                detector.handleData(buffer, 0, bytesRead)
                if (!detector.isDone()) {
                    bytesRead = inputStream.read(buffer)
                }
            }
            
            detector.dataEnd()
            
            // 获取检测到的编码
            val charsetName = detector.detectedCharset ?: "UTF-8"
            
            if (bytesRead > 0) {
                detectedData = buffer.copyOf(bytesRead)
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
