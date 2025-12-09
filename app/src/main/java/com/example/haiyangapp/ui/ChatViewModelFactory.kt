package com.example.haiyangapp.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.haiyangapp.repository.ChatRepository
import com.example.haiyangapp.viewmodel.ChatViewModel

/**
 * ChatViewModel的工厂类
 * 用于创建带有依赖注入的ViewModel实例
 */
class ChatViewModelFactory(
    private val repository: ChatRepository,
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
