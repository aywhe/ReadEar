package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.poi.xwpf.usermodel.XWPFDocument

/**
 * Word 文件文本提取器
 *
 * 支持格式：
 * - .docx (Office Open XML)
 * - 部分支持 .doc (需要额外配置)
 */
class WordExtractor(private val context: Context) : TextExtractor {

    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                XWPFDocument(inputStream).use { document ->
                    // 1. 提取段落文本
                    document.paragraphs.forEach { paragraph ->
                        val text = paragraph.text
                        if (text.isNotBlank()) {
                            emit(text)
                        }
                    }

                    // 2. 提取表格中的文本
                    document.tables.forEach { table ->
                        table.rows.forEach { row ->
                            // 修复：使用 getTableCells() 方法而不是 cells 属性
                            row.tableCells.forEach { cell ->
                                cell.paragraphs.forEach { paragraph ->
                                    val text = paragraph.text
                                    if (text.isNotBlank()) {
                                        emit(text)
                                    }
                                }
                            }
                        }
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
