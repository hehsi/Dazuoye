package com.example.haiyangapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.example.haiyangapp.ui.theme.ChatTheme

/**
 * 欢迎消息气泡
 */
@Composable
fun WelcomeMessageBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ChatTheme.Dimens.cornerRadiusNormal))
            .background(ChatTheme.Colors.backgroundAiMessage)
            .padding(ChatTheme.Dimens.spacingNormal)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = ChatTheme.Dimens.textSizeBody,
            color = ChatTheme.Colors.textPrimary
        )
    }
}

/**
 * 对话消息气泡
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: String,
    isFromUser: Boolean = false,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
    isBeingEdited: Boolean = false,
    isLiked: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onCopyClick: (() -> Unit)? = null,
    // 重新生成相关参数
    onRegenerateClick: (() -> Unit)? = null,
    versionIndex: Int = 0,
    totalVersions: Int = 1,
    onPreviousVersion: (() -> Unit)? = null,
    onNextVersion: (() -> Unit)? = null,
    isLoading: Boolean = false,
    // RAG 引用来源
    sources: List<SourceReference> = emptyList(),
    // 点击引用来源打开文件
    onSourceClick: ((SourceReference) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = ChatTheme.Dimens.spacingNormal,
                vertical = ChatTheme.Dimens.spacingTiny
            ),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isFromUser) Alignment.End else Alignment.Start
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .widthIn(max = ChatTheme.Dimens.messageBubbleMaxWidth)
                        .then(
                            // 编辑状态：添加外层发光边框
                            if (isBeingEdited) {
                                Modifier
                                    .shadow(
                                        elevation = ChatTheme.Dimens.elevationLarge,
                                        shape = RoundedCornerShape(ChatTheme.Dimens.cornerRadiusNormal),
                                        spotColor = ChatTheme.Colors.shadowEditingSpot,
                                        ambientColor = ChatTheme.Colors.shadowEditingAmbient
                                    )
                                    .border(
                                        width = ChatTheme.Dimens.messageBubbleBorderEditing,
                                        color = ChatTheme.Colors.accentOrange,
                                        shape = RoundedCornerShape(ChatTheme.Dimens.cornerRadiusNormal)
                                    )
                            } else if (isLiked && !isFromUser) {
                                // 点赞状态：添加淡蓝色边框
                                Modifier.border(
                                    width = ChatTheme.Dimens.messageBubbleBorderWidth,
                                    color = ChatTheme.Colors.borderLiked,
                                    shape = RoundedCornerShape(ChatTheme.Dimens.cornerRadiusNormal)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(ChatTheme.Dimens.cornerRadiusNormal))
                        .background(
                            when {
                                isBeingEdited -> ChatTheme.Colors.editingBackground
                                isLiked && !isFromUser -> ChatTheme.Colors.backgroundLikedMessage
                                isFromUser -> ChatTheme.Colors.backgroundUserMessage
                                else -> ChatTheme.Colors.backgroundAiMessage
                            }
                        )
                        .combinedClickable(
                            onClick = { },
                            onLongClick = { showMenu = true }
                        )
                        .padding(ChatTheme.Dimens.messageBubblePadding)
                ) {
                    // AI消息使用Markdown渲染，用户消息使用普通文本
                    if (!isFromUser) {
                        MarkdownText(
                            text = message,
                            color = when {
                                isBeingEdited -> ChatTheme.Colors.editingTextDark
                                else -> ChatTheme.Colors.textPrimary
                            },
                            fontSize = ChatTheme.Dimens.textSizeBody
                        )
                    } else {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = ChatTheme.Dimens.textSizeBody,
                            color = when {
                                isBeingEdited -> ChatTheme.Colors.editingTextDark
                                else -> ChatTheme.Colors.textWhite
                            }
                        )
                    }
                }

                // 长按菜单
                val menuShape = RoundedCornerShape(ChatTheme.Dimens.cornerRadiusMedium)
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .shadow(ChatTheme.Dimens.elevationLarge, menuShape, clip = false)
                        .background(ChatTheme.Colors.backgroundVeryLightGray, menuShape)
                        .border(
                            BorderStroke(ChatTheme.Dimens.dividerThicknessNormal, ChatTheme.Colors.dividerLight),
                            menuShape
                        )
                ) {
                    // 复制选项（所有消息都有）
                    if (onCopyClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = ChatTheme.Strings.menuCopy,
                                    color = ChatTheme.Colors.textSecondary,
                                    fontSize = ChatTheme.Dimens.textSizeBodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = ChatTheme.Colors.textTertiary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onCopyClick()
                            }
                        )
                    }

                    // 编辑选项（仅用户消息）
                    if (isFromUser && onEditClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = ChatTheme.Strings.menuEdit,
                                    color = ChatTheme.Colors.textSecondary,
                                    fontSize = ChatTheme.Dimens.textSizeBodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = ChatTheme.Colors.textTertiary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onEditClick()
                            }
                        )
                    }

                    // 点赞/取消点赞选项（仅AI消息）
                    if (!isFromUser && onLikeClick != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isLiked) ChatTheme.Strings.menuUnlike else ChatTheme.Strings.menuLike,
                                    color = ChatTheme.Colors.textSecondary,
                                    fontSize = ChatTheme.Dimens.textSizeBodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = null,
                                    tint = if (isLiked) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textTertiary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLikeClick()
                            }
                        )
                    }

                    // 重新生成选项（仅AI消息）
                    if (!isFromUser && onRegenerateClick != null && !isLoading) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "重新生成",
                                    color = ChatTheme.Colors.textSecondary,
                                    fontSize = ChatTheme.Dimens.textSizeBodySmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    tint = ChatTheme.Colors.textTertiary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRegenerateClick()
                            }
                        )
                    }
                }
            }

            // 点赞图标（仅AI消息且已点赞时显示）
            if (!isFromUser && isLiked) {
                Row(
                    modifier = Modifier.padding(
                        start = ChatTheme.Dimens.spacingSmall,
                        top = ChatTheme.Dimens.spacingTiny
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = ChatTheme.Strings.descLiked,
                        modifier = Modifier.size(ChatTheme.Dimens.iconSizeTiny),
                        tint = ChatTheme.Colors.primaryBlue
                    )
                    Spacer(modifier = Modifier.width(ChatTheme.Dimens.spacingTiny))
                    Text(
                        text = ChatTheme.Strings.messageLiked,
                        fontSize = ChatTheme.Dimens.textSizeTiny,
                        color = ChatTheme.Colors.primaryBlue
                    )
                }
            }

            // 版本切换器（仅AI消息且有多个版本时显示）
            if (!isFromUser && totalVersions > 1) {
                Row(
                    modifier = Modifier.padding(
                        start = ChatTheme.Dimens.spacingSmall,
                        top = ChatTheme.Dimens.spacingTiny
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一个版本按钮
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "上一个版本",
                        modifier = Modifier
                            .size(ChatTheme.Dimens.iconSizeSmall)
                            .clickable(enabled = versionIndex > 0 && !isLoading) {
                                onPreviousVersion?.invoke()
                            },
                        tint = if (versionIndex > 0 && !isLoading)
                            ChatTheme.Colors.textSecondary
                        else
                            ChatTheme.Colors.textTertiary.copy(alpha = 0.5f)
                    )

                    // 版本指示器 "1/3"
                    Text(
                        text = "${versionIndex + 1}/$totalVersions",
                        fontSize = ChatTheme.Dimens.textSizeTiny,
                        color = ChatTheme.Colors.textSecondary,
                        modifier = Modifier.padding(horizontal = ChatTheme.Dimens.spacingTiny)
                    )

                    // 下一个版本按钮
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "下一个版本",
                        modifier = Modifier
                            .size(ChatTheme.Dimens.iconSizeSmall)
                            .clickable(enabled = versionIndex < totalVersions - 1 && !isLoading) {
                                onNextVersion?.invoke()
                            },
                        tint = if (versionIndex < totalVersions - 1 && !isLoading)
                            ChatTheme.Colors.textSecondary
                        else
                            ChatTheme.Colors.textTertiary.copy(alpha = 0.5f)
                    )
                }
            }

            // 引用来源显示（仅AI消息且有来源时显示）
            if (!isFromUser && sources.isNotEmpty()) {
                SourceReferencesSection(
                    sources = sources,
                    onSourceClick = onSourceClick
                )
            }
        }
    }
}

/**
 * 引用来源区域
 */
