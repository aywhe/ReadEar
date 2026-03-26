package com.example.readear.parser

import android.graphics.Bitmap

interface OcrEngine {

    fun recognizeText(bitmap: Bitmap): String?

    fun release()

    fun reinitialize(): Boolean

    fun hasInitialized(): Boolean
}