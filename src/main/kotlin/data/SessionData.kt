package data

import kotlinx.serialization.Serializable
import presentation.InternalMessage

@Serializable
data class SessionData(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastModified: Long,
    val settings: SessionSettings,
    val messages: List<SerializableInternalMessage>,
    val sessionStats: SessionStats
)

@Serializable
data class SessionSettings(
    val vendor: Vendor,
    val selectedModel: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val compactionEnabled: Boolean
)

@Serializable
sealed class SerializableInternalMessage {
    abstract val timestamp: Long
    abstract val usage: UsageInfo?

    @Serializable
    data class Regular(
        val content: String,
        val isUser: Boolean,
        override val timestamp: Long,
        override val usage: UsageInfo? = null
    ) : SerializableInternalMessage()

    @Serializable
    data class CompactedSummary(
        val summaryContent: String,
        val originalMessages: List<Regular>,
        val aggregatedUsage: UsageInfo,
        override val timestamp: Long
    ) : SerializableInternalMessage() {
        override val usage = aggregatedUsage
    }
}

// Extension functions to convert between InternalMessage and SerializableInternalMessage
fun InternalMessage.toSerializable(): SerializableInternalMessage {
    return when (this) {
        is InternalMessage.Regular -> SerializableInternalMessage.Regular(
            content = content,
            isUser = isUser,
            timestamp = timestamp,
            usage = usage
        )
        is InternalMessage.CompactedSummary -> SerializableInternalMessage.CompactedSummary(
            summaryContent = summaryContent,
            originalMessages = originalMessages.map {
                SerializableInternalMessage.Regular(
                    content = it.content,
                    isUser = it.isUser,
                    timestamp = it.timestamp,
                    usage = it.usage
                )
            },
            aggregatedUsage = aggregatedUsage,
            timestamp = timestamp
        )
    }
}

fun SerializableInternalMessage.toInternal(): InternalMessage {
    return when (this) {
        is SerializableInternalMessage.Regular -> InternalMessage.Regular(
            content = content,
            isUser = isUser,
            timestamp = timestamp,
            usage = usage
        )
        is SerializableInternalMessage.CompactedSummary -> InternalMessage.CompactedSummary(
            summaryContent = summaryContent,
            originalMessages = originalMessages.map {
                InternalMessage.Regular(
                    content = it.content,
                    isUser = it.isUser,
                    timestamp = it.timestamp,
                    usage = it.usage
                )
            },
            aggregatedUsage = aggregatedUsage,
            timestamp = timestamp
        )
    }
}