@Composable
private fun SourceReferencesSection(
    sources: List<SourceReference>,
    modifier: Modifier = Modifier,
    onSourceClick: ((SourceReference) -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(
            start = ChatTheme.Dimens.spacingSmall,
            top = ChatTheme.Dimens.spacingSmall
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = ChatTheme.Colors.textHint
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "参考来源",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ChatTheme.Colors.textHint
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 显示来源文档（去重）
        // 使用sourcePath去重（处理知识库和网页搜索两种情况）
        // 如果是知识库来源（documentId > 0），按documentId去重
        // 如果是网页搜索来源（documentId = 0），按sourcePath（URL）去重
        val uniqueSources = sources.distinctBy {
            if (it.documentId > 0) "doc_${it.documentId}" else "url_${it.sourcePath}"
        }
        uniqueSources.forEach { source ->
            Row(
                modifier = Modifier
                    .padding(start = 2.dp, top = 2.dp)
                    .then(
                        if (onSourceClick != null && source.sourcePath.isNotEmpty()) {
                            Modifier.clickable { onSourceClick(source) }
                        } else {
                            Modifier
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "·",
                    fontSize = 11.sp,
                    color = ChatTheme.Colors.textHint
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = source.documentTitle,
                    fontSize = 11.sp,
                    color = ChatTheme.Colors.primaryBlue.copy(alpha = 0.8f),
                    fontWeight = if (onSourceClick != null) FontWeight.Medium else FontWeight.Normal
                )
                // 如果可点击，显示打开图标
                if (onSourceClick != null && source.sourcePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "打开文件",
                        modifier = Modifier.size(12.dp),
                        tint = ChatTheme.Colors.primaryBlue.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
