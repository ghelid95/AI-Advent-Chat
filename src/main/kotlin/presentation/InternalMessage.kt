package presentation

import data.UsageInfo

// Internal message representation supporting compaction
sealed class InternalMessage {
    abstract val timestamp: Long
    abstract val usage: UsageInfo?

    data class Regular(
        val content: String,
        val isUser: Boolean,
        override val timestamp: Long = System.currentTimeMillis(),
        override val usage: UsageInfo? = null
    ) : InternalMessage()

    data class CompactedSummary(
        val summaryContent: String,
        val originalMessages: List<Regular>,
        val aggregatedUsage: UsageInfo,
        override val timestamp: Long = System.currentTimeMillis()
    ) : InternalMessage() {
        override val usage = aggregatedUsage
    }
}
