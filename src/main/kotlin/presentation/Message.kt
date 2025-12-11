package presentation

import data.UsageInfo

data class Message(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: UsageInfo? = null
)
