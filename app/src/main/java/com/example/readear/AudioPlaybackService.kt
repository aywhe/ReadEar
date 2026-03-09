package com.example.readear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

/**
 * 音频播放服务
 * 
 * 后台服务，负责：
 * - TTS 文本朗读
 * - 播放状态管理（播放/暂停）
 * - 自动连续播放下一页
 * - 跨 Activity 播放控制
 * 
 * 使用前台服务确保在后台继续播放
 */
class AudioPlaybackService : Service(), TextToSpeech.OnInitListener {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var isPlaying = false
    private var currentBookId: String? = null
    private var currentPageNumber: Int = -1
    
    private val playbackScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackJob: Job? = null
    
    // 播放进度管理器
    private lateinit var progressManager: PlaybackProgressManager
    private lateinit var textManager: TextManager
    
    // 监听当前 Activity 是否在前台
    companion object {
        private const val TAG = "AudioPlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audio_playback_channel"
        
        // 单例引用
        var instance: AudioPlaybackService? = null
            private set
        
        // 播放状态监听器
        var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
        var onPageChanged: ((String, Int) -> Unit)? = null // (bookId, pageNumber)
        
        fun isPlaying(): Boolean = instance?.isPlaying ?: false
        
        fun getCurrentBookId(): String? = instance?.currentBookId
        
        fun getCurrentPageNumber(): Int = instance?.currentPageNumber ?: -1
        
        // 获取全局状态管理器
        private val stateManager by lazy { GlobalPlayButtonStateManager.getInstance() }
        
        const val ACTION_PLAY = "com.example.readear.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.readear.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.readear.ACTION_STOP"
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_PAGE_NUMBER = "extra_page_number"
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        progressManager = PlaybackProgressManager.getInstance(this)
        textManager = TextManager(applicationContext)
        createNotificationChannel()
        initializeTTS()
        Log.d(TAG, "✅ 音频播放服务已创建")
    }
    
