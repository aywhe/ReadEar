package com.example.readear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.readear.repository.FileRepository
import com.example.readear.ui.theme.ReadEarTheme
import kotlinx.coroutines.launch
import sh.calvin.reorderable.*
import androidx.core.net.toUri
import com.example.readear.data.BooksCache
import com.example.readear.data.CacheManager

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "file_list")

class MainActivity : ComponentActivity() {
    companion object {
        // 屏幕信息（在应用启动时获取）
        var screenDpi: Float = 0f
        var screenWidthPx: Int = 0
        var screenHeightPx: Int = 0

        // 定时器相关
        var countdownTimer: CountDownTimer? = null
        var remainingTimeMinutes by mutableStateOf<Int>(0)
    }

    private val fileBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uriList: List<Uri>? ->
        uriList?.forEach { uri ->
            // 立即获取持久 URI 权限
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 忽略异常，URI 可能没有持久权限标志
                e.printStackTrace()
            }

            val fileInfo = getFileInfoFromUri(uri)
            //Toast.makeText(this, "选择了文件：${fileInfo.fileName}", Toast.LENGTH_SHORT).show()

            val newFileItem = FileItem(
                fileName = fileInfo.fileName,
                fileType = getFileTypeFromName(fileInfo.fileName),
                fileUri = uri.toString(),
                fileSize = fileInfo.fileSize
            )

            addFileToList(newFileItem)
        }
        // 异步保存，不阻塞 UI
        fileRepository.saveFileList(fileList)
    }

    private var fileList by mutableStateOf<List<FileItem>>(emptyList())
    private lateinit var fileRepository: FileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 获取屏幕信息
        val metrics = resources.displayMetrics
        screenDpi = metrics.densityDpi.toFloat()
        screenWidthPx = metrics.widthPixels
        screenHeightPx = metrics.heightPixels

        fileRepository = FileRepository(applicationContext)

        setContent {
            ReadEarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        FileListScreen(
                            modifier = Modifier.fillMaxSize(),
                            files = fileList,
                            onDeleteFile = { file ->
                                deleteFileFromList(file)
                                // 清除对应的缓存和数据库数据
                                clearBookData(file.fileUri)
                                fileRepository.saveFileList(fileList)
                            },
                            onFileClick = { file ->
                                openContentActivity(file)
                            },
                            onMoveFile = { from, to ->
                                if (from != to) {
                                    fileList = fileList.toMutableList().apply {
                                        add(to, removeAt(from))
                                    }
                                }
                            }
                        )
                        DraggableFloatingButton(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            onClick = { openSystemFilePicker() }
                        )
                    }
                }
            }
        }

        // 异步恢复数据
        restoreFileList()
    }

    private fun openSystemFilePicker() {
        // 只允许选择 TXT、Word、PDF 文件
        val mimeTypes = arrayOf(
            "text/plain",                                    // TXT 文件
            "application/msword",                            // Word (.doc)
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // Word (.docx)
            "application/pdf"                                // PDF 文件
        )
        fileBrowserLauncher.launch(mimeTypes)
    }

    private fun addFileToList(newFile: FileItem) {
        val existingIndex = fileList.indexOfFirst { it.fileUri == newFile.fileUri }

        fileList = if (existingIndex >= 0) {
            fileList.toMutableList().apply {
                removeAt(existingIndex)
                add(newFile)  // 移动到末尾而不是开头
            }
        } else {
            fileList + newFile  // 添加到末尾而不是开头
        }
    }

    private fun deleteFileFromList(file: FileItem) {
        fileList = fileList.filter { it.fileUri != file.fileUri }
    }

    /**
     * 清除书籍的内存缓存和数据库数据（后台异步执行）
     */
    private fun clearBookData(fileUri: String) {
        val context = this
        val app = context.applicationContext as ReadEarApplication
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                app.booksCache.clearCache(fileUri)
                CacheManager(context).deleteBook(fileUri)
                releaseUriPermission(fileUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 释放 URI 权限
     */
    private fun releaseUriPermission(fileUri: String) {
        try {
            val uri = fileUri.toUri()
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // 忽略异常，可能权限本来就不存在
        }
    }

    private fun restoreFileList() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val restoredList = fileRepository.loadFileList()
                restoredList.forEach { fileItem ->
                    requestUriPermission(fileItem.fileUri)
                }
                with(kotlinx.coroutines.Dispatchers.Main) {
                    fileList = restoredList
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 请求 URI 持久化权限
     */
    private fun requestUriPermission(fileUri: String) {
        try {
            val uri = fileUri.toUri()
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    private data class FileInfo(
        val fileName: String,
        val fileSize: Long
    )

    private fun getFileInfoFromUri(uri: Uri): FileInfo {
        var fileName = ""
        var fileSize: Long = -1

        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0) {
                        fileName = cursor.getString(displayNameIndex) ?: "未知文件"
                    }

                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        }

        if (fileName.isEmpty()) {
            fileName = uri.lastPathSegment ?: "未知文件"
        }

        return FileInfo(fileName, fileSize)
    }

    private fun getFileTypeFromName(fileName: String): FileType {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".pdf") -> FileType.PDF
            name.endsWith(".docx") -> FileType.DOCX
            name.endsWith(".txt") -> FileType.TXT
            else -> FileType.OTHER
        }
    }

    private fun openContentActivity(file: FileItem) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra(ContentActivity.EXTRA_FILE_URI, file.fileUri)
            putExtra(ContentActivity.EXTRA_FILE_NAME, file.fileName)
            putExtra(ContentActivity.EXTRA_FILE_TYPE, file.fileType.name)
        }
        startActivity(intent)
    }

    fun startTimer(minutes: Int) {
        // 取消之前的定时器
        stopTimer()

        val millisInFuture = minutes * 60 * 1000L

        countdownTimer = object : CountDownTimer(millisInFuture, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTimeMinutes =
                    kotlin.math.ceil((millisUntilFinished * 1.0) / 1000 / 60).toInt()
            }

            override fun onFinish() {
                remainingTimeMinutes = 0
                Log.d("MainActivity", "Timer finished, stopping all speaking")
                // 停止播放
                stopAllSpeaking()
            }
        }.start()
        Log.d("MainActivity", "Timer started for $minutes minutes")
    }

    fun stopTimer() {
        countdownTimer?.cancel()
        countdownTimer = null
        remainingTimeMinutes = 0
        Log.d("MainActivity", "Timer stopped")
    }

    private fun stopAllSpeaking() {
        // 通知 ContentActivity 停止播放
        // 使用 LocalBroadcastManager 发送本地广播
        val intent = android.content.Intent("com.example.readear.STOP_SPEAKING")
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .sendBroadcast(intent)
        Log.d("MainActivity", "发送停止播放广播")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun TimerDialog(
        currentRemainingMinutes: Int,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit,
        onStopTimer: () -> Unit
    ) {
        // 使用当前剩余时间或 0 作为初始值
        var sliderPosition by remember {
            mutableStateOf(currentRemainingMinutes.toFloat() ?: 0f)
        }

        val minMinutes = 0
        val maxMinutes = 120


        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("定时关闭朗读") },
            text = {
                Column {
                    Text(
                        text = if ((sliderPosition).toInt() > 0) {
                            "剩余时间：${(sliderPosition).toInt()}分钟"
                        } else {
                            "设置时间：0 分钟"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            sliderPosition = it
                        },
                        valueRange = minMinutes.toFloat()..maxMinutes.toFloat(),
                        steps = (maxMinutes - minMinutes),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${minMinutes}分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${maxMinutes}分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirm((sliderPosition).toInt())
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    // 只有当有正在运行的定时器时才显示停止按钮
                    if (currentRemainingMinutes > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onStopTimer,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("停止")
                        }
                    }
                }
            }
        )
    }

    override fun onPause() {
        super.onPause()
        fileRepository.saveFileList(fileList)
    }

    override fun onDestroy() {
        super.onDestroy()
        fileRepository.saveFileList(fileList)
        // 清理定时器
        stopTimer()
    }
}

