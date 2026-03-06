package com.example.readear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readear.ui.theme.ReadEarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ContentActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_TYPE = "extra_file_type"
    }
    
    // 当前阅读页码
    private var currentPageNumber: Int = 0
    
    // 用于保存进度的协程作用域
    private val progressScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "未知文件"
        val fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.TXT.name)
        
        // 获取 URI 并请求持久化读取权限
        val fileUri = fileUriString.toUri()
        ensurePersistedUriPermission(fileUri)
        
        setContent {
            ReadEarTheme {
                ContentScreen(
                    uri = fileUri,
                    fileName = fileName,
                    fileType = fileType,
                    onNavigateBack = { finish() },
                    onPageChanged = { page -> currentPageNumber = page }
                )
            }
        }
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
    }
    
    override fun onStop() {
        super.onStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消协程作用域
        progressScope.cancel()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    uri: Uri,
    fileName: String,
    fileType: FileType,
    onNavigateBack: () -> Unit,
    onPageChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleScope = rememberCoroutineScope()
    
    // 状态管理
    var textChunks by remember { mutableStateOf(listOf<TextChunk>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalExtractedChars by remember { mutableStateOf(0) }
    
    val pagerState = rememberPagerState(pageCount = { textChunks.size })
    
    // 控制跳转对话框的显示
    var showJumpDialog by remember { mutableStateOf(false) }
    
    // 计算页面布局参数（使用 derivedStateOf 避免重复计算）
    val layoutParams by remember(context) {
        derivedStateOf {
            calculateLayoutParameters(context)
        }
    }
    
    // TextManager 实例
    val textManager = remember(context) { TextManager(context) }
    
    // 启动文本加载（后台自动判断是否使用缓存）
    LaunchedEffect(uri, fileType) {
        isLoading = true
        errorMessage = null
        
        try {
            // 1. 尝试加载内容（后台异步）
            when (val result = textManager.loadBookContent(uri.toString(), fileName, fileType)) {
                is TextManager.LoadResult.Success -> {
                    textChunks = result.pages
                    totalExtractedChars = result.pages.sumOf { it.content.length }
                    
                    // 2. 恢复上次阅读位置
                    val lastReadPage = textManager.getLastReadPage(uri.toString())
                    if (lastReadPage > 0 && lastReadPage < result.pages.size) {
                        pagerState.scrollToPage(lastReadPage)
                    }
                    
                    isLoading = false
                }
                is TextManager.LoadResult.NotExist -> {
                    // TODO: 数据库和缓存都没有，需要提取文本
                    errorMessage = "文件内容尚未提取，请稍后再试"
                    isLoading = false
                }
                is TextManager.LoadResult.Error -> {
                    errorMessage = result.message
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            errorMessage = "加载失败：${e.message}"
            isLoading = false
        }
    }
    
    // 监听页面变化，预加载后续页面
    LaunchedEffect(pagerState.currentPage) {
        if (textChunks.isNotEmpty()) {
            onPageChanged(pagerState.currentPage)
            
            // 预加载后续 5 页
            lifecycleScope.launch {
                textManager.preloadNextPages(uri.toString(), pagerState.currentPage, 5)
            }
        }
    }
    
    // 保存阅读进度（在 Activity 生命周期中调用）
    DisposableEffect(Unit) {
        onDispose {
            lifecycleScope.launch {
                textManager.saveReadingProgress(uri.toString(), pagerState.currentPage)
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                    if (textChunks.isNotEmpty()) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${textChunks.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable { showJumpDialog = true }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading && textChunks.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
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
                
                textChunks.isEmpty() -> {
                    Text(
                        text = "文件中没有内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                            beyondViewportPageCount = textChunks.size
                        ) { page ->
                            PageContent(chunk = textChunks[page])
                        }
                        
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 监听页面变化，通知 Activity（由 Activity 在退出时保存）
                    LaunchedEffect(pagerState.currentPage) {
                        if (textChunks.isNotEmpty()) {
                            onPageChanged(pagerState.currentPage)
                        }
                    }
                }
            }
        }
    }
    
    if (showJumpDialog) {
        JumpToPageDialog(
            currentPage = pagerState.currentPage + 1,
            totalPages = textChunks.size,
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
fun PageContent(chunk: TextChunk) {
    val defaultFontSize = 16.sp // 固定字体大小
    val baseLineHeight = 24.sp // 固定行高
    val scaledLineHeight = baseLineHeight * 1.5f // 1.5 倍行距
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = chunk.content,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = defaultFontSize,
                lineHeight = baseLineHeight
            ),
            lineHeight = scaledLineHeight,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
