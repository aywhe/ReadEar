package com.example.readear.parser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.equationl.ncnnandroidppocr.OCR
import com.equationl.ncnnandroidppocr.bean.Device
import com.equationl.ncnnandroidppocr.bean.DrawModel
import com.equationl.ncnnandroidppocr.bean.ImageSize
import com.equationl.ncnnandroidppocr.bean.ModelType

class PaddleOcrV5(private val context: Context) : OcrEngine{
    private var isInitialized = false
    private val TAG = "PaddleOcrV5"
    private val ocr = OCR()
    private fun initialize(): Boolean {
        Log.d(TAG, "ocr init")
        isInitialized = ocr.initModelFromAssert(context.assets, ModelType.Mobile, ImageSize.Size720, Device.CPU)
        return isInitialized
    }
    override fun recognizeText(bitmap: Bitmap): String? {
        if (!isInitialized || bitmap.isRecycled) {
            Log.e(TAG, "OCR 引擎未初始化或图片已回收")
            return null
        }
        
        val result = ocr.detectBitmap(bitmap, drawModel = DrawModel.None)
        if (result != null && result.text.isNotEmpty()) {
            val simpleText = result.text
            val inferenceTime = result.inferenceTime
            val outputRawResult = result.textLines

            Log.d(TAG, "识别文字=$simpleText 识别时间=$inferenceTime ms")
            
            val stringBuilder = StringBuilder()
            outputRawResult.forEach { ocrResultModel ->
                stringBuilder.append(ocrResultModel.text).append("\n")
            }
            
            return stringBuilder.toString()
        }
        
        Log.w(TAG, "未识别到文本")
        return null
    }

    override fun release() {
        ocr.release()
        isInitialized = false
        Log.d(TAG, "ocr release")
    }

    override fun reinitialize(): Boolean {
        if (isInitialized) {
            release()
        }
        return initialize()
    }
    override fun isOcrAvailable(): Boolean {
        return isInitialized
    }
}