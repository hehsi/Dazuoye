package com.example.haiyangapp.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户实体类
 * 存储用户的账号、密码等信息
 */
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)] // 确保用户名唯一
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,           // 用户名（唯一）
    val password: String,           // 密码（实际应用中应加密存储）
    val createdAt: Long,           // 创建时间
    val lastLoginAt: Long = 0      // 最后登录时间
)