/**
 * 设置对话框（独立可组合函数）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onOpenTTSSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // TTS 设置按钮
                Button(
                    onClick = onOpenTTSSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("设置 TTS")
                }

                Text(
                    text = "点击“设置 TTS”可跳转到系统文字转语音设置页面，选择您喜欢的 TTS 引擎（如讯飞、百度等）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("关闭")
            }
        }
    )
}

/**
 * 打开系统 TTS 设置页面
 */
private fun openTTSSettings(context: Context) {
    val app = context.applicationContext as ReadEarApplication
    app.userTextToSpeech?.openTTSSettings()
}

data class FileItem(
    val fileName: String,
    val fileType: FileType,
    val fileUri: String = "",
    val fileSize: Long = -1L
)

enum class FileType(val iconResId: Int) {
    PDF(R.drawable.ic_pdf),
    DOCX(R.drawable.ic_word),
    TXT(R.drawable.ic_txt),
    OTHER(R.drawable.ic_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    modifier: Modifier = Modifier,
    files: List<FileItem>,
    onDeleteFile: (FileItem) -> Unit = {},
    onFileClick: (FileItem) -> Unit = {},
    onMoveFile: (Int, Int) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current as MainActivity
    var isMovingFile by remember { mutableStateOf(false) }
    
    // 为 LazyColumn 设置初始滚动位置，避免不必要的重排
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (files.isNotEmpty()) files.size - 1 else 0, // 反向布局时显示最后一个项目
        initialFirstVisibleItemScrollOffset = 0
    )

