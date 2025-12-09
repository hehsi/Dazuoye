package com.example.haiyangapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.ui.theme.ChatTheme
import com.example.haiyangapp.viewmodel.ConversationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话列表屏幕
 * 显示所有历史对话，支持创建、选择和删除对话
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationViewModel,
    onConversationSelected: (Long) -> Unit,
    onCreateNewConversation: suspend () -> Long
) {
    val conversations by viewModel.conversations.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }

    Scaffold(
        containerColor = ChatTheme.Colors.backgroundVeryLightGray,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ChatTheme.Strings.conversationListTitle,
                        fontSize = ChatTheme.Dimens.textSizeLargeHeading,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ChatTheme.Colors.backgroundWhite,
                    titleContentColor = ChatTheme.Colors.textPrimary,
                    scrolledContainerColor = ChatTheme.Colors.backgroundWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val newConversationId = onCreateNewConversation()
                        if (newConversationId > 0) {
                            onConversationSelected(newConversationId)
                        }
                    }
                },
                containerColor = ChatTheme.Colors.primaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ChatTheme.Strings.descNewConversation
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (conversations.isEmpty()) {
                // 空状态
                EmptyConversationList(
                    onCreateNew = {
                        scope.launch {
                            val newConversationId = onCreateNewConversation()
                            if (newConversationId > 0) {
                                onConversationSelected(newConversationId)
                            }
                        }
                    }
                )
            } else {
                // 对话列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ChatTheme.Dimens.spacingMedium,
                        end = ChatTheme.Dimens.spacingMedium,
                        top = ChatTheme.Dimens.spacingMedium,
                        bottom = 80.dp // Space for FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(ChatTheme.Dimens.spacingSmall)
                ) {
                    items(
                        items = conversations,
                        key = { it.id }
                    ) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationSelected(conversation.id) },
                            onLongPress = { conversationToDelete = conversation }
                        )
                    }
                }
            }

            // 错误提示
            errorMessage?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp) // Avoid FAB overlap
                        .padding(horizontal = ChatTheme.Dimens.spacingNormal),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(ChatTheme.Strings.actionClose, color = ChatTheme.Colors.primaryBlueLight)
                        }
                    },
                    containerColor = ChatTheme.Colors.textPrimary,
                    contentColor = ChatTheme.Colors.textWhite
                ) {
                    Text(error)
                }
            }
        }
    }

    // 删除确认对话框
    conversationToDelete?.let { conversation ->
        val deleteMessage = androidx.compose.ui.res.stringResource(
            com.example.haiyangapp.R.string.conversation_delete_message,
            conversation.title
        )

        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ChatTheme.Colors.accentRed) },
            title = { Text(ChatTheme.Strings.conversationDeleteTitle) },
            text = {
                Text(deleteMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversation.id)
                        conversationToDelete = null
                    }
                ) {
                    Text(
                        ChatTheme.Strings.conversationDeleteConfirm,
                        color = ChatTheme.Colors.accentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text(ChatTheme.Strings.conversationDeleteCancel, color = ChatTheme.Colors.textSecondary)
                }
            },
            containerColor = ChatTheme.Colors.backgroundWhite,
            titleContentColor = ChatTheme.Colors.textPrimary,
            textContentColor = ChatTheme.Colors.textSecondary
        )
    }
}

/**
 * 对话列表项 - 卡片样式
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ChatTheme.Dimens.cornerRadiusMedium))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(
            containerColor = ChatTheme.Colors.backgroundWhite
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        ),
        shape = RoundedCornerShape(ChatTheme.Dimens.cornerRadiusMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ChatTheme.Dimens.spacingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标/头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ChatTheme.Colors.primaryBlueLight.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = ChatTheme.Colors.primaryBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingMedium))

            // 标题和时间
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = conversation.title,
                    fontSize = ChatTheme.Dimens.textSizeTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = ChatTheme.Colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatRelativeTime(conversation.lastMessageAt),
                    fontSize = ChatTheme.Dimens.textSizeSmall,
                    color = ChatTheme.Colors.textHint
                )
            }

            Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingSmall))

            // 箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ChatTheme.Colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 空对话列表状态
 */
@Composable
fun EmptyConversationList(onCreateNew: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(ChatTheme.Dimens.spacingLarge)
        ) {
            // 使用更大的图标或图片
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(ChatTheme.Colors.backgroundLightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = ChatTheme.Colors.textDisabled,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingLarge))

            Text(
                text = ChatTheme.Strings.conversationEmpty,
                fontSize = ChatTheme.Dimens.textSizeHeading,
                color = ChatTheme.Colors.textSecondary,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(ChatTheme.Dimens.spacingSmall))
            
            Text(
                text = "开始一个新的对话来探索 AI 的能力吧",
                fontSize = ChatTheme.Dimens.textSizeBodySmall,
                color = ChatTheme.Colors.textHint,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * 格式化相对时间
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "刚刚"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
        else -> {
            val formatter = SimpleDateFormat("MM-dd", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}
