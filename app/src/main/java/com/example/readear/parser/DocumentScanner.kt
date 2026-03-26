package com.example.readear.parser

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class OCREngineType {
    PaddleOCRV5
}
class DocumentScanner(private val ocrEngineType: OCREngineType) {
    private var ocrEngine: OcrEngine? = null
    val scope = CoroutineScope(Dispatchers.IO)


    fun reInitialize() {
        // 协程运行，避免阻塞
        scope.launch {
            reInitializeInternal()
        }
    }
    private fun reInitializeInternal() {
        if (ocrEngine != null) {
            ocrEngine!!.release()
            ocrEngine = null
        }
        when (ocrEngineType) {
            OCREngineType.PaddleOCRV5 -> {
                ocrEngine = PaddleOcrV5()
                if (!ocrEngine!!.reinitialize()) {
                    ocrEngine = null
                    Log.e("OCR", "PaddleOCRV5 initialization failed")
                }
                else {
                    Log.d("OCR", "PaddleOCRV5 initialized")
                }
            }
        }
    }

    fun doOcr(bitmap: Bitmap): String? {
        if(ocrEngine == null || !ocrEngine!!.hasInitialized()){
            return null
        }
        return ocrEngine!!.recognizeText(bitmap)
    }

    fun release() {
        ocrEngine?.release()
        ocrEngine = null
    }
}
