package com.example.haiyangapp.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.haiyangapp.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: Long): ConversationEntity?

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessageAt = :timestamp WHERE id = :conversationId")
    suspend fun updateLastMessageTime(conversationId: Long, timestamp: Long)

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateTitle(conversationId: Long, title: String)
}
