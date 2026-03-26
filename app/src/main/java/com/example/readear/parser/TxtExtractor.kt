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
    
    override fun extractTextRaw(uri: Uri, startPosition: Int): Flow<TextExtractionResult> = flow {
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
                    var position = 0
                    var nextLine: String? = reader.readLine() // 预读一行
                    
                    while (nextLine != null) {
                        line = nextLine
                        nextLine = reader.readLine() // 读取下一行
                        
                        // 如果没有下一行了，说明这是文件的最后一行
                        val isFileLastLine = nextLine == null
                        
                        if(position >= startPosition) {
                            // 只有在 startPos 之后的最后一行才标记为完成
                            val isCompleted = isFileLastLine
                            
                            emit(
                                TextExtractionResult(
                                    content = line,
                                    isCompleted = isCompleted,
                                    position = position
                                )
                            )
                        }
                        position++
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
