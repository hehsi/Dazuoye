package com.example.haiyangapp.database.dao

import androidx.room.*
import com.example.haiyangapp.database.entity.UserEntity

/**
 * 用户数据访问对象
 * 提供用户相关的数据库操作
 */
@Dao
interface UserDao {

    /**
     * 根据用户名查询用户
     */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?

    /**
     * 根据用户ID查询用户
     */
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Long): UserEntity?

    /**
     * 插入新用户
     * @return 新插入用户的ID
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    /**
     * 更新用户信息
     */
    @Update
    suspend fun updateUser(user: UserEntity)

    /**
     * 更新最后登录时间
     */
    @Query("UPDATE users SET lastLoginAt = :timestamp WHERE id = :userId")
    suspend fun updateLastLoginTime(userId: Long, timestamp: Long)

    /**
     * 检查用户名是否存在
     */
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :username)")
    suspend fun isUsernameExists(username: String): Boolean

    /**
     * 删除用户
     */
    @Delete
    suspend fun deleteUser(user: UserEntity)

    /**
     * 验证用户名和密码
     */
    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun validateUser(username: String, password: String): UserEntity?
}
