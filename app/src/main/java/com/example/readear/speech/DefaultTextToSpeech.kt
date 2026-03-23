package com.example.readear.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TTS 语音管理器（系统默认 TTS 引擎实现）
 *
 * 职责：
 * - 管理 TextToSpeech 的生命周期
 * - 提供文本朗读功能
 * - 暴露播放状态供 UI 观察
 * - 处理 TTS 初始化和错误
 *
 * 特性：
 * - 使用 StateFlow 响应式更新状态
 * - 支持自定义 UtteranceId 追踪
 * - 完整的错误处理和日志记录
 */
class DefaultTextToSpeech(
    private val context: Context
) : TextToSpeech.OnInitListener, TextToSpeechEngine {

    companion object {
        private const val TAG = "DefaultTextToSpeech"
        private const val UTTERANCE_ID_DEFAULT = "utterance_default"
    }

    // TTS 核心组件
    private var textToSpeech: TextToSpeech? = null

    // 状态流（使用 asStateFlow 避免外部修改）
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPlayDone = MutableStateFlow(false)
    override val isPlayDone: StateFlow<Boolean> = _isPlayDone.asStateFlow()

    private val _isTTSAvailable = MutableStateFlow(false)
    override val isTTSAvailable: StateFlow<Boolean> = _isTTSAvailable.asStateFlow()

    private val _currentText = MutableStateFlow("")
    override val currentText: StateFlow<String> = _currentText.asStateFlow()

    // 状态回调（可选，方便传统用法）
    override var onTTSInitComplete: (() -> Unit)? = null
    override var onTTSError: ((String) -> Unit)? = null

    // 初始化标记
    private var isInitialized = false

    init {
        Log.d(TAG, "开始初始化 TTS")
        initializeTTS()
    }

    /**
     * 初始化 TTS 引擎
     */
    private fun initializeTTS() {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS 初始化异常：${e.message}", e)
            //handleTTSError("TTS 初始化失败：${e.message}")
        }
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "TTS 初始化状态：$status")

        if (status == TextToSpeech.SUCCESS) {
            handleTTSInitSuccess()
        } else {
            handleTTSInitError(status)
        }

        isInitialized = true
    }

    /**
     * 处理 TTS 初始化成功
     */
    private fun handleTTSInitSuccess() {
        Log.d(TAG, "TTS 初始化成功")
        _isTTSAvailable.value = true

        // 设置语言（可选，使用默认语言）
        setDefaultLanguage()

        // 设置播放监听器
        setupUtteranceProgressListener()

        // 触发回调
        onTTSInitComplete?.invoke()
    }

    /**
     * 设置默认语言
     */
    private fun setDefaultLanguage() {
        try {
            val defaultLocale = Locale.getDefault()
            val result = textToSpeech?.setLanguage(defaultLocale)

            if (result != null) {
                when {
                    result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.w(TAG, "默认语言不支持：$defaultLocale")
                        // 尝试使用英语作为备用
                        textToSpeech?.setLanguage(Locale.US)
                    }

                    result == TextToSpeech.LANG_AVAILABLE ||
                            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                        Log.d(TAG, "语言设置成功：$defaultLocale")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置语言失败：${e.message}", e)
        }
    }

    /**
     * 设置语音进度监听器
     */
    private fun setupUtteranceProgressListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS 开始播放：$utteranceId")
                _isPlayDone.value = false
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS 播放完成：$utteranceId")
                _isPlayDone.value = true
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS 播放错误：$utteranceId")
                _isSpeaking.value = false
                handleTTSError("播放错误：$utteranceId")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                Log.d(TAG, "TTS 停止播放：$utteranceId, 中断：$interrupted")
                _isSpeaking.value = false
            }
        })
    }

    /**
     * 处理 TTS 初始化错误
     */
    private fun handleTTSInitError(status: Int) {
        val errorMessage = getErrorMessage(status)
        Log.e(TAG, "TTS 初始化失败：$errorMessage (状态码：$status)")
        _isTTSAvailable.value = false
        //handleTTSError(errorMessage)
    }

    /**
     * 获取错误消息
     */
    private fun getErrorMessage(status: Int): String {
        return when (status) {
            TextToSpeech.ERROR -> "TTS 通用错误"

            // API 21+ 支持的错误码
            0x0001 -> "TTS 忙" // ERROR_BUSY
            0x0002 -> "客户端错误" // ERROR_CLIENT  
            0x0003 -> "无效请求" // ERROR_INVALID_REQUEST
            0x0004 -> "资源不足" // ERROR_NO_RESOURCE
            0x0005 -> "输出错误" // ERROR_OUTPUT
            0x0006 -> "服务器错误" // ERROR_SERVER

            // API 29+ 支持的错误码（使用数值判断）
            0x0007 -> "TTS 引擎未安装完成" // ERROR_NOT_INSTALLED_YET
            0x0008 -> "会话忙" // ERROR_SESSION_BUSY
            0x0009 -> "系统级错误" // ERROR_SYSTEM_LEVEL
            0x000A -> "超时" // ERROR_TIMEOUT

            else -> "未知错误：$status"
        }
    }

    /**
     * 处理 TTS 错误
     */
    private fun handleTTSError(message: String) {
        onTTSError?.invoke(message)
    }

    /**
     * 播放文本
     *
     * @param text 要播放的文本内容
     * @param utteranceId 语音 ID，用于追踪播放状态，默认为 null
     * @return 是否成功开始播放
     */
    override fun playText(
        text: String,
        utteranceId: String?
    ): Boolean {

        val queueMode = TextToSpeech.QUEUE_FLUSH

        // 检查 TTS 是否可用
        if (!isTTSAvailable.value) {
            Log.w(TAG, "TTS 不可用，请检查设置")
            return false
        }

        // 检查文本是否为空
        if (text.isBlank()) {
            Log.w(TAG, "播放内容为空")
            return false
        }

        try {
            val id = utteranceId ?: UTTERANCE_ID_DEFAULT
            _currentText.value = text

            Log.d(TAG, "开始播放文本 [ID: $id]：${text.length} 字符")

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            }

            val result = textToSpeech?.speak(text, queueMode, params, id)

            return when (result) {
                TextToSpeech.SUCCESS -> true
                null -> {
                    Log.e(TAG, "播放失败：TTS 未初始化")
                    false
                }

                else -> {
                    val errorMessage = getErrorMessage(result)
                    Log.e(TAG, "播放失败：$errorMessage (错误码：$result)")
                    false
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "播放异常：${e.message}", e)
            handleTTSError("播放异常：${e.message}")
            return false
        }
    }

    /**
     * 停止播放
     */
    override fun stopSpeaking() {
        try {
            Log.d(TAG, "停止播放")
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "停止播放异常：${e.message}", e)
        }
    }


    /**
     * 检查是否正在说话
     */
    override fun isCurrentlySpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    /**
     * 设置语速
     *
     * @param speechRate 语速，0.5f 为慢速，1.0f 为正常，2.0f 为快速
     */
    override fun setSpeechRate(speechRate: Float) {
        try {
            textToSpeech?.setSpeechRate(speechRate.coerceIn(0.1f, 4.0f))
            Log.d(TAG, "语速设置为：$speechRate")
        } catch (e: Exception) {
            Log.e(TAG, "设置语速失败：${e.message}", e)
        }
    }

    /**
     * 设置音调
     *
     * @param pitch 音调，0.5f 为低音，1.0f 为正常，2.0f 为高音
     */
    override fun setPitch(pitch: Float) {
        try {
            textToSpeech?.setPitch(pitch.coerceIn(0.1f, 2.0f))
            Log.d(TAG, "音调设置为：$pitch")
        } catch (e: Exception) {
            Log.e(TAG, "设置音调失败：${e.message}", e)
        }
    }

    /**
     * 释放资源
     */
    override fun release() {
        Log.d(TAG, "释放 TTS 资源")
        try {
            stopSpeaking()
            textToSpeech?.shutdown()
            textToSpeech = null
            _isTTSAvailable.value = false
            _isSpeaking.value = false
            _isPlayDone.value = false
            _currentText.value = ""
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "释放资源异常：${e.message}", e)
        }
    }

    /**
     * 重新初始化 TTS
     */
    override fun reinitialize() {
        Log.d(TAG, "重新初始化 TTS")
        release()
        initializeTTS()
    }

    /**
     * 打开 TTS 设置页面
     */
    override fun openTTSSettings() {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "已打开 TTS 设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "无法打开 TTS 设置：${e.message}", e)
        }
    }
}
