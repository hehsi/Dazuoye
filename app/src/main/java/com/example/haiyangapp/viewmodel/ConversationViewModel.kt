package com.example.haiyangapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haiyangapp.database.entity.ConversationEntity
import com.example.haiyangapp.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 对话列表ViewModel
 * 管理所有对话的列表状态和操作
 */
class ConversationViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    // 所有对话列表
    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadConversations()
    }

    /**
     * 加载所有对话
     */
    private fun loadConversations() {
        viewModelScope.launch {
            try {
                repository.getAllConversations().collect { conversationList ->
                    _conversations.value = conversationList
                }
            } catch (e: Exception) {
                _errorMessage.value = "加载对话列表失败: ${e.message}"
            }
        }
    }

    /**
     * 创建新对话
     * @return 新对话的ID
     */
    suspend fun createNewConversation(): Long {
        return try {
            repository.createConversation("新对话")
        } catch (e: Exception) {
            _errorMessage.value = "创建对话失败: ${e.message}"
            -1L
        }
    }

    /**
     * 删除对话
     * @param conversationId 要删除的对话ID
     */
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteConversation(conversationId)
            } catch (e: Exception) {
                _errorMessage.value = "删除对话失败: ${e.message}"
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
