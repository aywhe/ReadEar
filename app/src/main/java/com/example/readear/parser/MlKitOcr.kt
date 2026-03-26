package com.example.readear.parser

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MlKitOcr(private val context: Context) : OcrEngine {
    companion object {
        private const val TAG = "MlKitOcrEngine"
    }

    // 支持中文和拉丁文的双语识别器
    private var chineseTextRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private var latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private var isInitialized = false

    init {
        isInitialized = true
        Log.d(TAG, "ML Kit OCR 引擎初始化完成")
    }

    override fun recognizeText(bitmap: Bitmap): String? {
        if (!isInitialized || bitmap.isRecycled) {
            Log.e(TAG, "OCR 引擎未初始化或图片已回收")
            return null
        }

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            // 先尝试使用中文识别器（中文识别器也支持拉丁文）
            return chineseTextRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    //Log.d(TAG, "识别成功：${visionText.text}")
                }
                .addOnFailureListener { e ->
                    //Log.e(TAG, "识别失败", e)
                }
                .let { task ->
                    // 同步获取结果（在实际使用中建议使用异步）
                    try {
                        Tasks.await(task).text.also { result ->
                            result.ifEmpty {
                                // 如果中文识别器没有结果，尝试拉丁文识别器
                                val latinTask = latinTextRecognizer.process(inputImage)
                                Tasks.await(latinTask).text
                            }
                        }
                    } catch (e: Exception) {
                        //Log.e(TAG, "获取识别结果异常", e)
                        null
                    }
                }
        } catch (e: Exception) {
            //Log.e(TAG, "OCR 识别过程异常", e)
            return null
        }
    }

    override fun release() {
        try {
            chineseTextRecognizer.close()
            latinTextRecognizer.close()
            isInitialized = false
            Log.d(TAG, "ML Kit OCR 引擎已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放 OCR 引擎异常", e)
        }
    }

    override fun reinitialize(): Boolean {
        if (!isInitialized) {
            try {
                chineseTextRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                latinTextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                isInitialized = true
                Log.d(TAG, "ML Kit OCR 引擎重新初始化成功")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit OCR 引擎重新初始化失败", e)
                return false
            }
        }
        return true
    }

    override fun isOcrAvailable(): Boolean {
        return isInitialized
    }
}
