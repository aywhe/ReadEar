package com.example.readear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.readear.ui.theme.ReadEarTheme
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
            // 尝试获取持久权限（仅当 URI 带有持久权限标志时才成功）
            contentResolver.takePersistableUriPermission(
                fileUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // 如果没有持久权限，至少确保有临时访问权限
            // 这在使用 ACTION_OPEN_DOCUMENT 时通常会自动授予
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
    
    // 状态管理
    var textChunks by remember { mutableStateOf(listOf<TextChunk>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalExtractedChars by remember { mutableStateOf(0) }
    
    val lifecycleScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { textChunks.size })
    
    // 启动文本提取
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
                // 尝试获取持久权限
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // 忽略，可能没有持久权限标志
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        try {
            val extractor = TextExtractorFactory.getExtractor(context, fileType)
            
            lifecycleScope.launch {
                extractor.extractText(uri, chunkSize = 3000).collectLatest { chunk ->
                    // 将新提取的文本块添加到列表（每块约 3000 字，适合一页显示）
                    textChunks = textChunks + chunk
                    totalExtractedChars += chunk.content.length
                    isLoading = false
                    
                    // 如果是第一个章节，自动跳转到第一页
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
                    // 显示当前页码和总页数
                    if (textChunks.isNotEmpty()) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${textChunks.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 16.dp)
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
                    // 初始加载状态
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                errorMessage != null -> {
                    // 错误状态
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
                            // 重新加载 - 通过启动新的 Activity 实现
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
                    // 空内容
                    Text(
                        text = "文件中没有内容",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                else -> {
                    // 使用 HorizontalPager 实现左右翻页效果
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f)
                        ) { page ->
                            PageContent(chunk = textChunks[page])
                        }
                        
                        // 底部加载指示器
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
}

@Composable
fun PageContent(chunk: TextChunk) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = chunk.content,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
