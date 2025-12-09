package com.example.haiyangapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.haiyangapp.ui.theme.ChatTheme

/**
 * Markdown text rendering component
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ChatTheme.Colors.textPrimary,
    fontSize: TextUnit = ChatTheme.Dimens.textSizeBody
) {
    val parsedContent = remember(text) { parseMarkdown(text) }

    Column(modifier = modifier) {
        parsedContent.forEach { block ->
            when (block) {
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownBlock.TextBlock -> {
                    Text(
                        text = block.annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = fontSize,
                            color = color
                        )
                    )
                }
                is MarkdownBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = when (block.level) {
                                1 -> 20.sp
                                2 -> 18.sp
                                else -> 16.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MarkdownBlock.ListItem -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = if (block.ordered) "${block.index}. " else "\u2022 ",
                            color = color,
                            fontSize = fontSize
                        )
                        Text(
                            text = block.annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSize,
                                color = color
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Code block view
 */
@Composable
private fun CodeBlockView(
    code: String,
    language: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(12.dp)
    ) {
        if (!language.isNullOrEmpty()) {
            Text(
                text = language,
                fontSize = 10.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Box(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFD4D4D4),
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Markdown block types
 */
sealed class MarkdownBlock {
    data class TextBlock(val annotatedString: AnnotatedString) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class ListItem(
        val annotatedString: AnnotatedString,
        val ordered: Boolean,
        val index: Int
    ) : MarkdownBlock()
}

/**
 * Parse Markdown text into blocks
 */
private fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")

    var i = 0
    var listIndex = 0

    while (i < lines.size) {
        val line = lines[i]

        // Check code block
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++

            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }

            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language.ifEmpty { null }))
            i++
            continue
        }

        // Check heading
        val headingMatch = Regex("^(#{1,3})\\s+(.+)$").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            blocks.add(MarkdownBlock.Heading(headingText, level))
            i++
            continue
        }

        // Check unordered list
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val content = line.trimStart().removePrefix("- ").removePrefix("* ")
            blocks.add(MarkdownBlock.ListItem(parseInlineMarkdown(content), false, 0))
            i++
            continue
        }

        // Check ordered list
        val orderedListMatch = Regex("^(\\d+)\\.\\s+(.+)$").find(line.trimStart())
        if (orderedListMatch != null) {
            listIndex++
            val content = orderedListMatch.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(parseInlineMarkdown(content), true, listIndex))
            i++
            continue
        } else {
            listIndex = 0
        }

        // Plain text or empty line
        if (line.isNotBlank()) {
            blocks.add(MarkdownBlock.TextBlock(parseInlineMarkdown(line)))
        }
        i++
    }

    return blocks
}

/**
 * Parse inline Markdown syntax
 */
private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val patterns = listOf(
            // Bold **text** or __text__
            Regex("\\*\\*(.+?)\\*\\*|__(.+?)__") to { match: MatchResult ->
                val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(content)
                }
            },
            // Italic *text* or _text_
            Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)") to { match: MatchResult ->
                val content = match.groupValues[1].ifEmpty { match.groupValues[2] }
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
            },
            // Strikethrough ~~text~~
            Regex("~~(.+?)~~") to { match: MatchResult ->
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(match.groupValues[1])
                }
            },
            // Inline code `code`
            Regex("`([^`]+)`") to { match: MatchResult ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFFE8E8E8),
                        color = Color(0xFFD32F2F)
                    )
                ) {
                    append(" ${match.groupValues[1]} ")
                }
            },
            // Link [text](url)
            Regex("\\[(.+?)]\\((.+?)\\)") to { match: MatchResult ->
                withStyle(
                    SpanStyle(
                        color = Color(0xFF1976D2),
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(match.groupValues[1])
                }
            }
        )

        data class MatchInfo(
            val range: IntRange,
            val replacement: AnnotatedString.Builder.() -> Unit,
            val originalLength: Int
        )

        val allMatches = mutableListOf<MatchInfo>()

        for ((pattern, handler) in patterns) {
            pattern.findAll(text).forEach { match ->
                allMatches.add(
                    MatchInfo(
                        range = match.range,
                        replacement = { handler(match) },
                        originalLength = match.value.length
                    )
                )
            }
        }

        // Sort by position
        allMatches.sortBy { it.range.first }

        // Remove overlapping matches
        val filteredMatches = mutableListOf<MatchInfo>()
        var lastEnd = -1
        for (match in allMatches) {
            if (match.range.first > lastEnd) {
                filteredMatches.add(match)
                lastEnd = match.range.last
            }
        }

        // Build AnnotatedString
        var pos = 0
        for (match in filteredMatches) {
            if (pos < match.range.first) {
                append(text.substring(pos, match.range.first))
            }
            match.replacement(this)
            pos = match.range.last + 1
        }

        // Append remaining text
        if (pos < text.length) {
            append(text.substring(pos))
        }
    }
}
