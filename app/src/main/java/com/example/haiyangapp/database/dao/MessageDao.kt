package com.example.haiyangapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.haiyangapp.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // 只获取当前版本的消息（用于UI显示）
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND isCurrentVersion = 1 ORDER BY timestamp ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

    // 获取所有消息包括非当前版本（用于调试或导出）
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getAllMessagesByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: Long): MessageEntity?

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isLiked = :isLiked WHERE id = :messageId")
    suspend fun updateLikeStatus(messageId: Long, isLiked: Boolean)

    @Query("UPDATE messages SET content = :content WHERE id = :messageId")
    suspend fun updateContent(messageId: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long)

    /**
     * 获取最近的用户消息（用于生成推荐问题）
     */
    @Query("SELECT * FROM messages WHERE isFromUser = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentUserMessages(limit: Int): List<MessageEntity>

    /**
     * 获取指定对话的消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int

    // ==================== 重新生成版本管理 ====================

    /**
     * 获取某个重新生成组的所有版本
     * @param groupId 组标识（对应用户消息的ID）
     */
    @Query("SELECT * FROM messages WHERE regenerateGroupId = :groupId ORDER BY versionIndex ASC")
    suspend fun getVersionsByGroup(groupId: Long): List<MessageEntity>

    /**
     * 获取某个重新生成组的版本总数
     */
    @Query("SELECT COUNT(*) FROM messages WHERE regenerateGroupId = :groupId")
    suspend fun getVersionCount(groupId: Long): Int

    /**
     * 获取某个重新生成组的最大版本号
     */
    @Query("SELECT MAX(versionIndex) FROM messages WHERE regenerateGroupId = :groupId")
    suspend fun getMaxVersionIndex(groupId: Long): Int?

    /**
     * 将某组的所有消息设为非当前版本
     */
    @Query("UPDATE messages SET isCurrentVersion = 0 WHERE regenerateGroupId = :groupId")
    suspend fun clearCurrentVersionInGroup(groupId: Long)

    /**
     * 将指定消息设为当前版本
     */
    @Query("UPDATE messages SET isCurrentVersion = 1 WHERE id = :messageId")
    suspend fun setAsCurrentVersion(messageId: Long)

    /**
     * 根据ID获取单条消息
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): MessageEntity?

    /**
     * 将原始消息初始化为版本组的第一个版本
     * 用于第一次重新生成时，把原始消息加入版本组
     */
    @Query("UPDATE messages SET regenerateGroupId = :groupId, versionIndex = 0, isCurrentVersion = 0 WHERE id = :messageId")
    suspend fun initializeAsFirstVersion(messageId: Long, groupId: Long)
}
