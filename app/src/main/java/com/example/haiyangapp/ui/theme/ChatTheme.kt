package com.example.haiyangapp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.haiyangapp.R

/**
 * 聊天界面主题配置
 * 提供统一的颜色、尺寸和字符串资源访问
 */
object ChatTheme {

    /**
     * 颜色资源
     */
    object Colors {
        val primaryBlue: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_blue)
        val primaryBlueLight: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_blue_light)
        val primaryBlueVariant: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_blue_variant)
        val primaryBlueLiked: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.primary_blue_liked)

        val backgroundWhite: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_white)
        val backgroundLightGray: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_light_gray)
        val backgroundVeryLightGray: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_very_light_gray)
        val backgroundUserMessage: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_user_message)
        val backgroundAiMessage: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_ai_message)
        val backgroundAvatar: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_avatar)
        val backgroundLikedMessage: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.background_liked_message)

        val editingBackground: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_background)
        val editingBorder: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_border)
        val editingBorderAlpha: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_border_alpha)
        val editingIconBackground: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_icon_background)
        val editingTextPrimary: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_text_primary)
        val editingTextSecondary: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_text_secondary)
        val editingTextDark: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_text_dark)
        val editingButton: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.editing_button)

        val textPrimary: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_primary)
        val textSecondary: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_secondary)
        val textTertiary: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_tertiary)
        val textHint: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_hint)
        val textDisabled: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_disabled)
        val textWhite: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.text_white)

        val dividerLight: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.divider_light)
        val dividerVeryLight: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.divider_very_light)
        val borderLight: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.border_light)
        val borderLiked: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.border_liked)
        val borderEditing: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.border_editing)

        val accentOrange: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.accent_orange)
        val accentOrangeShadow: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.accent_orange_shadow)
        val accentRed: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.accent_red)

        val shadowEditingSpot: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.shadow_editing_spot)
        val shadowEditingAmbient: Color @Composable @ReadOnlyComposable get() = colorResource(R.color.shadow_editing_ambient)
    }

    /**
     * 尺寸资源
     */
    object Dimens {
        val spacingTiny: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_tiny)
        val spacingSmall: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_small)
        val spacingMedium: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_medium)
        val spacingNormal: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_normal)
        val spacingLarge: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_large)
        val spacingXLarge: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.spacing_xlarge)

        val textSizeTiny: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_tiny).value.sp
        val textSizeSmall: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_small).value.sp
        val textSizeCaption: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_caption).value.sp
        val textSizeBodySmall: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_body_small).value.sp
        val textSizeBody: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_body).value.sp
        val textSizeTitle: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_title).value.sp
        val textSizeHeading: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_heading).value.sp
        val textSizeLargeHeading: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_large_heading).value.sp
        val textSizeEmoji: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_emoji).value.sp
        val textSizeAvatarEmoji: TextUnit @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.text_size_avatar_emoji).value.sp

        val iconSizeTiny: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_tiny)
        val iconSizeSmall: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_small)
        val iconSizeMedium: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_medium)
        val iconSizeLarge: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_large)
        val iconSizeButton: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_button)
        val iconSizeAvatar: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.icon_size_avatar)

        val cornerRadiusSmall: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.corner_radius_small)
        val cornerRadiusMedium: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.corner_radius_medium)
        val cornerRadiusNormal: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.corner_radius_normal)
        val cornerRadiusLarge: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.corner_radius_large)

        val messageBubbleMaxWidth: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.message_bubble_max_width)
        val messageBubblePadding: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.message_bubble_padding)
        val messageBubbleBorderWidth: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.message_bubble_border_width)
        val messageBubbleBorderEditing: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.message_bubble_border_editing)

        val inputFieldHeight: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.input_field_height)
        val quickActionButtonWidth: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.quick_action_button_width)

        val elevationSmall: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.elevation_small)
        val elevationMedium: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.elevation_medium)
        val elevationLarge: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.elevation_large)

        val dividerThicknessThin: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.divider_thickness_thin)
        val dividerThicknessNormal: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.divider_thickness_normal)

        val progressIndicatorSize: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.progress_indicator_size)
        val progressIndicatorStroke: Dp @Composable @ReadOnlyComposable get() = dimensionResource(R.dimen.progress_indicator_stroke)
    }

    /**
     * 字符串资源
     */
    object Strings {
        val appName: String @Composable @ReadOnlyComposable get() = stringResource(R.string.app_name)

        val chatTitleNew: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_title_new)
        val chatSubtitle: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_subtitle)
        val chatInputHint: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_input_hint)
        val chatInputHintEditing: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_input_hint_editing)
        val chatSendButtonDesc: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_send_button_desc)
        val chatResendButtonDesc: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_resend_button_desc)
        val chatThinking: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_thinking)
        val chatCopiedToClipboard: String @Composable @ReadOnlyComposable get() = stringResource(R.string.chat_copied_to_clipboard)

        val welcomeMessage: String @Composable @ReadOnlyComposable get() = stringResource(R.string.welcome_message)

        val suggestion1: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestion_1)
        val suggestion2: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestion_2)
        val suggestion3: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestion_3)
        val suggestion4: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestion_4)
        val suggestion5: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestion_5)

        val quickActionDeepThinking: String @Composable @ReadOnlyComposable get() = stringResource(R.string.quick_action_deep_thinking)
        val quickActionAiArt: String @Composable @ReadOnlyComposable get() = stringResource(R.string.quick_action_ai_art)
        val quickActionPhotoQa: String @Composable @ReadOnlyComposable get() = stringResource(R.string.quick_action_photo_qa)
        val quickActionAiCreative: String @Composable @ReadOnlyComposable get() = stringResource(R.string.quick_action_ai_creative)

        val menuCopy: String @Composable @ReadOnlyComposable get() = stringResource(R.string.menu_copy)
        val menuEdit: String @Composable @ReadOnlyComposable get() = stringResource(R.string.menu_edit)
        val menuLike: String @Composable @ReadOnlyComposable get() = stringResource(R.string.menu_like)
        val menuUnlike: String @Composable @ReadOnlyComposable get() = stringResource(R.string.menu_unlike)
        val messageLiked: String @Composable @ReadOnlyComposable get() = stringResource(R.string.message_liked)

        val editingTitle: String @Composable @ReadOnlyComposable get() = stringResource(R.string.editing_title)
        val editingHint: String @Composable @ReadOnlyComposable get() = stringResource(R.string.editing_hint)
        val editingCancel: String @Composable @ReadOnlyComposable get() = stringResource(R.string.editing_cancel)

        val conversationListTitle: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_list_title)
        val conversationNew: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_new)
        val conversationDelete: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_delete)
        val conversationDeleteTitle: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_delete_title)
        val conversationDeleteConfirm: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_delete_confirm)
        val conversationDeleteCancel: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_delete_cancel)
        val conversationEmpty: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_empty)
        val conversationStartNew: String @Composable @ReadOnlyComposable get() = stringResource(R.string.conversation_start_new)

        val descAiAvatar: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_ai_avatar)
        val descMenu: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_menu)
        val descMore: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_more)
        val descCamera: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_camera)
        val descLiked: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_liked)
        val descNewConversation: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_new_conversation)
        val descDeleteConversation: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_delete_conversation)

        val actionClose: String @Composable @ReadOnlyComposable get() = stringResource(R.string.action_close)

        val modelLocal: String @Composable @ReadOnlyComposable get() = stringResource(R.string.model_local)
        val modelRemote: String @Composable @ReadOnlyComposable get() = stringResource(R.string.model_remote)
        val descModelSelect: String @Composable @ReadOnlyComposable get() = stringResource(R.string.desc_model_select)

        val suggestionsLoading: String @Composable @ReadOnlyComposable get() = stringResource(R.string.suggestions_loading)
    }
}

/**
 * UI常量（不需要Context的常量）
 */
object ChatConstants {
    const val AI_AVATAR_EMOJI = "🤖"

    val SUGGESTION_LIST = listOf(
        "suggestion_1",
        "suggestion_2",
        "suggestion_3",
        "suggestion_4",
        "suggestion_5"
    )

    val QUICK_ACTION_ICONS = listOf(
        "🧠",
        "🎨",
        "📷",
        "✨"
    )
}
