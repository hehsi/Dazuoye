package com.example.haiyangapp.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isLiked: Boolean = false,
    // 重新生成版本管理字段
    val regenerateGroupId: Long? = null,  // 组标识：使用对应用户消息的ID，把同一问题的多个AI回答关联起来
    val versionIndex: Int = 0,            // 版本序号：0=原始版本，1,2,3...=重新生成的版本
    val isCurrentVersion: Boolean = true  // 是否为当前显示的版本
)
