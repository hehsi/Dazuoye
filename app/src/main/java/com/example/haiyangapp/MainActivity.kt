package com.example.haiyangapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.haiyangapp.repository.RepositoryFactory
import com.example.haiyangapp.ui.navigation.AppNavGraph
import com.example.haiyangapp.ui.navigation.NavRoutes
import com.example.haiyangapp.viewmodel.ChatViewModel
import com.example.haiyangapp.ui.ChatViewModelFactory
import com.example.haiyangapp.viewmodel.ConversationViewModel
import com.example.haiyangapp.viewmodel.ConversationViewModelFactory
import com.example.haiyangapp.inference.ModelLoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 立即启动后台模型加载（非阻塞）
        RepositoryFactory.startBackgroundModelLoading(applicationContext)

        setContent {
            MaterialTheme {
                Surface {
                    // 使用RepositoryFactory创建repository
                    // 使用 LOCAL 模式进行本地推理
                    val repository = remember {
                        RepositoryFactory.getChatRepository(
                            context = applicationContext,
                            mode = RepositoryFactory.InferenceMode.LOCAL
                        )
                    }

                    // 直接显示主界面，不等待模型加载
                    ChatApp(repository = repository, context = applicationContext)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        RepositoryFactory.release()
    }
}

@Composable
fun ChatApp(
    repository: com.example.haiyangapp.repository.ChatRepository,
    context: android.content.Context
) {
    // 创建ViewModels
    val conversationViewModel: ConversationViewModel = viewModel(
        factory = ConversationViewModelFactory(repository)
    )

    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(repository, context)
    )

    // 导航控制器
    val navController = rememberNavController()

    // 确定初始路由和对话ID
    var startDestination by remember { mutableStateOf<String?>(null) }

    // 启动时获取或复用空对话（避免创建多余的空对话）
    LaunchedEffect(Unit) {
        val conversationId = withContext(Dispatchers.IO) {
            // 优先复用已有的空对话，没有才创建新对话
            repository.getOrCreateEmptyConversation()
        }

        // 加载对话
        chatViewModel.loadConversation(conversationId)
        startDestination = NavRoutes.chatRoute(conversationId)
    }

    // 等待初始路由确定后再显示导航
    startDestination?.let { destination ->
        AppNavGraph(
            navController = navController,
            conversationViewModel = conversationViewModel,
            chatViewModel = chatViewModel,
            startDestination = destination
        )
    }
}
