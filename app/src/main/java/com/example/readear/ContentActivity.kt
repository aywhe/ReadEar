package com.example.readear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.readear.ui.theme.ReadEarTheme

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
        
        val fileUri = fileUriString.toUri()
        ensurePersistedUriPermission(fileUri)
        
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
    
    /**
     * 确保已获取 URI 的持久化读取权限
     * 如果已存在则跳过，避免重复申请
     * 
     * @param uri 需要访问的文件 URI
     */
    private fun ensurePersistedUriPermission(uri: Uri) {
        try {
            val hasPermission = contentResolver.persistedUriPermissions.any { 
                it.uri == uri && it.isReadPermission 
            }
            
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
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("请重新设计内容区域")
        }
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
 */
@Composable
private fun rememberLayoutParameters(
    context: Context,
    density: Density
): LayoutParameters {
    return remember(context) {
        calculateLayoutParameters(context)
    }
}

/**
 * 计算页面布局参数的独立函数
 */
fun calculateLayoutParameters(context: Context): LayoutParameters {
    val displayMetrics = context.resources.displayMetrics
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    
    val defaultFontSize = 16f
    val baseLineHeight = 24f
    val lineHeightScale = 1.5f
    val charAspectRatio = 1.0f
    val charSpacingFactor = 1.1f
    
    val fontSizePx = defaultFontSize * displayMetrics.density
    val baseLineHeightPx = baseLineHeight * displayMetrics.density
    val scaledLineHeightPx = baseLineHeightPx * lineHeightScale
    
    val horizontalPaddingPx = 16f * displayMetrics.density * 2
    
    val availableWidth = screenWidthPx - horizontalPaddingPx
    
    val topPaddingPx = (56f + 24f) * displayMetrics.density
    val bottomPaddingPx = 16f * displayMetrics.density
    val availableHeight = screenHeightPx - topPaddingPx - bottomPaddingPx
    
    val avgCharWidth = fontSizePx * charAspectRatio * charSpacingFactor
    val avgCharsPerLine = (availableWidth / avgCharWidth).toInt()
    
    val maxLinesPerPage = (availableHeight / scaledLineHeightPx).toInt().coerceIn(10, 35)
    
    return LayoutParameters(
        avgCharsPerLine = avgCharsPerLine,
        maxLinesPerPage = maxLinesPerPage
    )
}

@Composable
fun PageContent(text: String) {
    val defaultFontSize = 16.sp
    val baseLineHeight = 24.sp
    val scaledLineHeight = baseLineHeight * 1.5f
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = text,
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
