package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFFooter
import org.apache.poi.xwpf.usermodel.XWPFHeader
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable

/**
 * Word 文件文本提取器
 *
 * 支持格式：
 * - .docx (Office Open XML)
 * - 部分支持 .doc (需要额外配置)
 */
class WordExtractor(private val context: Context) : TextExtractor {

    override fun extractTextRaw(uri: Uri, startPosition: Int): Flow<TextExtractionResult> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                XWPFDocument(inputStream).use { document ->
                    // 按文档顺序获取所有 body 元素（段落和表格）
                    val bodyElements = document.bodyElements
                    
                    var position = 0
                    val totalElements = bodyElements.size
                    
                    for (index in startPosition until totalElements) {
                        val element = bodyElements[index]
                        val isLastElement = index == totalElements - 1
                        var emitText = ""
                        when (element) {
                            is XWPFParagraph -> {
                                emitText += element.text
                            }
                            is XWPFTable -> {
                                // 提取表格中的文本，按行遍历
                                val rows = element.rows
                                for (rowIndex in 0 until rows.size) {
                                    val row = rows[rowIndex]
                                    val cells = row.tableCells
                                    val rowText = buildString {
                                        for (cellIndex in 0 until cells.size) {
                                            val cell = cells[cellIndex]
                                            append(cell.text.trim())
                                            if (cellIndex < cells.size - 1) {
                                                append("\t") // 单元格之间用制表符分隔
                                            }
                                        }
                                    }
                                    
                                    if (rowText.isNotBlank()) {
                                        emitText += rowText + "\n" // 行之间换行
                                    }
                                }
                            }
                            // 页眉页脚
                            is XWPFFooter->{}
                            is XWPFHeader->{}
                        }
                        if (emitText.isNotBlank()) {
                            emit(TextExtractionResult(
                                content = emitText,
                                isCompleted = isLastElement,
                                position = position
                            ))
                        }
                        position++
                    }
                    // 3. 提取页眉页脚（可选）
//                    document.headerList.forEach { header ->
//                        header.paragraphs.forEach { paragraph ->
//                            val text = paragraph.text
//                            if (text.isNotBlank()) {
//                                emit(text)
//                            }
//                        }
//                    }
//
//                    document.footerList.forEach { footer ->
//                        footer.paragraphs.forEach { paragraph ->
//                            val text = paragraph.text
//                            if (text.isNotBlank()) {
//                                emit(text)
//                            }
//                        }
//                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
