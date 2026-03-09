package com.example.readear

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局播放按钮状态管理器（Application 级别）
 */
class GlobalPlayButtonStateManager {
    
    companion object {
        @Volatile
        private var INSTANCE: GlobalPlayButtonStateManager? = null
        
        fun getInstance(): GlobalPlayButtonStateManager {
            return INSTANCE ?: synchronized(this) {
                val instance = GlobalPlayButtonStateManager()
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentBookId = MutableStateFlow<String?>(null)
    val currentBookId: StateFlow<String?> = _currentBookId.asStateFlow()
    
    private val _currentPageNumber = MutableStateFlow(-1)
    val currentPageNumber: StateFlow<Int> = _currentPageNumber.asStateFlow()
    
    /**
     * 更新播放状态
     */
    fun updatePlaybackState(bookId: String?, pageNumber: Int, playing: Boolean) {
        _currentBookId.value = bookId
        _currentPageNumber.value = pageNumber
        _isPlaying.value = playing
        Log.d("GlobalPlayButtonState", "🔄 更新状态：书籍=$bookId, 页码=$pageNumber, 播放中=$playing")
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        _isPlaying.value = false
        // 不清除 bookId 和 pageNumber，保留进度信息
    }
}

/**
 * 全局悬浮播放按钮（Composable 版本）
 * 
 * 在 Activity 的 Compose UI 中使用
 * 
 * 特性：
 * - 可拖动
 * - 显示播放/暂停状态
 * - 点击控制播放
 */
@Composable
fun GlobalPlayButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 获取全局状态管理器
    val stateManager = remember(context) {
        GlobalPlayButtonStateManager.getInstance()
    }
    
    // 收集状态
    val isPlaying by stateManager.isPlaying.collectAsState()
    val currentBookId by stateManager.currentBookId.collectAsState(null)
    val currentPageNumber by stateManager.currentPageNumber.collectAsState(-1)
    
    FloatingActionButton(
        onClick = { 
            handleClick(context, isPlaying, currentBookId, currentPageNumber)
        },
        containerColor = if (isPlaying) 
            androidx.compose.material3.MaterialTheme.colorScheme.secondary 
        else 
            androidx.compose.material3.MaterialTheme.colorScheme.primary,
        contentColor = androidx.compose.ui.graphics.Color.White,
        modifier = modifier
            .size(56.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // TODO: 实现拖动逻辑（需要父布局支持）
                    }
                )
            },
        shape = CircleShape
    ) {
        Text(
            text = if (isPlaying) "⏸️" else "▶️",
            fontSize = 24.sp
        )
    }
}

private fun handleClick(
    context: Context,
    isPlaying: Boolean,
    currentBookId: String?,
    currentPageNumber: Int
) {
    Log.d("GlobalPlayButton", "🎵 点击播放按钮，当前状态：播放中=$isPlaying, 书籍=$currentBookId, 页码=$currentPageNumber")
    
    val serviceIntent = Intent(context, AudioPlaybackService::class.java)
    
    when {
        // 正在播放 -> 暂停
        isPlaying && currentBookId != null -> {
            Log.d("GlobalPlayButton", "⏸️ 暂停播放")
            serviceIntent.action = AudioPlaybackService.ACTION_PAUSE
            ContextCompat.startForegroundService(context, serviceIntent)
        }
        // 有播放进度 -> 继续播放
        currentBookId != null && currentPageNumber >= 0 -> {
            Log.d("GlobalPlayButton", "▶️ 继续播放：书籍=$currentBookId, 页码=${currentPageNumber + 1}")
            serviceIntent.action = AudioPlaybackService.ACTION_PLAY
            serviceIntent.putExtra(AudioPlaybackService.EXTRA_BOOK_ID, currentBookId)
            serviceIntent.putExtra(AudioPlaybackService.EXTRA_PAGE_NUMBER, currentPageNumber)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
        else -> {
            Log.d("GlobalPlayButton", "⚠️ 没有可播放的内容")
            // 没反应
        }
    }
}
