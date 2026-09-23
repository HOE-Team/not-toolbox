// SPDX-FileCopyrightText: ©2026 HOE Team
// SPDX-License-Identifier: GPL-3.0-only
//
// Project: NOT Toolbox
// Based on: NNETB (©2026 HOE Team, MIT License) and NNETB-For-Linux (©2026 HOE Team, GPL-3.0 License)
// License: GPL-3.0 (see LICENSE file for details)
//
// 工具调用标记的解析与流式隐藏。
//
// 格式由本应用自己的提示词规定（见 ToolPrompt），因此解析器**不做**宽容兼容：
// 标记就是 [TOOL_CALL]，标记后紧跟单行 JSON。写错格式的调用会被当作「格式错误」交给界面展示，
// 而不是猜测用户的意图（以前那套多括号 / 代码围栏 / 半标记猜测的逻辑既难维护又容易被误触发）。

package utils.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 工具调用标记 */
const val TOOL_CALL_MARKER = "[TOOL_CALL]"

object ToolCodec {
    private val parseJson = Json { ignoreUnknownKeys = true }

    /** 一行的调用载荷：去掉前导空白后以标记开头则返回标记之后的内容，否则返回 null */
    internal fun payloadOfLine(line: String): String? {
        val trimmed = line.trimStart()
        if (!trimmed.startsWith(TOOL_CALL_MARKER)) return null
        return trimmed.substring(TOOL_CALL_MARKER.length).trim()
    }

    /** 从模型输出中解析出工具调用（只认格式正确的调用行，按出现顺序） */
    fun parseCalls(text: String): List<ToolCall> =
        text.split('\n').mapNotNull { line -> payloadOfLine(line)?.let { toCall(it) } }

    /**
     * 格式错误的调用原文（标记写了但 JSON 不合法 / 缺字段）。
     * 这些内容同样要从可见正文里剔除，交给界面当错误卡片展示。
     */
    fun malformedCalls(text: String): List<String> =
        text.split('\n').mapNotNull { line ->
            val payload = payloadOfLine(line) ?: return@mapNotNull null
            if (toCall(payload) == null) line.trim() else null
        }

    /** 去掉正文里所有调用行（无论是否解析成功），只保留正文 */
    fun visibleText(text: String): String =
        text.split('\n').filterNot { payloadOfLine(it) != null }.joinToString("\n").trim()

    /**
     * 流式场景：把累积原文切成「可以安全显示的正文」与「需要暂存的尾部」。
     *
     * 只有两种尾部需要暂存（其余内容立即可见）：
     * 1. 最后一个调用块的 JSON 还没闭合（参数正在生成，甚至被模型换行写了）
     * 2. 尾部是标记本身的前缀（如 `[TOOL`，标记写了一半）
     *
     * 已闭合的调用行不在这里处理，由 [visibleText] 直接整行剔除。
     */
    fun splitForStreaming(text: String): Pair<String, String> {
        val cut = holdStart(text)
        return visibleText(text.substring(0, cut)) to text.substring(cut)
    }

    private fun holdStart(text: String): Int {
        val marker = text.lastIndexOf(TOOL_CALL_MARKER)
        if (marker >= 0) {
            val jsonStart = text.indexOf('{', marker + TOOL_CALL_MARKER.length)
            // 标记写全了但 JSON 还没开始，或花括号还没配对 → 从标记所在行起整段暂存
            if (jsonStart < 0 || matchBrace(text, jsonStart) < 0) return lineStart(text, marker)
        }
        val tailStart = lineStart(text, text.length)
        val tail = text.substring(tailStart)
        val trimmed = tail.trimStart()
        if (trimmed.isNotEmpty() && trimmed.length <= TOOL_CALL_MARKER.length &&
            TOOL_CALL_MARKER.startsWith(trimmed)
        ) {
            return tailStart
        }
        return text.length
    }

