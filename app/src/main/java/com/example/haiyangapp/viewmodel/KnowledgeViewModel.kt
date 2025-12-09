package com.example.haiyangapp.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.haiyangapp.database.AppDatabase
import com.example.haiyangapp.database.entity.KnowledgeDocumentEntity
import com.example.haiyangapp.knowledge.EmbeddingManager
import com.example.haiyangapp.knowledge.KnowledgeRepository
import com.example.haiyangapp.knowledge.KnowledgeRepositoryImpl
import com.example.haiyangapp.knowledge.RetrievalResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 知识库 ViewModel
 * 管理知识库文档的导入、删除和检索
 */
class KnowledgeViewModel(
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    /** 所有文档列表 */
    val documents: StateFlow<List<KnowledgeDocumentEntity>> = knowledgeRepository
        .getAllDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 索引进度 */
    val indexingProgress: StateFlow<Map<Long, Float>> = knowledgeRepository.getIndexingProgress()

    /** 操作状态 */
    sealed class OperationState {
        object Idle : OperationState()
        object Loading : OperationState()
        data class Success(val message: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    /** 导入对话框显示状态 */
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()

    /**
     * 显示导入对话框
     */
    fun showImportDialog() {
        _showImportDialog.value = true
    }

    /**
     * 隐藏导入对话框
     */
    fun hideImportDialog() {
        _showImportDialog.value = false
    }

    /**
     * 导入文档
     *
     * @param uri 文档 URI
     * @param title 文档标题（可选）
     */
    fun importDocument(uri: Uri, title: String? = null) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            hideImportDialog()

            val result = knowledgeRepository.addDocument(uri, title)

            result.fold(
                onSuccess = { documentId ->
                    _operationState.value = OperationState.Success("文档导入成功")
                },
                onFailure = { exception ->
                    _operationState.value = OperationState.Error(
                        exception.message ?: "导入失败"
                    )
                }
            )
        }
    }

    /**
     * 删除文档
     *
     * @param documentId 文档 ID
     */
    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading

            try {
                knowledgeRepository.deleteDocument(documentId)
                _operationState.value = OperationState.Success("文档已删除")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error(
                    e.message ?: "删除失败"
                )
            }
        }
    }

    /**
     * 清除操作状态（用于显示完成后重置）
     */
    fun clearOperationState() {
        _operationState.value = OperationState.Idle
    }

    /**
     * 检索相关知识
     *
     * @param query 查询文本
     * @param topK 返回数量
     * @return 检索结果
     */
    suspend fun searchRelevantChunks(query: String, topK: Int = 3): List<RetrievalResult> {
        return knowledgeRepository.searchRelevantChunks(query, topK)
    }

    /**
     * 检查是否有已索引的文档
     */
    suspend fun hasIndexedDocuments(): Boolean {
        return knowledgeRepository.hasIndexedDocuments()
    }

    /**
     * 获取文档数量
     */
    fun getDocumentCount(): Int {
        return documents.value.size
    }

    /**
     * 获取已索引的文档数量
     */
    fun getIndexedDocumentCount(): Int {
        return documents.value.count { it.isIndexed }
    }

    companion object {
        @Volatile
        private var embeddingManager: EmbeddingManager? = null
        @Volatile
        private var knowledgeRepository: KnowledgeRepository? = null

        /**
         * 获取或创建 EmbeddingManager 单例
         */
        fun getEmbeddingManager(context: Context): EmbeddingManager {
            return embeddingManager ?: synchronized(this) {
                embeddingManager ?: EmbeddingManager(context.applicationContext).also {
                    embeddingManager = it
                }
            }
        }

        /**
         * 获取或创建 KnowledgeRepository 单例
         */
        fun getKnowledgeRepository(context: Context): KnowledgeRepository {
            return knowledgeRepository ?: synchronized(this) {
                knowledgeRepository ?: run {
                    val database = AppDatabase.getDatabase(context)
                    val manager = getEmbeddingManager(context)
                    KnowledgeRepositoryImpl(
                        context = context.applicationContext,
                        knowledgeDao = database.knowledgeDao(),
                        embeddingManager = manager
                    ).also { knowledgeRepository = it }
                }
            }
        }
    }
}

/**
 * KnowledgeViewModel 工厂类
 */
class KnowledgeViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KnowledgeViewModel::class.java)) {
            val repository = KnowledgeViewModel.getKnowledgeRepository(context)
            return KnowledgeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
