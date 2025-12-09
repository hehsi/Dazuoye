package com.example.haiyangapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.haiyangapp.ui.theme.ChatTheme

/**
 * 聊天界面底部栏
 * 包含输入区域和语音输入功能
 */
@Composable
fun BottomBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean = false,
    isEditing: Boolean = false,
    onCancelEdit: () -> Unit = {},
    // 语音输入相关参数（长按录音）
    isListening: Boolean = false,
    onVoicePressStart: () -> Unit = {},
    onVoicePressEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ChatTheme.Colors.backgroundWhite)
            .padding(bottom = 8.dp) // 增加底部安全距离
    ) {
        // 顶部分隔线
        Divider(
            color = ChatTheme.Colors.dividerLight,
            thickness = ChatTheme.Dimens.dividerThicknessThin
        )

        // 输入区域
        InputArea(
            textInput = textInput,
            onTextChange = onTextChange,
            onSendClick = onSendClick,
            isSending = isSending,
            isEditing = isEditing,
            isListening = isListening,
            onVoicePressStart = onVoicePressStart,
            onVoicePressEnd = onVoicePressEnd
        )
    }
}

/**
 * 输入区域
 * 包含文本输入框、语音按钮和发送按钮
 */
@Composable
private fun InputArea(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean,
    isEditing: Boolean = false,
    isListening: Boolean = false,
    onVoicePressStart: () -> Unit = {},
    onVoicePressEnd: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ChatTheme.Dimens.spacingMedium,
                end = ChatTheme.Dimens.spacingMedium,
                top = ChatTheme.Dimens.spacingSmall
            ),
        verticalAlignment = Alignment.Bottom, // 对齐到底部，适应多行输入
        horizontalArrangement = Arrangement.spacedBy(ChatTheme.Dimens.spacingSmall)
    ) {
        // 文本输入框
        TextField(
            value = textInput,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = when {
                        isListening -> "正在听取语音..."
                        isEditing -> ChatTheme.Strings.chatInputHintEditing
                        else -> ChatTheme.Strings.chatInputHint
                    },
                    fontSize = ChatTheme.Dimens.textSizeBodySmall,
                    color = if (isListening) ChatTheme.Colors.primaryBlue else ChatTheme.Colors.textHint
                )
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 50.dp), // 最小高度
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ChatTheme.Colors.backgroundLightGray,
                unfocusedContainerColor = ChatTheme.Colors.backgroundLightGray,
                disabledContainerColor = ChatTheme.Colors.backgroundLightGray,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp), // 圆润的形状
            maxLines = 5,
            enabled = !isSending && !isListening
        )

        // 语音输入按钮（长按录音）
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (!isSending) {
                        if (isListening) Color(0xFFE53935) else ChatTheme.Colors.backgroundLightGray
                    } else {
                        ChatTheme.Colors.backgroundLightGray
                    }
                )
                .pointerInput(isSending) {
                    if (!isSending) {
                        detectTapGestures(
                            onPress = {
                                // 按下时开始录音
                                onVoicePressStart()
                                // 等待抬起或取消
                                val released = tryAwaitRelease()
                                // 抬起或取消时停止录音
                                onVoicePressEnd()
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "松开停止录音" else "长按开始录音",
                modifier = Modifier.size(20.dp),
                tint = if (!isSending) {
                    if (isListening) Color.White else ChatTheme.Colors.textSecondary
                } else {
                    ChatTheme.Colors.textHint
                }
            )
        }

        // 发送按钮
        val isSendEnabled = textInput.isNotBlank() && !isSending && !isListening

        FilledIconButton(
            onClick = onSendClick,
            enabled = isSendEnabled,
            modifier = Modifier.size(50.dp), // 与输入框高度匹配
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = ChatTheme.Colors.primaryBlue,
                contentColor = Color.White,
                disabledContainerColor = ChatTheme.Colors.backgroundLightGray,
                disabledContentColor = ChatTheme.Colors.textHint
            )
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = if (isEditing) {
                    ChatTheme.Strings.chatResendButtonDesc
                } else {
                    ChatTheme.Strings.chatSendButtonDesc
                },
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
