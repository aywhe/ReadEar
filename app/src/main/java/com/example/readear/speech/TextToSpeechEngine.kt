package com.example.readear.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * TTS 引擎通用接口
 * 
 * 定义所有 TTS 引擎必须实现的方法
 * 便于扩展不同的 TTS 实现（系统 TTS、讯飞、百度等）
 */
interface TextToSpeechEngine {
    
    /**
     * 播放文本
     * @param text 要播放的文本内容
     * @param utteranceId 语音 ID
     * @return 是否成功开始播放
     */
    fun playText(
        text: String,
        utteranceId: String? = null
    ): Boolean
    
    /**
     * 停止播放
     */
    fun stopSpeaking()
    
    /**
     * 检查是否正在说话
     */
    fun isCurrentlySpeaking(): Boolean
    
    /**
     * 设置语速
     * @param speechRate 语速，0.5f 为慢速，1.0f 为正常，2.0f 为快速
     */
    fun setSpeechRate(speechRate: Float)
    
    /**
     * 设置音调
     * @param pitch 音调，0.5f 为低音，1.0f 为正常，2.0f 为高音
     */
    fun setPitch(pitch: Float)
    
    /**
     * 释放资源
     */
    fun release()
    
    /**
     * 重新初始化
     */
    fun reinitialize()

    /**
     * 打开 TTS 设置界面
     */
    fun openTTSSettings()
    
    // ==================== 状态流属性（用于响应式 UI）====================
    
    /**
     * 是否正在说话（StateFlow）
     */
    val isSpeaking: StateFlow<Boolean>
    
    /**
     * 播放是否完成（StateFlow）
     */
    val isPlayDone: StateFlow<Boolean>
    
    /**
     * TTS 是否可用（StateFlow）
     */
    val isTTSAvailable: StateFlow<Boolean>
    
    /**
     * 当前文本（StateFlow）
     */
    val currentText: StateFlow<String>
    
    /**
     * TTS 初始化完成回调
     */
    var onTTSInitComplete: (() -> Unit)?
    
    /**
     * TTS 错误回调
     */
    var onTTSError: ((String) -> Unit)?
}
