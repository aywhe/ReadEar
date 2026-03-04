package com.example.readear

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReadEarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FileListScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class FileItem(
    val fileName: String,
    val fileType: FileType
)

enum class FileType(val iconResId: Int) {
    PDF(R.drawable.ic_pdf),
    WORD(R.drawable.ic_word),
    TXT(R.drawable.ic_txt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(modifier: Modifier = Modifier) {
    val files = listOf(
        FileItem("项目文档.pdf", FileType.PDF),
        FileItem("需求说明.docx", FileType.WORD),
        FileItem("笔记.txt", FileType.TXT),
        FileItem("技术手册.pdf", FileType.PDF),
        FileItem("会议记录.docx", FileType.WORD),
        FileItem("项目文档.pdf", FileType.PDF),
        FileItem("需求说明.docx", FileType.WORD),
        FileItem("笔记.txt", FileType.TXT),
        FileItem("技术手册.pdf", FileType.PDF),
        FileItem("会议记录.docx", FileType.WORD),
        FileItem("项目文档.pdf", FileType.PDF),
        FileItem("需求说明.docx", FileType.WORD),
        FileItem("笔记.txt", FileType.TXT),
        FileItem("技术手册.pdf", FileType.PDF),
        FileItem("会议记录.docx", FileType.WORD),
        FileItem("日志.txt", FileType.TXT)
    )

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
            items(files) { file ->
                FileListItem(file = file)
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

@Preview(showBackground = true)
@Composable
fun FileListScreenPreview() {
    ReadEarTheme {
        FileListScreen()
    }
}