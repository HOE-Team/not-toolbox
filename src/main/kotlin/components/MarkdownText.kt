// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)

package components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState

/**
 * 渲染 Markdown 文本（标题、列表、引用、表格、代码块、链接、任务清单等）。
 *
 * 底层用 `com.mikepenz:multiplatform-markdown-renderer`：它与 Markwon 同源，
 * 桌面端由 JetBrains Markdown（commonmark 解析器）解析、Compose 原生绘制，
 * 不依赖 WebView 与 Android 组件。
 *
 * 配色全部取自当前主题（正文色继承 [LocalContentColor]，因此放在任何 Surface 里都能自适应），
 * 而不是用库里的 MaterialTheme.onBackground 默认值——气泡底色不是 background，用默认值会与气泡对比度不足。
 *
 * @param text Markdown 源文本
 * @param modifier 布局修饰符
 * @param textStyle 正文（段落 / 列表 / 表格文字）字号样式，默认 bodyMedium
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    // 流式输出时文本每几十毫秒就变一次。retainState 让「重新解析期间继续显示上一版内容」，
    // 否则每次增量都会先回到 Loading 状态，气泡会不停闪烁；解析走 snapshotFlow + conflate，
    // 增量过快时自动合并，不会堆积解析任务。
    val state = rememberMarkdownState(content = text, retainState = true)

    // 主题底色亮度决定深/浅配色（应用内可手动切换深浅色，不能依赖 isSystemInDarkTheme）
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val content = LocalContentColor.current

    val colors = markdownColor(
        text = content,
        codeBackground = content.copy(alpha = 0.08f),
        inlineCodeBackground = content.copy(alpha = 0.08f),
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
        tableBackground = content.copy(alpha = 0.04f),
        darkTheme = darkTheme
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.titleLarge,
        h2 = MaterialTheme.typography.titleMedium,
        h3 = MaterialTheme.typography.titleSmall,
        h4 = textStyle.copy(fontWeight = FontWeight.SemiBold),
        h5 = textStyle.copy(fontWeight = FontWeight.SemiBold),
        h6 = textStyle.copy(fontWeight = FontWeight.SemiBold),
        text = textStyle,
        paragraph = textStyle,
        list = textStyle,
        ordered = textStyle,
        bullet = textStyle,
        quote = textStyle.copy(fontStyle = FontStyle.Italic),
        code = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        inlineCode = textStyle.copy(fontFamily = FontFamily.Monospace),
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ),
        table = MaterialTheme.typography.bodySmall
    )

    // 代码块同步高亮；表头由库自带的代码块顶栏提供（语言 + 复制按钮）
    val components = remember(typography) {
        markdownComponents(
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
            codeFence = { model ->
                MarkdownHighlightedCodeFence(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    showHeader = true
                )
            },
            codeBlock = { model ->
                MarkdownHighlightedCodeBlock(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    showHeader = true
                )
            }
        )
    }

    SelectionContainer {
        Markdown(
            markdownState = state,
            colors = colors,
            typography = typography,
            components = components,
            modifier = modifier
        )
    }
}