    override fun onDestroy() {
        stopPlayback()
        textToSpeech?.shutdown()
        instance = null
        Log.d(TAG, "❌ 音频播放服务已销毁")
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
                val pageNumber = intent.getIntExtra(EXTRA_PAGE_NUMBER, -1)
                if (bookId != null && pageNumber >= 0) {
                    startPlayback(bookId, pageNumber)
                } else if (currentBookId != null && currentPageNumber >= 0) {
                    resumePlayback()
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        
        // 作为前台服务运行，确保后台播放
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY
    }
    
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(this, this)
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CHINA)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ TTS 语言不支持")
                isInitialized = false
            } else {
                isInitialized = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "🔊 开始朗读：$utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "✅ 朗读完成：$utteranceId")
                        // 播放完成后自动播放下一页
                        if (isPlaying) {
                            playNextPage()
                        }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "❌ 朗读错误：$utteranceId")
                    }
                })
                Log.d(TAG, "✅ TTS 初始化成功")
            }
        } else {
            Log.e(TAG, "❌ TTS 初始化失败")
            isInitialized = false
        }
    }
    
    /**
     * 开始播放指定页面
     */
    fun startPlayback(bookId: String, pageNumber: Int) {
        if (!isInitialized) {
            Log.e(TAG, "❌ TTS 未初始化，无法播放")
            return
        }
        
        // 如果正在播放其他书或页面，先停止
        if (isPlaying && (currentBookId != bookId || currentPageNumber != pageNumber)) {
            textToSpeech?.stop()
        }
        
        currentBookId = bookId
        currentPageNumber = pageNumber
        isPlaying = true
        
        playbackJob = playbackScope.launch {
            try {
                val pageContent = textManager.getPage(bookId, pageNumber)
                if (pageContent != null && pageContent.content.isNotEmpty()) {
                    Log.d(TAG, "▶️ 开始播放：书籍=$bookId, 页码=${pageNumber + 1}")
                    
                    // 保存播放进度
                    progressManager.savePlaybackProgress(bookId, pageNumber, isPlaying = true)
                    
                    // 通知 UI 更新
                    onPlaybackStateChanged?.invoke(true)
                    onPageChanged?.invoke(bookId, pageNumber)
                    
                    // 更新全局状态
                    stateManager.updatePlaybackState(bookId, pageNumber, playing = true)
                    
                    // 使用 TTS 朗读
                    val utteranceId = "page_${pageNumber}"
                    textToSpeech?.speak(pageContent.content, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                    
                    // 更新通知
                    updateNotification()
                } else {
                    Log.e(TAG, "❌ 页面内容为空：书籍=$bookId, 页码=$pageNumber")
                    // 尝试播放下一页
                    playNextPage()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 播放失败：${e.message}", e)
                isPlaying = false
                onPlaybackStateChanged?.invoke(false)
            }
        }
    }
    
    /**
     * 恢复播放
     */
    fun resumePlayback() {
        if (currentBookId != null && currentPageNumber >= 0) {
            startPlayback(currentBookId!!, currentPageNumber)
        }
    }
    
    /**
     * 暂停播放
     */
    fun pausePlayback() {
        Log.d(TAG, "⏸️ 暂停播放")
        isPlaying = false
        textToSpeech?.stop()
        playbackJob?.cancel()
        
        // 保存暂停状态
        currentBookId?.let { bookId ->
            playbackScope.launch {
                progressManager.savePlaybackProgress(bookId, currentPageNumber, isPlaying = false)
            }
        }
        
        onPlaybackStateChanged?.invoke(false)
        updateNotification()
        
        // 更新全局状态
        currentBookId?.let { bookId ->
            stateManager.updatePlaybackState(bookId, currentPageNumber, playing = false)
        } ?: stateManager.reset()
    }
    
    /**
     * 停止播放
     */
    fun stopPlayback() {
        Log.d(TAG, "⏹️ 停止播放")
        isPlaying = false
        textToSpeech?.stop()
        playbackJob?.cancel()
        currentBookId = null
        currentPageNumber = -1
        
        onPlaybackStateChanged?.invoke(false)
    }
    
    /**
     * 播放下一页
     */
    private fun playNextPage() {
        if (currentBookId == null || currentPageNumber < 0) return
        
        playbackJob = playbackScope.launch {
            try {
                val totalPages = textManager.getPagesCount(currentBookId!!)
                if (totalPages != null && currentPageNumber + 1 < totalPages) {
                    currentPageNumber++
                    Log.d(TAG, "➡️ 播放下一页：书籍=$currentBookId, 页码=${currentPageNumber + 1}")
                    
                    // 保存新进度
                    progressManager.savePlaybackProgress(currentBookId!!, currentPageNumber, isPlaying = true)
                    onPageChanged?.invoke(currentBookId!!, currentPageNumber)
                    
                    // 如果在 ContentActivity 中，切换到播放的页面
                    // （这个逻辑由 ContentActivity 自己监听处理）
                    
                    // 朗读新页面
                    val pageContent = textManager.getPage(currentBookId!!, currentPageNumber)
                    if (pageContent != null && pageContent.content.isNotEmpty()) {
                        val utteranceId = "page_${currentPageNumber}"
                        textToSpeech?.speak(pageContent.content, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        updateNotification()
                    } else {
                        // 如果下一页也为空，继续尝试下一页
                        playNextPage()
                    }
                } else {
                    // 书本播放结束
                    Log.d(TAG, "📚 书本播放结束")
                    stopPlayback()
                    updateNotification()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 播放下一页失败：${e.message}", e)
            }
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "文本朗读播放控制"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在播放")
            .setContentText(getContentText())
            .setSmallIcon(R.drawable.ic_pdf) // 可以换成更合适的图标
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    /**
     * 获取通知内容文本
     */
    private fun getContentText(): String {
        return if (currentBookId != null && currentPageNumber >= 0) {
            "第 ${currentPageNumber + 1} 页"
        } else {
            "已暂停"
        }
    }
    
    // Note: constants already defined in first companion object
}
