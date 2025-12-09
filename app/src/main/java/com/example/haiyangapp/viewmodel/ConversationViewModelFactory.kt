package com.example.haiyangapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.haiyangapp.repository.ChatRepository

/**
 * ConversationViewModel工厂
 * 负责创建ConversationViewModel实例并注入依赖
 */
class ConversationViewModelFactory(//工厂只负责是否创建
    private val repository: ChatRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConversationViewModel::class.java)) {
            return ConversationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
