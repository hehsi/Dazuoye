package com.example.haiyangapp.repository

import com.example.haiyangapp.database.dao.UserDao
import com.example.haiyangapp.database.entity.UserEntity

/**
 * 用户仓库接口
 */
interface UserRepository {
    ////函数返回一个 Result 对象，如果成功，里面包含 UserEntity 类型的数据；如果失败，里面包含错误信息。
    suspend fun login(username: String, password: String): Result<UserEntity> 
    suspend fun register(username: String, password: String): Result<UserEntity>
    suspend fun isUsernameExists(username: String): Boolean
    suspend fun updateLastLoginTime(userId: Long)
}

/**
 * 用户仓库实现类
 */
class UserRepositoryImpl(
    private val userDao: UserDao
) : UserRepository {

    override suspend fun login(username: String, password: String): Result<UserEntity> {
        return try {
            val user = userDao.validateUser(username, password)
            if (user != null) {
                // 更新最后登录时间
                updateLastLoginTime(user.id)
                Result.success(user)
            } else {
                Result.failure(Exception("用户名或密码错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, password: String): Result<UserEntity> {
        return try {
            // 检查用户名是否已存在
            if (userDao.isUsernameExists(username)) {
                return Result.failure(Exception("用户名已存在"))
            }

            // 创建新用户
            val newUser = UserEntity(
                username = username,
                password = password, // 注意：实际应用中应该加密存储
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )

            val userId = userDao.insertUser(newUser)
            if (userId > 0) {
                Result.success(newUser.copy(id = userId))
            } else {
                Result.failure(Exception("注册失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isUsernameExists(username: String): Boolean {
        return try {
            userDao.isUsernameExists(username)
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun updateLastLoginTime(userId: Long) {
        try {
            userDao.updateLastLoginTime(userId, System.currentTimeMillis())
        } catch (e: Exception) {
            // 忽略错误
        }
    }
}