package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/**
 * PDF 文件文本提取器
 */
class PdfExtractor(private val context: Context) : TextExtractor {

    override fun extractTextRaw(uri: Uri, startPosition: Int): Flow<TextExtractionResult> = flow {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()

                    // 获取总页数
                    val totalPages = document.numberOfPages
                    val pdfLineCombiner = PdfLineCombiner()

                    // 按页读取文本，pdfbox的PDFTextStripper索引要从1开始
                    for (page in startPosition + 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page

                        val text = stripper.getText(document)

                        if(text.isNotBlank()) {
                            pdfLineCombiner.feed(text)
                            emit(TextExtractionResult(
                                pdfLineCombiner.combineTextLines(text),
                                page == totalPages,
                                page - 1
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}

class PdfLineCombiner() {
    var lineCharsThreshold: Float? = null
    var benchLines: MutableList<String> = mutableListOf()
    val benchLinesThreshold = 200

    fun feed(text: String){
        if(text.isBlank()) {
            return
        }
        if(lineCharsThreshold == null) {
            if (benchLines.size < benchLinesThreshold) { // 收集基准行数据，直到达到阈值
                benchLines.addAll(text.lines())
            } else {
                val lineLengths = benchLines.map { it.length }.sorted()
                lineCharsThreshold = lineLengths[lineLengths.size / 2] * 0.9f
                benchLines.clear() // 清空基准行数据，后续不再使用
            }
        }
    }

    fun combineTextLines(text: String): String {
        if(lineCharsThreshold == null) {
            return text
        }
        val lines = text.lines()
        val combinedText = StringBuilder()
        for (line in lines) {
            if (line.length >= (lineCharsThreshold?: Float.MAX_VALUE)) {
                // 如果行长度大于等于阈值，认为是同一段落的一部分，直接连接
                combinedText.append(line)
            } else {
                // 否则认为是新段落，换行连接
                combinedText.append(line).append("\n")
            }
        }
        return combinedText.toString().trim()
    }
}
