package com.example.readear.speech

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * TTS 类型枚举
 */
enum class TTSEngineType {
    DEFAULT,  // 系统默认 TTS
    CUSTOM    // 自定义 TTS 引擎（预留扩展）
}

/**
 * 用户 TTS 管理器（支持多引擎切换）
 * 
 * 职责：
 * - 根据配置选择不同的 TTS 引擎
 * - 提供统一的 TTS 接口
 * - 支持动态切换 TTS 引擎
 * 
 * 使用场景：
 * - 用户可以在设置中选择不同的 TTS 引擎（如讯飞、百度、Google 等）
 * - 不同场景使用不同的 TTS（如朗读用默认引擎，录音用高质量引擎）
 * 
 * 设计说明：
 * - 非单例模式，允许创建多个实例
 * - 实例化时传入 TTS 引擎类型，灵活配置
 * - Context 通过构造函数注入，避免内存泄漏
 */
class UserTextToSpeech(
    private val context: Context,
    private val engineType: TTSEngineType = TTSEngineType.DEFAULT
) {
    
    // TTS 引擎实例（在 init 块中初始化）
    private var textToSpeechEngine: TextToSpeechEngine? = null
    
    init {
        initialize()
    }
    private fun initialize() {
        textToSpeechEngine = when (engineType) {
            TTSEngineType.DEFAULT -> DefaultTextToSpeech(context)
            TTSEngineType.CUSTOM -> {
                // 预留自定义 TTS 引擎的扩展点
                // 未来可以在这里集成第三方 TTS（如讯飞、百度等）
                // 还没有新的其他 TTS，输出错误
                throw IllegalArgumentException("暂不支持的 TTS 引擎")
            }
        }
    }

    /**
     * 播放文本
     * @param text 要播放的文本内容
     * @param utteranceId 语音 ID
     * @return 是否成功开始播放
     */
    fun playText(
        text: String,
        utteranceId: String? = null
    ): Boolean? {
        return textToSpeechEngine?.playText(text, utteranceId)
    }

    /**
     * 停止播放
     */
    fun stopSpeaking() {
        textToSpeechEngine?.stopSpeaking()
    }

    /**
     * 检查是否正在说话
     */
    fun isCurrentlySpeaking(): Boolean? {
        return textToSpeechEngine?.isCurrentlySpeaking()
    }

    /**
     * 设置语速
     * @param speechRate 语速，0.5f 为慢速，1.0f 为正常，2.0f 为快速
     */
    fun setSpeechRate(speechRate: Float) {
        textToSpeechEngine?.setSpeechRate(speechRate)
    }

    /**
     * 设置音调
     * @param pitch 音调，0.5f 为低音，1.0f 为正常，2.0f 为高音
     */
    fun setPitch(pitch: Float) {
        textToSpeechEngine?.setPitch(pitch)
    }

    /**
     * 获取当前使用的 TTS 引擎类型
     */
    fun getEngineType(): TTSEngineType {
        return engineType
    }
    
    /**
     * 切换到其他 TTS 引擎
     * 
     * 该方法会释放当前 TTS 引擎资源，并使用新的类型重新创建引擎。
     * 
     * @param newType 要切换到的 TTS 引擎类型
     * @param context Context 用于创建新引擎
     * @return 是否切换成功
     */
    fun switchTTSEngine(newType: TTSEngineType, context: Context): Boolean {
        try {
            // 释放当前引擎资源
            release()
            
            // 重新创建新类型的引擎
            initialize()
            
            return true
        } catch (e: Exception) {
            // 切换失败，回滚到默认类型
            textToSpeechEngine = DefaultTextToSpeech(context)
            return false
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        textToSpeechEngine?.release()
        textToSpeechEngine = null
    }

    /**
     * 重新初始化 TTS
     */
    fun reinitialize() {
        textToSpeechEngine?.reinitialize()
    }

    /**
     * 打开 TTS 设置页面
     */
    fun openTTSSettings() {
        textToSpeechEngine?.openTTSSettings()
    }

    // ==================== 状态流代理（用于响应式 UI）====================

    /**
     * 是否正在说话（StateFlow）
     */
    val isSpeaking: StateFlow<Boolean>?
        get() = textToSpeechEngine?.isSpeaking

    /**
     * 播放是否完成（StateFlow）
     */
    val isPlayDone: StateFlow<Boolean>?
        get() = textToSpeechEngine?.isPlayDone

    /**
     * TTS 是否可用（StateFlow）
     */
    val isTTSAvailable: StateFlow<Boolean>?
        get() = textToSpeechEngine?.isTTSAvailable

    /**
     * 当前文本（StateFlow）
     */
    val currentText: StateFlow<String>?
        get() = textToSpeechEngine?.currentText
    
    /**
     * 设置 TTS 初始化完成回调
     */
    var onTTSInitComplete: (() -> Unit)?
        get() = textToSpeechEngine?.onTTSInitComplete
        set(value) { textToSpeechEngine?.onTTSInitComplete = value }
    
    /**
     * 设置 TTS 错误回调
     */
    var onTTSError: ((String) -> Unit)?
        get() = textToSpeechEngine?.onTTSError
        set(value) { textToSpeechEngine?.onTTSError = value }
}
