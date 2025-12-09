package com.example.haiyangapp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.haiyangapp.ui.theme.ChatTheme
import com.example.haiyangapp.ui.theme.ChatConstants
import com.example.haiyangapp.viewmodel.ChatViewModel
import com.example.haiyangapp.repository.RepositoryFactory
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material.icons.filled.MenuBook

/**
 * 聊天界面主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    conversationId: Long,
    onNavigateBack: () -> Unit = {},
    onNavigateToConversationList: () -> Unit = {},
    conversationViewModel: com.example.haiyangapp.viewmodel.ConversationViewModel? = null,
    onConversationSelected: ((Long) -> Unit)? = null,
    onNavigateToKnowledgeBase: (() -> Unit)? = null
) {
    // 加载对话（如果还未加载）
    LaunchedEffect(conversationId) {
        if (conversationId > 0 && viewModel.currentConversationId.value != conversationId) {
            viewModel.loadConversation(conversationId)
        }
    }

    // 观察ViewModel的状态
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val likedMessageIds by viewModel.likedMessageIds.collectAsState()
    val currentInferenceMode by viewModel.currentInferenceMode.collectAsState()
    val isDeepThinkingEnabled by viewModel.isDeepThinkingEnabled.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isSuggestionsLoading by viewModel.isSuggestionsLoading.collectAsState()
    val isRAGEnabled by viewModel.isRAGEnabled.collectAsState()

    // 输入框文本状态
    var textInput by remember { mutableStateOf("") }

    // 编辑状态：记录正在编辑的消息ID
    var editingMessageId by remember { mutableStateOf<Int?>(null) }

    // 语音识别状态
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    // 权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限获取成功，开始语音识别
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    // 设置语音识别监听器
    DisposableEffect(speechRecognizer) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                // Error 7 = ERROR_NO_MATCH（没有识别到语音），这是正常情况，不需要报错
                // Error 8 = ERROR_RECOGNIZER_BUSY（识别器忙）
                // Error 9 = ERROR_INSUFFICIENT_PERMISSIONS（权限不足）
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        // 没有识别到语音，静默处理
                        android.util.Log.d("SpeechRecognizer", "No speech detected")
                    }
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // 语音超时，静默处理
                        android.util.Log.d("SpeechRecognizer", "Speech timeout")
                    }
                    else -> {
                        android.util.Log.e("SpeechRecognizer", "Error: $error")
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // 将识别结果追加到输入框
                    textInput = if (textInput.isBlank()) matches[0] else "$textInput ${matches[0]}"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // 实时显示部分识别结果
                    textInput = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer.destroy()
        }
    }

    // 剪贴板管理器
    val clipboardManager = LocalClipboardManager.current

    // Snackbar 显示状态
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 获取字符串资源（在Composable上下文中）
    val copiedMessage = ChatTheme.Strings.chatCopiedToClipboard

    // LazyColumn滚动状态
    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(messages.size, isLoading, errorMessage) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + 2)
        }
    }

    // 获取对话标题
    val conversationTitle by viewModel.conversationTitle.collectAsState()

    // 抽屉状态
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // 新建会话加载状态
    var isCreatingConversation by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            if (conversationViewModel != null && onConversationSelected != null) {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxWidth(0.75f) // 占据75%宽度
                ) {
                    ConversationDrawerContent(
                        viewModel = conversationViewModel,
                        onConversationSelected = { selectedId ->
                            coroutineScope.launch {
                                drawerState.close()
                                onConversationSelected(selectedId)
                            }
                        },
                        onCreateNewConversation = {
                            coroutineScope.launch {
                                val newId = conversationViewModel.createNewConversation()
                                if (newId > 0) {
                                    // 显示加载对话框
                                    isCreatingConversation = true
                                    // 先生成推荐问题（阻塞等待完成）
                                    viewModel.generateAndCacheSuggestions()
                                    // 生成完成后关闭加载对话框并进入新会话
                                    isCreatingConversation = false
                                    drawerState.close()
                                    onConversationSelected(newId)
                                }
                            }
                        },
                        currentConversationId = conversationId
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = conversationTitle,
                    onMenuClick = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    },
                    currentMode = currentInferenceMode,
                    onModeChange = { mode ->
                        viewModel.setInferenceMode(mode)
                    },
                    isRAGEnabled = isRAGEnabled,
                    onRAGToggle = { viewModel.toggleRAG() },
                    onKnowledgeBaseClick = onNavigateToKnowledgeBase
                )
            },
            bottomBar = {
                BottomBar(
                    textInput = textInput,
                    onTextChange = { textInput = it },
                    onSendClick = {
                        if (textInput.isNotBlank()) {
                            editingMessageId?.let { messageId ->
                                // 编辑模式：重新发送消息
                                viewModel.editMessage(messageId, textInput)
                                editingMessageId = null
                            } ?: run {
                                // 普通模式：发送新消息
                                viewModel.sendMessage(textInput)
                            }
                            textInput = "" // 清空输入框
                        }
                    },
                    isSending = isLoading,
                    isEditing = editingMessageId != null,
                    onCancelEdit = {
                        editingMessageId = null
                        textInput = ""
                    },
                    // 语音输入（长按录音）
                    isListening = isListening,
                    onVoicePressStart = {
                        // 长按开始录音，请求权限
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onVoicePressEnd = {
                        // 松开停止录音
                        if (isListening) {
                            speechRecognizer.stopListening()
                            isListening = false
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(ChatTheme.Colors.backgroundWhite),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // AI 头像
                item {
                    Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingLarge))
                    Box(
                        modifier = Modifier
                            .size(ChatTheme.Dimens.iconSizeAvatar)
                            .clip(CircleShape)
                            .background(ChatTheme.Colors.primaryBlue.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ChatTheme.Colors.primaryBlue,
                            modifier = Modifier
                                .size(ChatTheme.Dimens.iconSizeAvatar * 0.6f)
                                .align(Alignment.Center)
                        )
                    }
                    Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingNormal))
                }

                // 欢迎消息
                item {
                    WelcomeMessageBubble(
                        message = ChatTheme.Strings.welcomeMessage
                    )
                    Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingMedium))
                }

                // 建议问题（仅在没有消息时显示）
                if (messages.isEmpty()) {
                    item {
                        if (isSuggestionsLoading) {
                            // 加载中状态
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ChatTheme.Dimens.spacingNormal),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(ChatTheme.Dimens.iconSizeSmall),
                                    strokeWidth = ChatTheme.Dimens.progressIndicatorStroke,
                                    color = ChatTheme.Colors.primaryBlue
                                )
                                Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))
                                Text(
                                    text = ChatTheme.Strings.suggestionsLoading,
                                    fontSize = ChatTheme.Dimens.textSizeBodySmall,
                                    color = ChatTheme.Colors.textHint
                                )
                            }
                        } else if (suggestions.isNotEmpty()) {
                            SuggestionChips(
                                suggestions = suggestions,
                                onSuggestionClick = { suggestion ->
                                    viewModel.sendMessage(suggestion)
                                }
                            )
                        }
                    }
                }

                // 动态消息列表
                items(messages) { message ->
                    MessageBubble(
                        message = message.content,
                        isFromUser = message.isFromUser,
                        onEditClick = if (message.isFromUser) {
                            {
                                // 进入编辑模式
                                editingMessageId = message.id
                                textInput = message.content
                            }
                        } else null,
                        isBeingEdited = message.id == editingMessageId,
                        isLiked = viewModel.isMessageLiked(message.id),
                        onLikeClick = if (!message.isFromUser) {
                            {
                                // 切换点赞状态
                                viewModel.toggleLike(message.id)
                            }
                        } else null,
                        onCopyClick = {
                            // 复制消息到剪贴板
                            clipboardManager.setText(AnnotatedString(message.content))
                            // 显示提示
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = copiedMessage,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        },
                        // 重新生成相关回调（仅AI消息）
                        onRegenerateClick = if (!message.isFromUser) {
                            { viewModel.regenerateMessage(message.id) }
                        } else null,
                        versionIndex = message.versionIndex,
                        totalVersions = message.totalVersions,
                        onPreviousVersion = if (!message.isFromUser && message.totalVersions > 1) {
                            { viewModel.switchToPreviousVersion(message.id) }
                        } else null,
                        onNextVersion = if (!message.isFromUser && message.totalVersions > 1) {
                            { viewModel.switchToNextVersion(message.id) }
                        } else null,
                        isLoading = isLoading,
                        sources = message.sources,
                        onSourceClick = { source ->
                            // 打开源文件或网页链接
                            if (source.sourcePath.isNotEmpty()) {
                                try {
                                    val uri = Uri.parse(source.sourcePath)

                                    // 检查是否为网页链接
                                    val isWebUrl = source.sourcePath.startsWith("http://") ||
                                            source.sourcePath.startsWith("https://")

                                    if (isWebUrl) {
                                        // 网页链接：使用浏览器打开
                                        val intent = Intent(Intent.ACTION_VIEW, uri)
                                        context.startActivity(intent)
                                    } else {
                                        // 本地文件：检查权限后打开
                                        // 检查是否有持久化的URI权限
                                        val hasPermission = context.contentResolver.persistedUriPermissions
                                            .any { it.uri == uri && it.isReadPermission }

                                        if (!hasPermission) {
                                            // 如果没有权限，提示用户重新导入文档
                                            Toast.makeText(
                                                context,
                                                "文件访问权限已过期，请在知识库中重新导入该文档",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@MessageBubble
                                        }

                                        // 获取 MIME 类型
                                        val mimeType = context.contentResolver.getType(uri)
                                            ?: getMimeTypeFromFileName(source.documentTitle)

                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }

                                        // 使用 chooser 让用户选择应用
                                        val chooserIntent = Intent.createChooser(intent, "打开 ${source.documentTitle}")
                                        context.startActivity(chooserIntent)
                                    }
                                } catch (e: SecurityException) {
                                    android.util.Log.e("ChatScreen", "Permission denied: ${e.message}", e)
                                    Toast.makeText(
                                        context,
                                        "文件访问权限已过期，请在知识库中重新导入该文档",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Failed to open: ${e.message}", e)
                                    Toast.makeText(
                                        context,
                                        "无法打开: ${source.documentTitle}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }

                // 加载指示器（只在消息列表为空或最后一条是用户消息时显示）
                if (isLoading && (messages.isEmpty() || messages.lastOrNull()?.isFromUser == true)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = ChatTheme.Dimens.spacingNormal,
                                    vertical = ChatTheme.Dimens.spacingSmall
                                ),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ChatTheme.Dimens.progressIndicatorSize),
                                strokeWidth = ChatTheme.Dimens.progressIndicatorStroke,
                                color = ChatTheme.Colors.primaryBlue
                            )
                            Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))
                            Text(
                                text = ChatTheme.Strings.chatThinking,
                                fontSize = ChatTheme.Dimens.textSizeBodySmall,
                                color = ChatTheme.Colors.textHint
                            )
                        }
                    }
                }

                // 错误消息
                errorMessage?.let { error ->
                    item {
                        Snackbar(
                            modifier = Modifier.padding(ChatTheme.Dimens.spacingNormal),
                            action = {
                                TextButton(onClick = { viewModel.clearError() }) {
                                    Text(ChatTheme.Strings.actionClose)
                                }
                            }
                        ) {
                            Text(error)
                        }
                    }
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingSmall))
                }
            }
        }
    }

    // 新建会话加载对话框
    if (isCreatingConversation) {
        AlertDialog(
            onDismissRequest = { /* 不允许关闭 */ },
            confirmButton = { },
            title = {
                Text(
                    text = ChatTheme.Strings.suggestionsLoading,
                    fontSize = ChatTheme.Dimens.textSizeTitle,
                    fontWeight = FontWeight.Medium
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ChatTheme.Dimens.iconSizeMedium),
                        strokeWidth = ChatTheme.Dimens.progressIndicatorStroke,
                        color = ChatTheme.Colors.primaryBlue
                    )
                }
            }
        )
    }
}