    /** [index] 所在行的起始下标 */
    private fun lineStart(text: String, index: Int): Int =
        text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    /** 从 [braceStart] 处的 `{` 找配对的 `}`（正确处理字符串与转义）；找不到返回 -1 */
    internal fun matchBrace(text: CharSequence, braceStart: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
        }
        return -1
    }

    /** 把一个调用载荷解析成 [ToolCall]；参数体是载荷里的第一个 JSON 对象 */
    private fun toCall(payload: String): ToolCall? {
        val braceStart = payload.indexOf('{')
        if (braceStart < 0) return null
        val obj = try {
            parseJson.parseToJsonElement(payload.substring(braceStart)).jsonObject
        } catch (_: Exception) {
            return null
        }
        val tool = obj["tool"]?.jsonPrimitive?.contentOrNull
        if (tool.isNullOrBlank()) return null
        val args = (obj["arguments"] as? JsonObject)?.entries?.associate { it.key to it.value } ?: emptyMap()
        return ToolCall(tool = tool, arguments = args)
    }

    /**
     * 流式增量扫描器：把分块依次喂进来，**只处理新增部分**。
     *
     * 旧实现每收到一个分块就把整段累积文本重扫一遍（`split('\n')` + `joinToString` + 逐行 JSON 解析），
     * 长回答下是二次方开销；这里改成「整行/整块结算」：
     * - 收到换行且该行不是调用行 → 立刻并入可见正文
     * - 调用块（可能被模型换行写）：JSON 闭口后整块剔除并记为一个调用，未闭口则连行一起留在尾部等下一块
     *
     * 注意：这里只服务于**流式显示**与「提前建工具卡片」。真正的执行仍以回合结束时的
     * [parseCalls] / [malformedCalls]（对整段原文做一次权威解析）为准。
     */
    class StreamScanner {
        private val raw = StringBuilder()
        private val settledVisible = StringBuilder()
        private var settledUpTo = 0
        private val calls = mutableListOf<ToolCall>()
        private val malformed = mutableListOf<String>()

        /**
         * 上一条调用块结算时后面还没跟换行。
         * 分块会让这个换行晚一步到达，此时那个空行属于**调用行自己的换行**，
         * 必须消费掉而不能当作正文里的空行（否则正文会多出一个换行，与一次性解析结果不一致）。
         */
        private var callLineNewlinePending = false

        fun append(chunk: String) {
            raw.append(chunk)
            settle()
        }

        /** 累积原文（回合结束时回填给模型用） */
        val rawText: String get() = raw.toString()

        /** 已经完整、且能解析出工具名的调用（按出现顺序） */
        val completeCalls: List<ToolCall> get() = calls

        /** 已经完整、但解析不出来的调用原文 */
        val malformedBlocks: List<String> get() = malformed

        /**
         * 尾部是否存在一段「标记已写出、但 JSON 还没闭口」的调用。
         * 界面据此在参数还没生成完时就把卡片建出来（先给容器、再填内容）。
         */
        val hasOpenCallBlock: Boolean
            get() {
                val tail = raw.substring(settledUpTo)
                if (payloadOfLine(tail) == null) return false
                val brace = tail.indexOf('{')
                return brace < 0 || matchBrace(tail, brace) < 0
            }

        /**
         * 从还没闭口的调用里读出工具名。
         *
         * 只在 `"tool":"…"` 的**右引号已经到达**时才返回，并要求它出现在 `"arguments"` 之前
         * （避免误取参数里嵌套的同名字段）。属于确定性读取，不做任何模糊猜测。
         */
        fun peekToolName(): String? {
            val tail = raw.substring(settledUpTo)
            if (payloadOfLine(tail) == null) return null
            val toolKeyAt = tail.indexOf("\"tool\"")
            if (toolKeyAt < 0) return null
            val argumentsAt = tail.indexOf("\"arguments\"")
            if (argumentsAt in 0 until toolKeyAt) return null
            var i = tail.indexOf(':', toolKeyAt + 6)
            if (i < 0) return null
            i++
            while (i < tail.length && tail[i].isWhitespace()) i++
            if (i >= tail.length || tail[i] != '"') return null
            i++
            val name = StringBuilder()
            while (i < tail.length) {
                val c = tail[i]
                // 工具名不会含转义字符：出现即视为不是工具名
                if (c == '\\') return null
                if (c == '"') return name.toString().ifBlank { null }
                name.append(c)
                i++
            }
            return null // 右引号还没到
        }

        /** 当前可以安全显示的正文 */
        fun visible(): String =
            (settledVisible.toString() + splitForStreaming(raw.substring(settledUpTo)).first).trim()

        private fun settle() {
            while (true) {
                val newline = raw.indexOf('\n', settledUpTo)
                if (newline >= 0) {
                    val line = raw.substring(settledUpTo, newline)
                    if (line.isEmpty() && callLineNewlinePending) {
                        // 上一条调用行自己的换行晚到了几步，消费掉即可
                        callLineNewlinePending = false
                        settledUpTo = newline + 1
                        continue
                    }
                    if (payloadOfLine(line) == null) {
                        callLineNewlinePending = false
                        settledVisible.append(line).append('\n')
                        settledUpTo = newline + 1
                        continue
                    }
                    // 调用行：必须等（可能跨行的）JSON 闭口
                    if (!settleCallBlock()) break
                    continue
                }
                // 没有完整行了：尾部若正好是一个已闭合的调用块也要结算
                //（模型不保证在调用行后面补换行，参数一到齐就该把卡片建出来）
                if (!settleCallBlock()) break
                if (raw.length == settledUpTo) break
            }
        }

        /**
         * 把 [settledUpTo] 处开始的调用块结算掉（JSON 必须闭口）。
         * 返回 false 表示「还不是一个完整调用块」，调用方应停止结算，把尾部留给 [visible]。
         */
        private fun settleCallBlock(): Boolean {
            val lineEnd = raw.indexOf('\n', settledUpTo).let { if (it < 0) raw.length else it }
            if (payloadOfLine(raw.substring(settledUpTo, lineEnd)) == null) return false
            val brace = raw.indexOf('{', settledUpTo)
            if (brace < 0) return false
            val end = matchBrace(raw, brace)
            if (end < 0) return false
            val block = raw.substring(settledUpTo, end + 1)
            val call = toCall(payloadOfLine(block) ?: block)
            if (call != null) calls += call else malformed += block.trim()
            settledUpTo = end + 1
            if (settledUpTo < raw.length && raw[settledUpTo] == '\n') {
                settledUpTo++
                callLineNewlinePending = false
            } else {
                callLineNewlinePending = true
            }
            return true
        }
    }
}