    // 创建reorderable状态，使用detectReorder而不是detectReorderAfterLongPress
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            isMovingFile = true
            Log.d("MainActivity", "Moving item from ${from.index} to ${to.index}")
            onMoveFile(from.index, to.index)
        }
    )
    
    // 使用rememberUpdatedState确保Lambda更新
    val onFileClickUpdated = rememberUpdatedState(onFileClick)

    LaunchedEffect(files.lastOrNull()?.fileUri) {
        if (files.isNotEmpty()
            && !isMovingFile
        ) {
            scope.launch {
                // 使用animateScrollToItem实现平滑滚动
                Log.d("MainActivity", "last item changed, scrolling to last item: ${files.last().fileUri}")
                lazyListState.animateScrollToItem(files.size - 1)
            }
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("文件列表 (${files.size})") },
            actions = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多选项"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = {
                                showMenu = false
                                showSettingsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (MainActivity.remainingTimeMinutes != null && MainActivity.remainingTimeMinutes!! > 0) {
                                        Text("定时 (${MainActivity.remainingTimeMinutes})")
                                    } else {
                                        Text("定时")
                                    }
                                }
                            },
                            onClick = {
                                showMenu = false
                                showTimerDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("关于") },
                            onClick = {
                                showMenu = false
                                showAboutDialog = true
                            }
                        )
                    }
                }
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (files.isEmpty()) {
            Box {
                Text(
                    text = "暂无文件，点击浮动按钮 + 号添加文件",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                reverseLayout = true,
                state = lazyListState,
                modifier = Modifier
                    .animateContentSize()
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // 添加内容缓存，提高滚动性能
                contentPadding = PaddingValues(4.dp),
                // 预加载可见项前后各5个item
                userScrollEnabled = true
            ) {
                items(
                    count = files.size,
                    key = { index -> files[index].fileUri },
                    contentType = { "file_item" } // 添加content type，帮助Compose优化重组
                ) { index ->
                    val file by remember { derivedStateOf { files[index] } }
                    
                    ReorderableItem(
                        state = reorderableState,
                        key = file.fileUri,
                        // 简化修饰符链，减少中间对象创建
                        modifier = Modifier.fillMaxWidth()
                    ) { isDragging ->
                        // 使用remember计算缩放值，避免重复计算
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.05f else 1f,
                            label = "scale"
                        )

                        FileListItem(
                            file = file,
                            isDragging = isDragging,
                            onDelete = {
                                scope.launch {
                                    onDeleteFile(file)
                                }
                            },
                            onClick = {
                                if (!isDragging) {
                                    onFileClickUpdated.value(file)
                                }
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .scale(animatedScale)
                                .draggableHandle(
                                    dragGestureDetector = DragGestureDetector.LongPress,
                                    onDragStopped = {
                                        isMovingFile = false
                                    }
                                )
                        )
                    }
                }
            }
        }
    }

    if (showTimerDialog) {
        (context as MainActivity).TimerDialog(
            currentRemainingMinutes = MainActivity.remainingTimeMinutes,
            onDismiss = { showTimerDialog = false },
            onConfirm = { minutes ->
                if (minutes > 0) {
                    context.startTimer(minutes)
                }
                showTimerDialog = false
            },
            onStopTimer = {
                context.stopTimer()
                showTimerDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            onOpenTTSSettings = {
                openTTSSettings(context)
                showSettingsDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("关于 ReadEar") },
            text = {
                Text(
                    text = """
                        ReadEar - 智能听书助手 v1.2.1
                        
                        主要功能：
                        • 支持 TXT、DOCX、PDF 格式
                        • 文字转语音朗读
                        • 定时关闭功能
                        • 自动保存阅读进度
                        
                        让阅读更轻松！
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false }
                ) {
                    Text("确定")
                }
            }
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024L -> "$size B"
        size < 1024L * 1024L -> "${size / 1024L} KB"
        size < 1024L * 1024L * 1024L -> "${size / (1024L * 1024L)} MB"
        else -> "${size / (1024L * 1024L * 1024L)} GB"
    }
}

@Composable
fun FileListItem(
    file: FileItem,
    isDragging: Boolean = false,
    onDelete: suspend () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteButton by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isDragging) {
        if (isDragging) {
            showDeleteButton = true
        } else {
            kotlinx.coroutines.delay(5000)
            showDeleteButton = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = file.fileType.iconResId),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (file.fileSize > 0L) {
                    Text(
                        text = formatFileSize(file.fileSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = showDeleteButton,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // 根据 showDeleteButton 状态控制删除按钮显示
                IconButton(
                    modifier = Modifier.animateEnterExit(),
                    onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "删除",
                        modifier = Modifier.rotate(45f)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除 \"${file.fileName}\" 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            onDelete()
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun DraggableFloatingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    var offset by remember { mutableStateOf(IntOffset.Zero) }

    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primary,
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
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FileListScreenPreview() {
    ReadEarTheme {
        FileListScreen(
            files = listOf(
                FileItem(
                    "项目文档.pdf",
                    FileType.PDF,
                    "content://com.android.providers.media.documents/document/pdf%3A12345",
                    1024 * 512
                ),
                FileItem(
                    "需求说明.docx",
                    FileType.DOCX,
                    "content://com.android.providers.media.documents/document/word%3A67890",
                    1024 * 256
                ),
                FileItem(
                    "笔记.txt",
                    FileType.TXT,
                    "content://com.android.providers.media.documents/document/text%3A11111",
                    1024 * 10
                )
            )
        )
    }
}