/**
 * 对话抽屉内容
 */
@Composable
private fun ConversationDrawerContent(
    viewModel: com.example.haiyangapp.viewmodel.ConversationViewModel,
    onConversationSelected: (Long) -> Unit,
    onCreateNewConversation: () -> Unit,
    currentConversationId: Long
) {
    val conversations by viewModel.conversations.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatTheme.Colors.backgroundWhite)
    ) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ChatTheme.Dimens.spacingNormal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ChatTheme.Strings.conversationListTitle,
                fontSize = ChatTheme.Dimens.textSizeLargeHeading,
                fontWeight = FontWeight.Bold,
                color = ChatTheme.Colors.textPrimary
            )
        }

        Divider(color = ChatTheme.Colors.dividerLight)

        // 新建对话按钮
        TextButton(
            onClick = onCreateNewConversation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ChatTheme.Dimens.spacingNormal,
                    vertical = ChatTheme.Dimens.spacingSmall
                )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = ChatTheme.Strings.descNewConversation,
                tint = ChatTheme.Colors.primaryBlue
            )
            Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))
            Text(
                text = ChatTheme.Strings.conversationNew,
                color = ChatTheme.Colors.primaryBlue,
                fontSize = ChatTheme.Dimens.textSizeTitle
            )
        }

        Divider(color = ChatTheme.Colors.dividerLight)

        // 对话列表
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(conversations) { conversation ->
                val isSelected = conversation.id == currentConversationId

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConversationSelected(conversation.id) },
                    color = if (isSelected) ChatTheme.Colors.primaryBlueLight.copy(alpha = 0.1f) else ChatTheme.Colors.backgroundWhite
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ChatTheme.Dimens.spacingSmall), // 减小垂直内边距，更紧凑
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 选中状态指示条
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(32.dp)
                                .background(
                                    if (isSelected) ChatTheme.Colors.primaryBlue else Color.Transparent,
                                    shape = RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp)
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = ChatTheme.Dimens.spacingSmall)
                        ) {
                            Text(
                                text = conversation.title,
                                fontSize = ChatTheme.Dimens.textSizeTitle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textPrimary,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingTiny))
                            Text(
                                text = formatTimestamp(conversation.lastMessageAt),
                                fontSize = ChatTheme.Dimens.textSizeSmall,
                                color = ChatTheme.Colors.textHint
                            )
                        }

                        // 删除按钮
                        IconButton(
                            onClick = { viewModel.deleteConversation(conversation.id) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = ChatTheme.Strings.descDeleteConversation,
                                tint = if (isSelected) ChatTheme.Colors.primaryBlue.copy(alpha = 0.6f) else ChatTheme.Colors.textHint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))
                    }
                }
                Divider(
                    color = ChatTheme.Colors.dividerLight, 
                    modifier = Modifier.padding(start = 16.dp) // 缩进分割线
                )
            }
        }
    }
}

