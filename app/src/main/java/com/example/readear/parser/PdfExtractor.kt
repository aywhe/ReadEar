package com.example.readear.parser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PDF 文件文本提取器
 */
class PdfExtractor(private val context: Context) : TextExtractor {
    private val TAG = "PdfExtractor"
    val pageTextOcrThreld = 30

    private val docScanner = DocumentScanner(OCREngineType.PaddleOCRV5)
    val scope = CoroutineScope(Dispatchers.IO)

    init{
        // 后台运行初始化
        scope.launch(Dispatchers.IO) {
            docScanner.reInitialize()
        }
    }
    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    val pdfRenderer = PDFRenderer(document)

                    // 获取总页数
                    val totalPages = document.numberOfPages

                    // 按页读取文本
                    for (page in 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page

                        var text = stripper.getText(document)

                        if (text.length < pageTextOcrThreld) {
                            try {
                                val bitmap = pdfRenderer.renderImage(page - 1)
                                val ocrText = docScanner.doOcr(bitmap)
                                bitmap.recycle()
                                if (ocrText?.isNotBlank() ?: false) {
                                    text = ocrText
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        // 发送文本
                        emit(text)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }.flowOn(Dispatchers.IO)
}
