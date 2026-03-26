package com.example.readear.parser

import android.content.Context
import android.graphics.Bitmap
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
    val pageTextOcrThreshold = 30

    private val docScanner = DocumentScanner(context, OCREngineType.PaddleOCRV5)
    val scope = CoroutineScope(Dispatchers.IO)

    override fun extractTextRaw(uri: Uri): Flow<String> = flow {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context)
        }
        // 后台运行初始化
        scope.launch(Dispatchers.IO) {
            docScanner.reInitialize()
        }
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val stripper = PDFTextStripper()
                    val pdfRenderer = PDFRenderer(document)

                    // 获取总页数
                    val totalPages = document.numberOfPages

                    // 按页读取文本，pdfbox的PDFTextStripper索引要从1开始
                    for (page in 1..totalPages) {
                        stripper.startPage = page
                        stripper.endPage = page

                        var text = stripper.getText(document)

                        if (text.length < pageTextOcrThreshold) {
                            var bitmap: Bitmap? = null
                            try {
                                // pdfRenderer的索引1-base与PDFTextStripper的索引的0-base不同
                                bitmap = pdfRenderer.renderImage(page - 1)
                                val ocrText = docScanner.doOcr(bitmap)
                                if (ocrText?.isNotBlank() ?: false) {
                                    text = ocrText
                                }
                                else{
                                    // 使用原来提取的文本，不做修改
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            finally {
                                bitmap?.recycle()
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
        finally {
            docScanner.release()
        }
    }.flowOn(Dispatchers.IO)
}