/**
 * 根据文件名获取 MIME 类型
 */
private fun getMimeTypeFromFileName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        // 文本文件
        "txt" -> "text/plain"
        "md", "markdown" -> "text/markdown"
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"

        // 文档
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"

        // 图片
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"

        // 音频
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"

        // 视频
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"

        // 默认
        else -> "*/*"
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 604800_000 -> "${diff / 86400_000}天前"
        else -> {
            val date = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
            date.format(java.util.Date(timestamp))
        }
    }
}

/**
 * 顶部导航栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String = "",
    onMenuClick: () -> Unit = {},
    currentMode: RepositoryFactory.InferenceMode = RepositoryFactory.InferenceMode.LOCAL,
    onModeChange: (RepositoryFactory.InferenceMode) -> Unit = {},
    isRAGEnabled: Boolean = false,
    onRAGToggle: () -> Unit = {},
    onKnowledgeBaseClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifEmpty { ChatTheme.Strings.chatTitleNew }
    var showModeMenu by remember { mutableStateOf(false) }
    var showRAGMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = displayTitle,
                fontSize = ChatTheme.Dimens.textSizeHeading,
                fontWeight = FontWeight.Medium,
                color = ChatTheme.Colors.textPrimary,
                maxLines = 1
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = ChatTheme.Strings.descMenu,
                    tint = ChatTheme.Colors.textSecondary
                )
            }
        },
        actions = {
            // 知识库开关 Chip
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isRAGEnabled) ChatTheme.Colors.primaryBlue.copy(alpha = 0.15f) else ChatTheme.Colors.backgroundLightGray,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { showRAGMenu = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "知识库",
                            modifier = Modifier.size(16.dp),
                            tint = if (isRAGEnabled) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRAGEnabled) "知识库" else "知识库",
                            fontSize = ChatTheme.Dimens.textSizeSmall,
                            color = if (isRAGEnabled) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textSecondary,
                            fontWeight = if (isRAGEnabled) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }

                DropdownMenu(
                    expanded = showRAGMenu,
                    onDismissRequest = { showRAGMenu = false },
                    modifier = Modifier.background(ChatTheme.Colors.backgroundWhite)
                ) {
                    // 开关选项
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isRAGEnabled) "关闭知识库" else "开启知识库",
                                color = ChatTheme.Colors.textPrimary
                            )
                        },
                        onClick = {
                            onRAGToggle()
                            showRAGMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = if (isRAGEnabled) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textSecondary
                            )
                        }
                    )
                    // 管理知识库选项
                    if (onKnowledgeBaseClick != null) {
                        HorizontalDivider(color = ChatTheme.Colors.dividerLight)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "管理知识库",
                                    color = ChatTheme.Colors.textPrimary
                                )
                            },
                            onClick = {
                                showRAGMenu = false
                                onKnowledgeBaseClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = ChatTheme.Colors.textSecondary
                                )
                            }
                        )
                    }
                }
            }

            // 模式切换 Chip
            Box(
                modifier = Modifier
                    .padding(end = ChatTheme.Dimens.spacingSmall)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ChatTheme.Colors.backgroundLightGray,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { showModeMenu = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (currentMode == RepositoryFactory.InferenceMode.LOCAL)
                                Icons.Default.Computer else Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ChatTheme.Colors.primaryBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentMode == RepositoryFactory.InferenceMode.LOCAL)
                                ChatTheme.Strings.modelLocal else ChatTheme.Strings.modelRemote,
                            fontSize = ChatTheme.Dimens.textSizeSmall,
                            color = ChatTheme.Colors.primaryBlue,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = ChatTheme.Colors.textSecondary
                        )
                    }
                }

                DropdownMenu(
                    expanded = showModeMenu,
                    onDismissRequest = { showModeMenu = false },
                    modifier = Modifier.background(ChatTheme.Colors.backgroundWhite)
                ) {
                    DropdownMenuItem(
                        text = { Text(ChatTheme.Strings.modelLocal) },
                        onClick = {
                            onModeChange(RepositoryFactory.InferenceMode.LOCAL)
                            showModeMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Computer,
                                contentDescription = null,
                                tint = if (currentMode == RepositoryFactory.InferenceMode.LOCAL)
                                    ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textSecondary
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(ChatTheme.Strings.modelRemote) },
                        onClick = {
                            onModeChange(RepositoryFactory.InferenceMode.REMOTE)
                            showModeMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (currentMode == RepositoryFactory.InferenceMode.REMOTE)
                                    ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textSecondary
                            )
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ChatTheme.Colors.backgroundWhite
        )
    )
}

