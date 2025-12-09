package com.example.haiyangapp.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.haiyangapp.database.entity.KnowledgeDocumentEntity
import com.example.haiyangapp.ui.theme.ChatTheme
import com.example.haiyangapp.viewmodel.KnowledgeViewModel
import com.example.haiyangapp.viewmodel.KnowledgeViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

/**
 * 知识库管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    onNavigateBack: () -> Unit,
    viewModel: KnowledgeViewModel = viewModel(
        factory = KnowledgeViewModelFactory(LocalContext.current)
    )
) {
    val documents by viewModel.documents.collectAsState()
    val indexingProgress by viewModel.indexingProgress.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val showImportDialog by viewModel.showImportDialog.collectAsState()

    val context = LocalContext.current

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 持久化URI访问权限，这样应用重启后仍能访问该文件
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                android.util.Log.w("KnowledgeBaseScreen", "Failed to persist URI permission: ${e.message}")
            }
            viewModel.importDocument(it)
        }
    }

    // 删除确认对话框状态
    var documentToDelete by remember { mutableStateOf<KnowledgeDocumentEntity?>(null) }

    // Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }

    // 处理操作状态
    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is KnowledgeViewModel.OperationState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearOperationState()
            }
            is KnowledgeViewModel.OperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "知识库管理",
                        color = ChatTheme.Colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = ChatTheme.Colors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword"
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "导入文档",
                            tint = ChatTheme.Colors.primaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatTheme.Colors.backgroundWhite
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ChatTheme.Colors.backgroundLightGray
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (documents.isEmpty()) {
                // 空状态
                EmptyKnowledgeState(
                    onImportClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/msword"
                            )
                        )
                    }
                )
            } else {
                // 文档列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 统计信息
                    item {
                        KnowledgeStats(
                            totalDocuments = documents.size,
                            indexedDocuments = documents.count { it.isIndexed },
                            totalChunks = documents.sumOf { it.chunkCount }
                        )
                    }

                    // 文档列表
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            indexingProgress = indexingProgress[document.id],
                            onDelete = { documentToDelete = document }
                        )
                    }
                }
            }

            // 加载指示器
            if (operationState is KnowledgeViewModel.OperationState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ChatTheme.Colors.primaryBlue)
                }
            }
        }
    }

    // 删除确认对话框
    documentToDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${document.title}」吗？\n该操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(document.id)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 空状态界面
 */
@Composable
private fun EmptyKnowledgeState(
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = ChatTheme.Colors.textHint
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "知识库为空",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ChatTheme.Colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "导入PDF或Word文档，让AI能够基于你的文档回答问题",
            fontSize = 14.sp,
            color = ChatTheme.Colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChatTheme.Colors.primaryBlue
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入文档")
        }
    }
}

/**
 * 统计信息卡片
 */
@Composable
private fun KnowledgeStats(
    totalDocuments: Int,
    indexedDocuments: Int,
    totalChunks: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ChatTheme.Colors.primaryBlue.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Description,
                value = totalDocuments.toString(),
                label = "文档"
            )
            StatItem(
                icon = Icons.Default.CheckCircle,
                value = indexedDocuments.toString(),
                label = "已索引"
            )
            StatItem(
                icon = Icons.Default.Layers,
                value = totalChunks.toString(),
                label = "知识块"
            )
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = ChatTheme.Colors.primaryBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = ChatTheme.Colors.textPrimary
        )
        Text(
            label,
            fontSize = 12.sp,
            color = ChatTheme.Colors.textSecondary
        )
    }
}

/**
 * 文档卡片
 */
@Composable
private fun DocumentCard(
    document: KnowledgeDocumentEntity,
    indexingProgress: Float?,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ChatTheme.Colors.backgroundWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文档图标
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (document.sourceType) {
                                "pdf" -> Color(0xFFE53935).copy(alpha = 0.1f)
                                "docx", "doc" -> Color(0xFF1976D2).copy(alpha = 0.1f)
                                else -> ChatTheme.Colors.backgroundLightGray
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (document.sourceType) {
                            "pdf" -> Icons.Default.PictureAsPdf
                            "docx", "doc" -> Icons.Default.Description
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when (document.sourceType) {
                            "pdf" -> Color(0xFFE53935)
                            "docx", "doc" -> Color(0xFF1976D2)
                            else -> ChatTheme.Colors.textSecondary
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 文档信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        document.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ChatTheme.Colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatFileSize(document.fileSize),
                            fontSize = 12.sp,
                            color = ChatTheme.Colors.textHint
                        )
                        Text(
                            " · ",
                            fontSize = 12.sp,
                            color = ChatTheme.Colors.textHint
                        )
                        Text(
                            "${document.chunkCount} 个知识块",
                            fontSize = 12.sp,
                            color = ChatTheme.Colors.textHint
                        )
                    }
                }

                // 状态/操作
                if (document.isIndexed) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = ChatTheme.Colors.textHint
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = ChatTheme.Colors.primaryBlue
                    )
                }
            }

            // 索引进度条
            indexingProgress?.let { progress ->
                if (progress < 1f) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = ChatTheme.Colors.primaryBlue,
                            trackColor = ChatTheme.Colors.backgroundLightGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "正在建立索引: ${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = ChatTheme.Colors.primaryBlue
                        )
                    }
                }
            }

            // 创建时间
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "导入于 ${formatDate(document.createdAt)}",
                fontSize = 11.sp,
                color = ChatTheme.Colors.textHint
            )
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * 格式化日期
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
