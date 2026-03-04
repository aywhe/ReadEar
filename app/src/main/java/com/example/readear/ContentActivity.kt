package com.example.readear

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
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.readear.ui.theme.ReadEarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ContentActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_FILE_URI = "extra_file_uri"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_FILE_TYPE = "extra_file_type"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val fileUriString = intent.getStringExtra(EXTRA_FILE_URI) ?: ""
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "未知文件"
        val fileType = FileType.valueOf(intent.getStringExtra(EXTRA_FILE_TYPE) ?: FileType.TXT.name)
        
        // 获取 URI 并请求临时读取权限
        val fileUri = Uri.parse(fileUriString)
        try {
            contentResolver.takePersistableUriPermission(
                fileUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setContent {
            ReadEarTheme {
                ContentScreen(
                    uri = fileUri,
                    fileName = fileName,
                    fileType = fileType,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    uri: Uri,
    fileName: String,
    fileType: FileType,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // 状态管理
    var textChunks by remember { mutableStateOf(listOf<TextChunk>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalExtractedChars by remember { mutableStateOf(0) }
    
    val lifecycleScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { textChunks.size })
    
    // 控制跳转对话框的显示
    var showJumpDialog by remember { mutableStateOf(false) }
    
    // 页面布局参数 - 每页能显示的字符数（由后台根据行数自动计算）
    // 基于固定的字体大小（16sp）和行高（24sp * 1.5）
    val defaultFontSize = 16.sp
    val baseLineHeight = 24.sp
    val scaledLineHeight = baseLineHeight * 1.5f
    
    // 计算每页能显示的行数和每行字符数
    var avgCharsPerLine by remember { mutableIntStateOf(0) }
    var maxLinesPerPage by remember { mutableIntStateOf(0) }
    var isParamsInitialized by remember { mutableStateOf(false) }
    
    // 启动文本提取（一次性异步提取所有文本）
    LaunchedEffect(uri, fileType) {
        isLoading = true
        errorMessage = null
        textChunks = listOf()
        totalExtractedChars = 0
        
        // 确保有 URI 访问权限
        try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { it.uri == uri }
            if (!hasPermission) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // 忽略
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val extractor = TextExtractorFactory.getExtractor(context, fileType)
            val paginationProcessor = TextPaginationProcessor()
            
            lifecycleScope.launch {
                // 模块 1：提取原始文本流
                val rawTextFlow = extractor.extractTextRaw(uri)
                
                // 模块 2：处理文本流并分页
                paginationProcessor.paginateText(rawTextFlow, avgCharsPerLine, maxLinesPerPage)
                    .collectLatest { chunk ->
                        textChunks = textChunks + chunk
                        totalExtractedChars += chunk.content.length
                        isLoading = false
                        
                        if (textChunks.size == 1) {
                            lifecycleScope.launch {
                                pagerState.scrollToPage(0)
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "提取失败：${e.message}"
            isLoading = false
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
        // 精确计算可用高度和宽度（排除顶部 AppBar、底部和左右 padding）
        val topPadding = innerPadding.calculateTopPadding().value * density.density
        val bottomPadding = innerPadding.calculateBottomPadding().value * density.density
        val horizontalPadding = 16.dp.value * 2 * density.density
        
        val screenHeightPx = context.resources.displayMetrics.heightPixels.toFloat()
        val screenWidthPx = context.resources.displayMetrics.widthPixels.toFloat()
        
        val availableHeight = screenHeightPx - topPadding - bottomPadding
        val availableWidth = screenWidthPx - horizontalPadding
        
        // 如果还没初始化参数，现在计算
        if (!isParamsInitialized) {
            // 1. 计算每行平均字符数（左右留安全距离）
            val fontSizePx = with(density) { defaultFontSize.toPx() }
            val charAspectRatio = 1.0f // 汉字宽高比约 1:1
            val avgCharWidth = fontSizePx * charAspectRatio * 0.9f // 考虑字间距，留 10% 余量
            avgCharsPerLine = (availableWidth / avgCharWidth).toInt()
            
            // 2. 计算每页最大行数（上下留安全距离，已排除标题栏）
            val scaledLineHeightPx = with(density) { scaledLineHeight.toPx() }
            maxLinesPerPage = (availableHeight / scaledLineHeightPx).toInt().coerceIn(10, 35)
            
            isParamsInitialized = true
        }
        
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
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            PageContent(chunk = textChunks[page])
                        }
                        
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
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
fun JumpToPageDialog(
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
