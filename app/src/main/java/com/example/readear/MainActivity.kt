package com.example.readear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import com.example.readear.ui.theme.ReadEarTheme

class MainActivity : ComponentActivity() {
    private val fileBrowserLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val fileInfo = getFileInfoFromUri(it)
            Toast.makeText(this, "选择了文件：${fileInfo.fileName}", Toast.LENGTH_SHORT).show()
            
            val newFileItem = FileItem(
                fileName = fileInfo.fileName,
                fileType = getFileTypeFromName(fileInfo.fileName),
                fileUri = it.toString(),
                fileSize = fileInfo.fileSize
            )
            
            addFileToList(newFileItem)
        }
    }

    private var fileList by mutableStateOf<List<FileItem>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadEarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        FileListScreen(
                            modifier = Modifier.fillMaxSize(),
                            files = fileList,
                            onAddFileClick = { openSystemFilePicker() }
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
    }

    private fun openSystemFilePicker() {
        // 支持所有类型的文件
        val mimeTypes = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
        )
        fileBrowserLauncher.launch(mimeTypes)
    }

    private fun addFileToList(newFile: FileItem) {
        // 检查是否已存在相同 URI 的文件
        val existingIndex = fileList.indexOfFirst { it.fileUri == newFile.fileUri }
        
        fileList = if (existingIndex >= 0) {
            // 如果已存在，移除旧的项目，然后将新项目放到第一位
            val updatedList = fileList.toMutableList().apply {
                removeAt(existingIndex)
                add(0, newFile)
            }
            updatedList
        } else {
            // 如果不存在，直接添加到第一位
            listOf(newFile) + fileList
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
            name.endsWith(".doc") || name.endsWith(".docx") -> FileType.WORD
            name.endsWith(".txt") -> FileType.TXT
            else -> FileType.TXT
        }
    }
}

data class FileItem(
    val fileName: String,
    val fileType: FileType,
    val fileUri: String = "",
    val fileSize: Long = -1L
)

enum class FileType(val iconResId: Int) {
    PDF(R.drawable.ic_pdf),
    WORD(R.drawable.ic_word),
    TXT(R.drawable.ic_txt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    modifier: Modifier = Modifier,
    files: List<FileItem>,
    onAddFileClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("文件列表") },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Text(
                        text = "暂无文件，点击 + 号或浮动按钮添加文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(files) { file ->
                    FileListItem(file = file)
                }
            }
        }
    }
}

@Composable
fun FileListItem(file: FileItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
            
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.bodyLarge
            )
        }
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
                FileItem("项目文档.pdf", FileType.PDF, "content://com.android.providers.media.documents/document/pdf%3A12345", 1024 * 512),
                FileItem("需求说明.docx", FileType.WORD, "content://com.android.providers.media.documents/document/word%3A67890", 1024 * 256),
                FileItem("笔记.txt", FileType.TXT, "content://com.android.providers.media.documents/document/text%3A11111", 1024 * 10)
            ),
            onAddFileClick = {}
        )
    }
}