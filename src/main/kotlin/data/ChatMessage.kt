package data

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: ChatMessageContent
) {
    // Helper constructor for backward compatibility with string content
    constructor(role: String, content: String) : this(role, ChatMessageContent.Text(content))
}

@Serializable
sealed class ChatMessageContent {
    @Serializable
    data class Text(val text: String) : ChatMessageContent()

    @Serializable
    data class ContentBlocks(val blocks: List<ContentBlock>) : ChatMessageContent()
}

// Helper function to get text content from ChatMessage
fun ChatMessage.getTextContent(): String {
    return when (content) {
        is ChatMessageContent.Text -> content.text
        is ChatMessageContent.ContentBlocks -> {
            content.blocks.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.ToolUse -> "[Tool use: ${block.name}]"
                    is ContentBlock.ToolResult -> "[Tool result: ${block.content.take(100)}...]"
                }
            }
        }
    }
}
