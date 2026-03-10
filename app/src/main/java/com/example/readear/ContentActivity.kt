package com.example.readear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readear.ui.theme.ReadEarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import com.example.readear.parser.TextChunk
import com.example.readear.parser.TextManager
import com.example.readear.parser.DefaultTextToSpeech
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ContentActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_TYPE = "extra_file_type"
    }

    // TTS 相关
    private var speechManager: DefaultTextToSpeech? = null
    private var isSpeaking by mutableStateOf(false)
    private var isPlayDone by mutableStateOf(false)
    private var currentTextToSpeak = ""
    private var isTTSAvailable by mutableStateOf(false)

    // 当前阅读页码
    private var currentPageNumber: Int = 0
    
    // 定时器广播接收器
    private val timerStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.readear.STOP_SPEAKING") {
                stopSpeaking()
                Log.d("ContentActivity", "收到定时器广播，已停止播放")
                Toast.makeText(this@ContentActivity, "定时时间到，已停止播放", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 注册广播接收器
        LocalBroadcastManager.getInstance(this).registerReceiver(
            timerStopReceiver,
            IntentFilter("com.example.readear.STOP_SPEAKING")
        )

        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "未知文件"
        val fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.TXT.name)

        // 获取 URI 并请求持久化读取权限
        val fileUri = fileUriString.toUri()
        ensurePersistedUriPermission(fileUri)

        // 初始化 TTS 管理器
        speechManager = DefaultTextToSpeech.getInstance(this)
        
        // 监听 TTS 状态变化
        observeTTSState()

        // 延迟初始化 TTS，避免在 onCreate 中立即初始化导致失败
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(100) // 延迟 100ms 初始化
            Log.d("ContentActivity", "TTS 已就绪")
        }

        setContent {
            ReadEarTheme {
                ContentScreen(
                    uri = fileUri,
                    fileName = fileName,
                    fileType = fileType,
                    onNavigateBack = { finish() },
                    onPageChanged = { page -> currentPageNumber = page },
                    onPlayText = { text -> playText(text) },
                    onStopSpeaking = { stopSpeaking() },
                    isSpeaking = isSpeaking,
                    isPlayDone = isPlayDone
                )
            }
        }
    }

    private fun observeTTSState() {
        lifecycleScope.launch {
            launch {
                speechManager?.isSpeaking?.collect { speaking ->
                    isSpeaking = speaking
                    Log.d("ContentActivity", "TTS 状态更新：isSpeaking=$speaking")
                }
            }
            launch {
                speechManager?.isPlayDone?.collect { done ->
                    isPlayDone = done
                    Log.d("ContentActivity", "TTS 状态更新：isPlayDone=$done")
                }
            }
            launch {
                speechManager?.isTTSAvailable?.collect { available ->
                    isTTSAvailable = available
                    Log.d("ContentActivity", "TTS 状态更新：isTTSAvailable=$available")
                }
            }
        }
        
        // 设置错误回调
        speechManager?.onTTSError = { error ->
            Log.e("ContentActivity", "TTS 错误：$error")
            showTTSSettingsDialog()
        }
    }

    private fun playText(text: String) {
        if (text.isNotEmpty()) {
            currentTextToSpeak = text
            Log.d("ContentActivity", "开始播放文本：${text.length} 字符")
            
            val success = speechManager?.playText(text)
            if (success == false) {
                Log.w("ContentActivity", "播放失败，TTS 可能未就绪")
                Toast.makeText(this, "TTS 未就绪，请稍后再试", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("ContentActivity", "播放内容为空")
        }
    }

    private fun stopSpeaking() {
        speechManager?.stopSpeaking()
    }

    /**
     * 确保已获取 URI 的持久化读取权限
     * 如果已存在则跳过，避免重复申请
     *
     * @param uri 需要访问的文件 URI
     */
    private fun ensurePersistedUriPermission(uri: Uri) {
        try {
            // 检查是否已经有持久化权限
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }

            // 如果没有权限，才申请新的
            if (!hasPermission) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        //saveReadingProgress()
        // 播放不自动暂停，只能手动暂停直至播放结束
        //if (isSpeaking) {
        //    stopSpeaking()
        //}
    }

    override fun onStop() {
        super.onStop()
        //saveReadingProgress()
        // 播放不自动暂停，只能手动暂停直至播放结束
        //if (isSpeaking) {
        //    stopSpeaking()
        //}
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放 URI 权限（可选，根据需求决定）
        saveReadingProgress()
        releasePersistedUriPermission()
        
        // 释放 TTS 资源（注意：不释放单例，只重置状态）
        speechManager?.stopSpeaking()
        speechManager = null
        isSpeaking = false
        isTTSAvailable = false
        
        // 注销广播接收器
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerStopReceiver)
    }

    private fun saveReadingProgress() {
        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: return
        
        if (currentPageNumber >= 0) {
            Log.d("ContentActivity", "保存进度：页码 ${currentPageNumber + 1}, URI: $fileUriString")
            
            // 使用协程在后台线程执行保存操作
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // 方案 1：在 Activity 中创建独立的 TextManager 实例，用于保存进度
                    val textManager = TextManager(applicationContext)
                    textManager.saveReadingProgress(fileUriString, currentPageNumber)
                    Log.d("ContentActivity", "进度保存成功")
                } catch (e: Exception) {
                    Log.e("ContentActivity", "保存进度失败：${e.message}", e)
                }
            }
        } else {
            Log.w("ContentActivity", "跳过保存：currentPageNumber = $currentPageNumber")
        }
    }
    /**
     * 释放持久化 URI 权限（如果需要的话）
     */
    private fun releasePersistedUriPermission() {
        try {
            val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: return
            val uri = fileUriString.toUri()
            
            // 检查是否有持久化权限
            val hasPermission = contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            
            if (hasPermission) {
                contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showTTSSettingsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("TTS 引擎不可用")
            .setMessage("请安装或启用文本转语音引擎。\n\n路径：设置 > 辅助功能 > 文字转语音输出")
            .setPositiveButton("前往设置") { _, _ ->
                speechManager?.openTTSSettings(this)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContentScreen(
    uri: Uri,
    fileName: String,
    fileType: FileType,
    onNavigateBack: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
    onPlayText: (String) -> Unit = {},
    onStopSpeaking: () -> Unit = {},
    isSpeaking: Boolean = false,
    isPlayDone: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleScope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalPages by remember { mutableIntStateOf(0) }

    // 上次阅读进度
    var lastReadingPage by remember { mutableStateOf<Int?>(null) }
    var hasRestoredLastReading by remember { mutableStateOf(false) }

    // 新增：是否正在初始化
    var isInitializing by remember { mutableStateOf(true) }

    // 全屏模式
    var isFullScreen by remember { mutableStateOf(false) }

    // 当前播放语音的页面索引
    var currentSpeakingPage by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalPages }
    )

    var showJumpDialog by remember { mutableStateOf(false) }

    val layoutParams by remember(context) {
        derivedStateOf {
            calculateLayoutParameters(context)
        }
    }

    // TextManager 实例（使用 remember 避免重复创建）
    val textManager = remember(context) { TextManager(context.applicationContext as Context) }


    // 启动初始化：同步内存数据并恢复上次阅读位置
    LaunchedEffect(uri, fileType) {
        try {
            // 1. 在后台启动协程执行 syncLoadPages，不阻塞当前协程
            launch(Dispatchers.IO) {
                textManager.startLoadPages(
                    uri,
                    fileType,
                    layoutParams.avgCharsPerLine,
                    layoutParams.maxLinesPerPage
                )
            }
        } catch (e: Exception) {
            errorMessage = "初始化失败：${e.message}"
            isInitializing = false
        }
            // 1. 尝试获取阅读进度（设置超时，避免无限等待）
        var retryCount = 0
        val maxProgressRetry = 10
        var hasLoadedProgress = false
        while (lastReadingPage == null && retryCount < maxProgressRetry) {
            val progress = textManager.getLastReadPageNumber(uri.toString())
            if (progress != null) {
                lastReadingPage = progress
                hasLoadedProgress = true
            } else {
                delay(10)
                retryCount++
            }
        }

        // 2. 如果没有获取到进度，设置为 0（从第一页开始）
        if (!hasLoadedProgress) {
            lastReadingPage = 0
        }

        // 标记初始化完成
        isInitializing = false
    }
    // 任务 2：持续更新 totalPages（后台运行）
    DisposableEffect(Unit) {
        val pageCountJob = lifecycleScope.launch {
            // 持续检查，直到书籍加载完成
            while (true) {
                delay(10)

                val pagesCount = textManager.getPagesCount(uri.toString())
                if (pagesCount != null && pagesCount > 0) {
                    totalPages = pagesCount
                }

                // 完成后退出
                if (textManager.isBookCompleted(uri.toString())) {
                    Log.d("ContentActivity", "书籍页数获取完成，总页数：$totalPages")
                    break
                }
                delay(10)
            }
        }

        onDispose {
            pageCountJob.cancel()
        }
    }

    // 监听页面变化，加载当前页面和预加载后续页面
    LaunchedEffect(pagerState.currentPage) {
        if (textManager.hasBook(uri.toString())) {
            onPageChanged(pagerState.currentPage)

            // 取消预加载
            // 预加载后续 1 页
//            lifecycleScope.launch {
//                textManager.PagesRange(uri.toString(), pagerState.currentPage, pagerState.currentPage + 1)
//            }
            // 不用预加载前一页，即使回退就从数据库中获取把
        }
    }

    LaunchedEffect(totalPages, lastReadingPage) {
        // 恢复上次阅读位置（如果有）
        if ((!hasRestoredLastReading) && totalPages > 0 && lastReadingPage != null) {
            pagerState.scrollToPage(lastReadingPage!!)
            hasRestoredLastReading = true
        }
    }
    LaunchedEffect(isPlayDone){
        if(isPlayDone){
            // 播放完成后自动跳转到下一页
            val nextPageContent = textManager.getPage(uri.toString(), currentSpeakingPage + 1)
            if (nextPageContent != null) {
                currentSpeakingPage++
                onPlayText(nextPageContent.content)
            }
        }
    }

    LaunchedEffect(isSpeaking){
        if(isSpeaking){
            if(currentSpeakingPage != pagerState.currentPage){
                pagerState.scrollToPage(currentSpeakingPage)
            }
        }else{
            // 这样会导致自动播放时跳转错误
            // 停止播放时更新当前播放页面为当前显示的页面
            // currentSpeakingPage = pagerState.currentPage
        }
    }

    DisposableEffect(isFullScreen){
        // 切换隐藏和显示ContentScreen的状态栏
            if (isFullScreen) {
                // 隐藏状态栏
                androidx.core.view.WindowInsetsControllerCompat(
                    (context as ComponentActivity).window,
                    (context as ComponentActivity).window.decorView
                ).hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            } else {
                // 显示状态栏
                androidx.core.view.WindowInsetsControllerCompat(
                    (context as ComponentActivity).window,
                    (context as ComponentActivity).window.decorView
                ).show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            }

        onDispose {
            // 离开页面时恢复默认状态
            androidx.core.view.WindowInsetsControllerCompat(
                (context as ComponentActivity).window,
                (context as ComponentActivity).window.decorView
            ).show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }
    
    // 定时保存进度：每 60 秒自动保存一次（修改为 1 分钟）
    DisposableEffect(Unit) {
        val autoSaveJob = lifecycleScope.launch {
            while (true) {
                delay(60_000)
                if (textManager.hasBook(uri.toString())) {
                    Log.d("ContentActivity", "保存进度：${pagerState.currentPage}")
                    textManager.saveReadingProgress(uri.toString(), pagerState.currentPage)
                }
            }
        }

        onDispose {
            // 取消定时任务并立即保存一次
            autoSaveJob.cancel()
            lifecycleScope.launch {
                Log.d("ContentActivity", "保存进度：${pagerState.currentPage}")
                textManager.saveReadingProgress(uri.toString(), pagerState.currentPage)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // 根据全屏模式决定是否显示 TopAppBar
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        // 新增：直接显示页码信息，点击跳转到指定页面
                        TextButton(
                            onClick = { showJumpDialog = true },
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}/$totalPages",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 有错误
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "未知错误",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            val intent = Intent(context, ContentActivity::class.java).apply {
                                putExtra(ContentActivity.EXTRA_FILE_URI, uri.toString())
                                putExtra(ContentActivity.EXTRA_FILE_NAME, fileName)
                                putExtra(ContentActivity.EXTRA_FILE_TYPE, fileType.name)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("重试")
                        }
                    }
                }

                // 正在初始化
                isInitializing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在加载内容...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 正常显示内容（修改条件：只要 lastReadingPage != null 即可）
                lastReadingPage != null -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 10//Int.MAX_VALUE
                    ) { page ->
                        // 尝试获取页面内容
                        val chunk = remember(page) {
                            mutableStateOf<TextChunk?>(null)
                        }

                        // 如果为 null，等待并重新尝试获取
                        LaunchedEffect(page) {
                            var retryCount = 0
                            val maxRetryCount = 50
                            
                            Log.d("ContentActivity", "开始加载页面 $page")
                            
                            while (chunk.value == null && retryCount < maxRetryCount) {
                                val startTime = System.currentTimeMillis()
                                val pageContent = textManager.getPage(uri.toString(), page)
                                val loadTime = System.currentTimeMillis() - startTime

                                if (pageContent != null) {
                                    chunk.value = pageContent
                                    Log.d("ContentActivity", "✓ 成功加载页面 $page，耗时：${loadTime}ms")
                                    break
                                } else {
                                    Log.d("ContentActivity", "✗ 未获取到页面 $page (retry: ${retryCount + 1}/$maxRetryCount)，耗时：${loadTime}ms")
                                }
                                delay(30)
                                retryCount++
                            }

                            // 如果超时仍未获取到，使用空文本块
                            if (chunk.value == null) {
                                Log.e("ContentActivity", "⚠ 超时未获取到页面 $page，创建空文本块")
                                chunk.value = TextChunk("", false, page)
                            }
                        }
                        chunk.value?.let { displayChunk ->
                            // 显示内容（确保不为 null）
                            PageContent(
                                chunk = displayChunk,
                                onDoubleTap = {
                                    isFullScreen = !isFullScreen
                                    Log.d("ContentActivity", "全屏模式变量 isFullScreen = $isFullScreen")
                                },
                                onLongPress = { selectedText ->
                                    showTextSelection(context, selectedText)
                                }
                            )
                        }
                    }
                    
                    // 添加可拖动的播放按钮
                    DraggablePlayButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        isSpeaking = isSpeaking,
                        onClick = {
                            lifecycleScope.launch {
                                val currentPageContent = textManager.getPage(uri.toString(), pagerState.currentPage)
                                if (currentPageContent != null) {
                                    // 更新当前播放页面为当前显示的页面
                                    currentSpeakingPage = pagerState.currentPage
                                    onPlayText(currentPageContent.content)
                                } else {
                                    Toast.makeText(context, "当前页面内容为空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onStop = { onStopSpeaking() }
                    )
                }
                else -> {
                        Text(
                            text = "文件夹没有内容",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                }
            }
        }
    }

    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = pagerState.currentPage + 1,
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onConfirm = { targetPage ->
                lifecycleScope.launch {
                    pagerState.animateScrollToPage(targetPage - 1)
                }
                showJumpDialog = false
            }
        )
    }
}

@Composable
fun PageContent(
    chunk: TextChunk,
    onDoubleTap: () -> Unit,
    onLongPress: (String) -> Unit
) {
    val defaultFontSize = 16.sp // 固定字体大小
    val baseLineHeight = 24.sp // 固定行高
    val scaledLineHeight = baseLineHeight * 1.5f // 1.5 倍行距

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { offset ->
                        onLongPress(chunk.content)
                    }
                )
            },
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = chunk.content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = defaultFontSize,
                lineHeight = baseLineHeight,
                color = Color.Black
            ),
            lineHeight = scaledLineHeight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun showTextSelection(context: Context, text: String) {
    //Toast.makeText(context, "长按文本：$text", Toast.LENGTH_SHORT).show()
    // 后续可以实现复制、分享等功能

    // 复制到剪贴板，并提示已复制文本
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("Selected Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "已复制文本", Toast.LENGTH_SHORT).show()

}

/**
 * 页面布局参数数据类
 */
data class LayoutParameters(
    val avgCharsPerLine: Int,
    val maxLinesPerPage: Int
)

/**
 * 计算并记住页面布局参数
 * 使用屏幕尺寸和密度自动计算每行字符数和每页行数
 *
 * @param context Context
 * @param density Density
 * @return LayoutParameters 包含 avgCharsPerLine 和 maxLinesPerPage
 */
@Composable
private fun rememberLayoutParameters(
    context: Context,
    density: androidx.compose.ui.unit.Density
): LayoutParameters {
    // 使用 derivedStateOf 创建响应式计算结果
    return remember(context) {
        calculateLayoutParameters(context)
    }
}

/**
 * 计算页面布局参数的独立函数
 * 可以在任何地方调用，无需 Compose 环境
 *
 * @param context Context
 * @return LayoutParameters 包含 avgCharsPerLine 和 maxLinesPerPage
 */
fun calculateLayoutParameters(context: Context): LayoutParameters {
    // 页面布局参数的默认值（基于标准字体大小和行高）
    val defaultFontSize = 16f // sp
    val baseLineHeight = 24f // sp
    val lineHeightScale = 1.5f // 行距倍数
    val charAspectRatio = 1.0f // 汉字宽高比
    val charSpacingFactor = 1.1f // 字间距系数

    val displayMetrics = context.resources.displayMetrics
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val screenWidthPx = displayMetrics.widthPixels.toFloat()

    // 转换为像素（使用 displayMetrics 而不是 density）
    val fontSizePx = defaultFontSize * displayMetrics.density
    val baseLineHeightPx = baseLineHeight * displayMetrics.density
    val scaledLineHeightPx = baseLineHeightPx * lineHeightScale

    // 计算左右 padding（16dp * 2）
    val horizontalPaddingPx = 16f * displayMetrics.density * 2

    // 计算可用区域
    val availableWidth = screenWidthPx - horizontalPaddingPx

    // 估算顶部 padding（AppBar 高度约 56dp + status bar）
    val topPaddingPx = (56f + 24f) * displayMetrics.density
    val bottomPaddingPx = 16f * displayMetrics.density
    val availableHeight = screenHeightPx - topPaddingPx - bottomPaddingPx

    // 1. 计算每行平均字符数
    val avgCharWidth = fontSizePx * charAspectRatio * charSpacingFactor
    val avgCharsPerLine = (availableWidth / avgCharWidth).toInt()

    // 2. 计算每页最大行数（限制在合理范围内）
    val maxLinesPerPage = (availableHeight / scaledLineHeightPx).toInt().coerceIn(10, 35)

    return LayoutParameters(
        avgCharsPerLine = avgCharsPerLine,
        maxLinesPerPage = maxLinesPerPage
    )
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pageNumber by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "跳转到页面")
        },
        text = {
            Column {
                Text(
                    text = "当前在第 $currentPage 页，共 $totalPages 页",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pageNumber,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            pageNumber = input
                        }
                    },
                    label = { Text("页码") },
                    placeholder = { Text("请输入页码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = pageNumber.toIntOrNull()?.let { it < 1 || it > totalPages } ?: false
                )
                if (pageNumber.toIntOrNull()?.let { it < 1 || it > totalPages } == true) {
                    Text(
                        text = "请输入 1 到 $totalPages 之间的数字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    pageNumber.toIntOrNull()?.let { page ->
                        if (page in 1..totalPages) {
                            onConfirm(page)
                        }
                    }
                },
                enabled = pageNumber.toIntOrNull()?.let { it in 1..totalPages } == true
            ) {
                Text("跳转")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun DraggablePlayButton(
    modifier: Modifier = Modifier,
    isSpeaking: Boolean = false,
    onClick: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    var offset by remember { mutableStateOf(IntOffset.Zero) }

    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = {
                if (isSpeaking) {
                    onStop()
                } else {
                    onClick()
                }
            },
            containerColor = if (isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .offset { offset }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += IntOffset(
                                dragAmount.x.toInt(),
                                dragAmount.y.toInt()
                            )
                        }
                    )
                }
                .size(56.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isSpeaking) "暂停" else "播放",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
