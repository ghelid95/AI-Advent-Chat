package data

import kotlinx.serialization.Serializable

@Serializable
data class LlmMessage(
    val answer: String,
    val joke: String?,
    val usage: UsageInfo? = null,
    val toolUses: List<ContentBlock.ToolUse>? = null,
    val stopReason: String? = null
)
