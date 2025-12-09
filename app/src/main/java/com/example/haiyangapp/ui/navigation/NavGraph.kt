package com.example.haiyangapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.haiyangapp.ui.ChatScreen
import com.example.haiyangapp.ui.ConversationListScreen
import com.example.haiyangapp.ui.KnowledgeBaseScreen
import com.example.haiyangapp.viewmodel.ChatViewModel
import com.example.haiyangapp.viewmodel.ConversationViewModel

/**
 * 导航路由定义
 */
object NavRoutes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val KNOWLEDGE_BASE = "knowledge_base"

    fun chatRoute(conversationId: Long) = "chat/$conversationId"
}

/**
 * 应用导航图
 * 定义所有导航目的地和路由
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    conversationViewModel: ConversationViewModel,
    chatViewModel: ChatViewModel,
    startDestination: String = NavRoutes.CONVERSATION_LIST
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 对话列表页面
        composable(NavRoutes.CONVERSATION_LIST) {
            ConversationListScreen(
                viewModel = conversationViewModel,
                onConversationSelected = { conversationId ->
                    // 加载对话并导航到聊天页面
                    chatViewModel.loadConversation(conversationId)
                    navController.navigate(NavRoutes.chatRoute(conversationId)) {
                        // 避免返回时重复添加相同页面
                        launchSingleTop = true
                    }
                },
                onCreateNewConversation = {
                    conversationViewModel.createNewConversation()
                }
            )
        }

        // 聊天页面
        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: return@composable

            ChatScreen(
                viewModel = chatViewModel,
                conversationId = conversationId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToConversationList = {
                    navController.navigate(NavRoutes.CONVERSATION_LIST) {
                        // 清空返回栈到对话列表
                        popUpTo(NavRoutes.CONVERSATION_LIST) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                conversationViewModel = conversationViewModel,
                onConversationSelected = { selectedId ->
                    // 加载对话并导航
                    chatViewModel.loadConversation(selectedId)
                    navController.navigate(NavRoutes.chatRoute(selectedId)) {
                        // 替换当前聊天页面
                        popUpTo(NavRoutes.CHAT) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onNavigateToKnowledgeBase = {
                    navController.navigate(NavRoutes.KNOWLEDGE_BASE)
                }
            )
        }

        // 知识库管理页面
        composable(NavRoutes.KNOWLEDGE_BASE) {
            KnowledgeBaseScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
