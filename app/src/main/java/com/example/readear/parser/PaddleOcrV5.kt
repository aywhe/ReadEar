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
        var resultText: String? = null
        val result = ocr.detectBitmap(bitmap, drawModel = DrawModel.None)  // drawModel = DrawModel.Full 表示要将识别结果绘制在 Bitmap 上返回，使用时建议设置为 DrawModel.None
        if (result != null) {
            val simpleText = result.text
            val inferenceTime = result.inferenceTime
            val outputRawResult = result.textLines

            //Log.d(TAG, "识别文字=${simpleText} 识别时间=${inferenceTime} ms")
            resultText = ""
            outputRawResult.forEachIndexed { index, ocrResultModel ->
                resultText += "${ocrResultModel.text}\n"
                //Log.d(TAG,"$index: 文字：${ocrResultModel.text}，文字方向：${ocrResultModel.orientation}；置信度：${ocrResultModel.confidence}；文字位置：${ocrResultModel.points}")
            }
        }
        return resultText
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