package com.example.haiyangapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haiyangapp.database.entity.UserEntity
import com.example.haiyangapp.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 认证状态
 */
sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserEntity) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * 认证ViewModel
 * 处理登录和注册逻辑
 */
class AuthViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * 登录
     */
    fun login(username: String, password: String) {
        // 验证输入
        if (username.isBlank()) {
            _authState.value = AuthState.Error("请输入用户名")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthState.Error("请输入密码")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading

            val result = userRepository.login(username, password)

            _isLoading.value = false
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "登录失败")
            }
        }
    }

    /**
     * 注册
     */
    fun register(username: String, password: String, confirmPassword: String) {
        // 验证输入
        if (username.isBlank()) {
            _authState.value = AuthState.Error("请输入用户名")
            return
        }
        if (username.length < 3) {
            _authState.value = AuthState.Error("用户名至少3个字符")
            return
        }
        if (password.isBlank()) {
            _authState.value = AuthState.Error("请输入密码")
            return
        }
        if (password.length < 6) {
            _authState.value = AuthState.Error("密码至少6个字符")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("两次密码不一致")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _authState.value = AuthState.Loading

            val result = userRepository.register(username, password)

            _isLoading.value = false
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "注册失败")
            }
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
