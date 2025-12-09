package com.example.haiyangapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.haiyangapp.database.dao.ConversationDao
import com.example.haiyangapp.database.dao.KnowledgeDao
import com.example.haiyangapp.database.dao.MessageDao
import com.example.haiyangapp.database.dao.UserDao
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.database.entity.KnowledgeChunkEntity
import com.example.haiyangapp.database.entity.KnowledgeDocumentEntity
import com.example.haiyangapp.database.entity.MessageEntity
import com.example.haiyangapp.database.entity.UserEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        UserEntity::class,
        KnowledgeDocumentEntity::class,
        KnowledgeChunkEntity::class
    ],
    version = 4,  // 升级版本：添加知识库功能
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haiyang_chat_database"
                )
                    .fallbackToDestructiveMigration() // 简化迁移，实际应用中应使用Migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
